package com.example.myargus.document.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.myargus.auth.CurrentUserService;
import com.example.myargus.common.enums.DocumentStatus;
import com.example.myargus.common.exception.BusinessException;
import com.example.myargus.document.mapper.DocumentMapper;
import com.example.myargus.document.mapper.DocumentUploadChunkMapper;
import com.example.myargus.document.mapper.DocumentUploadSessionMapper;
import com.example.myargus.document.model.dto.UploadChunkRequest;
import com.example.myargus.document.model.dto.UploadDocumentRequest;
import com.example.myargus.document.model.dto.UploadInitRequest;
import com.example.myargus.document.model.entity.DocumentEntity;
import com.example.myargus.document.model.entity.DocumentUploadChunkEntity;
import com.example.myargus.document.model.entity.DocumentUploadSessionEntity;
import com.example.myargus.document.model.vo.UploadInitResponse;
import com.example.myargus.document.model.vo.UploadStatusResponse;
import com.example.myargus.engine.elasticsearch.ElasticsearchChunkIndexService;
import com.example.myargus.engine.storage.ObjectStorageService;
import com.example.myargus.group.service.GroupMembershipService;
import com.example.myargus.ingestion.vector.VectorIngestionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 大文件分片上传及小文件直传服务。
 *
 * <p>支持将文件拆分为多个分片上传（最大 256MB，每个分片最大 10MB）。
 * 上传流程：初始化会话（init） -> 逐个上传分片（uploadChunk） -> 合并完成（complete）。
 * 支持文件哈希去重（秒传复用）和可续传会话恢复（断点续传）。
 *
 * @author LeanArgus
 */
@Service
@Slf4j
public class DocumentUploadService {

    /** 文件名最大长度 */
    private static final int MAX_FILE_NAME_LENGTH = 255;
    /** Content-Type 最大长度 */
    private static final int MAX_CONTENT_TYPE_LENGTH = 128;
    /** 文件哈希最大长度 */
    private static final int MAX_FILE_HASH_LENGTH = 128;
    /** 文件扩展名最大长度 */
    private static final int MAX_FILE_EXT_LENGTH = 16;
    /** 分片上传最大文件大小：256MB */
    private static final long MAX_FILE_SIZE = 256L * 1024 * 1024;
    /** 单个分片的最大大小：10MB */
    private static final long MAX_CHUNK_SIZE = 10L * 1024 * 1024;
    /** 上传会话过期时长：24 小时 */
    private static final long SESSION_EXPIRE_HOURS = 24L;
    /** 支持的上传文件格式 */
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("txt", "md", "pdf", "docx");
    /** 上传会话状态：已初始化 */
    private static final String UPLOAD_STATUS_INIT = "INIT";
    /** 上传会话状态：上传中 */
    private static final String UPLOAD_STATUS_UPLOADING = "UPLOADING";
    /** 上传会话状态：正在合并 */
    private static final String UPLOAD_STATUS_COMPLETING = "COMPLETING";
    /** 上传会话状态：已完成 */
    private static final String UPLOAD_STATUS_COMPLETED = "COMPLETED";
    /** 分片默认 MIME 类型 */
    private static final String OCTET_STREAM = "application/octet-stream";
    /** 直接上传最大文件大小：10MB */
    private static final long MAX_DIRECT_FILE_SIZE = 10L * 1024 * 1024;

    private final DocumentMapper documentMapper;
    private final DocumentUploadSessionMapper documentUploadSessionMapper;
    private final DocumentUploadChunkMapper documentUploadChunkMapper;
    private final GroupMembershipService groupMembershipService;
    private final ObjectStorageService objectStorageService;
    private final VectorIngestionService vectorIngestionService;
    private final ElasticsearchChunkIndexService elasticsearchChunkIndexService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public DocumentUploadService(
            DocumentMapper documentMapper,
            DocumentUploadSessionMapper documentUploadSessionMapper,
            DocumentUploadChunkMapper documentUploadChunkMapper,
            GroupMembershipService groupMembershipService,
            ObjectStorageService objectStorageService,
            VectorIngestionService vectorIngestionService,
            ElasticsearchChunkIndexService elasticsearchChunkIndexService,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.documentMapper = documentMapper;
        this.documentUploadSessionMapper = documentUploadSessionMapper;
        this.documentUploadChunkMapper = documentUploadChunkMapper;
        this.groupMembershipService = groupMembershipService;
        this.objectStorageService = objectStorageService;
        this.vectorIngestionService = vectorIngestionService;
        this.elasticsearchChunkIndexService = elasticsearchChunkIndexService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 初始化分片上传会话。
     *
     * <ol>
     *   <li>秒传：存在相同哈希的 READY 文档则直接复用</li>
     *   <li>断点续传：存在相同哈希的未过期会话则恢复</li>
     *   <li>新建会话：创建全新的上传会话</li>
     * </ol>
     */
    @Transactional
    public UploadInitResponse initUpload(HttpServletRequest request, UploadInitRequest uploadRequest) {
        NormalizedInitRequest normalizedRequest = validateInitRequest(uploadRequest);
        Long groupId = normalizedRequest.groupId();
        CurrentUserService.CurrentUser currentUser = groupMembershipService.requireGroupOwner(groupId);
        DocumentEntity existingDocument = documentMapper.selectByGroupIdAndFileHash(groupId, normalizedRequest.fileHash());
        if (existingDocument != null && DocumentStatus.READY.name().equals(existingDocument.getStatus())) {
            log.info("分片上传-秒传复用: groupId={}, userId={}, fileName={}, fileHash={}, reusedDocumentId={}",
                    groupId, currentUser.userId(), normalizedRequest.fileName(), normalizedRequest.fileHash(), existingDocument.getId());
            Long documentId = createInstantUploadedDocument(
                    groupId,
                    currentUser.userId(),
                    existingDocument,
                    normalizedRequest.fileName()
            );
            return UploadInitResponse.instant(documentId);
        }
        DocumentUploadSessionEntity existingSession = documentUploadSessionMapper.selectLatestReusableSession(
                groupId,
                currentUser.userId(),
                normalizedRequest.fileHash()
        );
        if (existingSession != null) {
            log.info("分片上传-续传恢复: groupId={}, userId={}, uploadId={}, fileName={}, fileHash={}, chunkSize={}, chunkCount={}",
                    groupId, currentUser.userId(), existingSession.getUploadId(), normalizedRequest.fileName(),
                    normalizedRequest.fileHash(), existingSession.getChunkSize(), existingSession.getChunkCount());
            List<Integer> uploadedChunks = documentUploadChunkMapper.selectByUploadId(existingSession.getUploadId()).stream()
                    .map(DocumentUploadChunkEntity::getChunkIndex)
                    .toList();
            return UploadInitResponse.uploadSession(
                    existingSession.getUploadId(),
                    uploadedChunks,
                    existingSession.getChunkSize(),
                    existingSession.getChunkCount()
            );
        }
        DocumentUploadSessionEntity session = buildUploadSession(groupId, currentUser.userId(), normalizedRequest);
        documentUploadSessionMapper.insert(session);
        log.info("分片上传-新建会话: groupId={}, userId={}, uploadId={}, fileName={}, fileHash={}, fileSize={}, chunkSize={}, chunkCount={}",
                groupId, currentUser.userId(), session.getUploadId(), normalizedRequest.fileName(),
                normalizedRequest.fileHash(), normalizedRequest.fileSize(), normalizedRequest.chunkSize(), normalizedRequest.chunkCount());
        return UploadInitResponse.uploadSession(session.getUploadId(), session.getChunkSize(), session.getChunkCount());
    }

    /**
     * 上传单个分片。
     */
    @Transactional
    public List<Integer> uploadChunk(HttpServletRequest request, UploadChunkRequest uploadRequest) {
        DocumentUploadSessionEntity session = requireOwnedActiveSession(request, uploadRequest.uploadId());
        MultipartFile chunk = requireChunk(uploadRequest, session);
        String chunkHash = normalizeFileHash(uploadRequest.chunkHash());
        String objectKey = buildChunkObjectKey(session.getGroupId(), session.getUploadId(), uploadRequest.chunkIndex());
        log.debug("分片上传-接收分片: uploadId={}, chunkIndex={}/{}, chunkSize={}",
                uploadRequest.uploadId(), uploadRequest.chunkIndex(), session.getChunkCount(), chunk.getSize());
        LocalDateTime now = LocalDateTime.now();
        byte[] chunkData;
        try {
            chunkData = chunk.getBytes();
        } catch (IOException exception) {
            throw new BusinessException("读取分片数据失败");
        }
        String computedHash = computeSha256(chunkData);
        if (!computedHash.equalsIgnoreCase(chunkHash)) {
            throw new BusinessException("分片校验失败，数据可能已损坏");
        }
        try {
            objectStorageService.putObject(
                    session.getStorageBucket(),
                    objectKey,
                    new ByteArrayInputStream(chunkData),
                    chunkData.length,
                    OCTET_STREAM
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("分片上传失败");
        }

        DocumentUploadChunkEntity uploadChunk = new DocumentUploadChunkEntity();
        uploadChunk.setUploadId(session.getUploadId());
        uploadChunk.setChunkIndex(uploadRequest.chunkIndex());
        uploadChunk.setChunkSize((long) chunkData.length);
        uploadChunk.setChunkHash(chunkHash);
        uploadChunk.setStorageBucket(session.getStorageBucket());
        uploadChunk.setStorageObjectKey(objectKey);
        uploadChunk.setUploadedAt(now);
        uploadChunk.setCreatedAt(now);
        uploadChunk.setUpdatedAt(now);
        try {
            documentUploadChunkMapper.upsert(uploadChunk);
        } catch (RuntimeException exception) {
            compensateUploadedChunkObject(session.getStorageBucket(), objectKey);
            throw exception;
        }
        documentUploadSessionMapper.update(null, new LambdaUpdateWrapper<DocumentUploadSessionEntity>()
                .eq(DocumentUploadSessionEntity::getUploadId, session.getUploadId())
                .set(DocumentUploadSessionEntity::getStatus, UPLOAD_STATUS_UPLOADING)
                .set(DocumentUploadSessionEntity::getMergedObjectKey, null)
                .set(DocumentUploadSessionEntity::getUpdatedAt, now)
        );
        List<Integer> uploadedChunkIndexes = documentUploadChunkMapper.selectByUploadId(session.getUploadId()).stream()
                .map(DocumentUploadChunkEntity::getChunkIndex)
                .toList();
        if (uploadedChunkIndexes.size() == session.getChunkCount()) {
            log.info("分片上传-全部接收完成: uploadId={}, fileName={}, totalChunks={}",
                    session.getUploadId(), session.getFileName(), session.getChunkCount());
        }
        return uploadedChunkIndexes;
    }

    /**
     * 完成分片上传，合并所有分片为最终文档。
     */
    @Transactional
    public Long completeUpload(HttpServletRequest request, String uploadId) {
        DocumentUploadSessionEntity session = requireOwnedActiveSession(request, uploadId);
        List<DocumentUploadChunkEntity> chunks = documentUploadChunkMapper.selectByUploadId(uploadId).stream()
                .sorted(Comparator.comparing(DocumentUploadChunkEntity::getChunkIndex))
                .toList();
        ensureAllChunksPresent(session, chunks);
        log.info("分片上传-开始合并: uploadId={}, fileName={}, fileSize={}, chunkCount={}",
                uploadId, session.getFileName(), session.getFileSize(), chunks.size());
        String objectKey = buildFinalObjectKey(session);
        LocalDateTime now = LocalDateTime.now();
        int updated = documentUploadSessionMapper.update(null, new LambdaUpdateWrapper<DocumentUploadSessionEntity>()
                .eq(DocumentUploadSessionEntity::getUploadId, uploadId)
                .eq(DocumentUploadSessionEntity::getStatus, UPLOAD_STATUS_UPLOADING)
                .set(DocumentUploadSessionEntity::getStatus, UPLOAD_STATUS_COMPLETING)
                .set(DocumentUploadSessionEntity::getMergedObjectKey, null)
                .set(DocumentUploadSessionEntity::getUpdatedAt, now)
        );
        if (updated == 0) {
            throw new BusinessException("上传会话状态异常，无法完成上传");
        }
        try {
            objectStorageService.composeObject(
                    session.getStorageBucket(),
                    objectKey,
                    chunks.stream().map(DocumentUploadChunkEntity::getStorageObjectKey).toList(),
                    session.getContentType()
            );
            Long documentId = finalizeUploadedDocument(
                    session.getGroupId(),
                    session.getUploaderUserId(),
                    session.getFileName(),
                    session.getFileExt(),
                    session.getContentType(),
                    session.getFileSize(),
                    session.getFileHash(),
                    session.getStorageBucket(),
                    objectKey
            );
            documentUploadSessionMapper.update(null, new LambdaUpdateWrapper<DocumentUploadSessionEntity>()
                    .eq(DocumentUploadSessionEntity::getUploadId, uploadId)
                    .set(DocumentUploadSessionEntity::getStatus, UPLOAD_STATUS_COMPLETED)
                    .set(DocumentUploadSessionEntity::getMergedObjectKey, objectKey)
                    .set(DocumentUploadSessionEntity::getUpdatedAt, LocalDateTime.now())
            );
            log.info("分片上传-合并完成: uploadId={}, fileName={}, documentId={}, objectKey={}",
                    uploadId, session.getFileName(), documentId, objectKey);
            return documentId;
        } catch (RuntimeException exception) {
            log.error("分片上传-合并失败: uploadId={}, fileName={}, reason={}",
                    uploadId, session.getFileName(), exception.getMessage(), exception);
            try {
                objectStorageService.deleteObject(session.getStorageBucket(), objectKey);
            } catch (RuntimeException ignored) {
            }
            for (DocumentUploadChunkEntity chunk : chunks) {
                compensateUploadedChunkObject(session.getStorageBucket(), chunk.getStorageObjectKey());
            }
            throw exception;
        }
    }

    /**
     * 查询上传会话的当前状态。
     */
    public UploadStatusResponse getUploadStatus(HttpServletRequest request, String uploadId) {
        DocumentUploadSessionEntity session = requireOwnedActiveSession(request, uploadId);
        List<Integer> uploadedChunks = documentUploadChunkMapper.selectByUploadId(uploadId).stream()
                .map(DocumentUploadChunkEntity::getChunkIndex)
                .toList();
        return new UploadStatusResponse(
                session.getStatus(),
                uploadedChunks,
                uploadedChunks.size(),
                session.getChunkCount()
        );
    }

    // ────────────────────────────── 直接上传（小文件模式） ──────────────────────────────

    /** 直接上传文档（小文件模式） */
    @Transactional
    public Long uploadDocument(Long userId, UploadDocumentRequest uploadRequest) {
        Long groupId = requireGroupId(uploadRequest.getGroupId());
        groupMembershipService.requireGroupOwner(groupId);
        MultipartFile file = requireValidFile(uploadRequest.getFile());
        String fileName = extractDirectFileName(file);
        String fileExt = extractFileExt(fileName);
        String bucket = objectStorageService.getDefaultBucket();
        String objectKey = buildDirectObjectKey(groupId, userId, fileExt);
        String fileHash = calculateSha256(file);
        DocumentEntity document = null;
        log.info("开始直接上传文档: groupId={}, userId={}, fileName={}, size={}, objectKey={}",
                groupId, userId, fileName, file.getSize(), objectKey);
        uploadDirectFile(bucket, objectKey, file);
        log.info("对象存储上传完成: groupId={}, objectKey={}", groupId, objectKey);
        try {
            document = persistAndFinalizeUploadedDocument(new FinalizedUploadCommand(
                    groupId, userId, fileName, fileExt,
                    normalizeContentType(file.getContentType()), file.getSize(),
                    fileHash, bucket, objectKey));
            return document.getId();
        } catch (RuntimeException exception) {
            log.error("文档上传链路失败: groupId={}, objectKey={}, reason={}",
                    groupId, objectKey, exception.getMessage(), exception);
            compensateExternalIndexes(document);
            compensateUploadedDirectObject(bucket, objectKey, exception);
            throw exception;
        }
    }

    /** 通过复用已有文档创建新文档记录（秒传） */
    @Transactional
    public Long createInstantUploadedDocument(Long groupId, Long userId,
                                               DocumentEntity existingDocument, String fileName) {
        if (existingDocument == null) {
            throw new BusinessException("复用文档不存在");
        }
        DocumentEntity document = persistAndFinalizeUploadedDocument(new FinalizedUploadCommand(
                requireGroupId(groupId),
                requirePositiveUserId(userId),
                validateReusableFileName(fileName),
                requireText(existingDocument.getFileExt(), "文件扩展名非法"),
                normalizeContentType(existingDocument.getContentType()),
                requirePositiveFileSize(existingDocument.getFileSize()),
                existingDocument.getFileHash(),
                requireText(existingDocument.getStorageBucket(), "对象存储桶非法"),
                requireText(existingDocument.getStorageObjectKey(), "对象存储路径非法")
        ));
        return document.getId();
    }

    /** 完成分片上传后的文档持久化 */
    @Transactional
    public Long finalizeUploadedDocument(Long groupId, Long userId, String fileName,
                                          String fileExt, String contentType, Long fileSize,
                                          String fileHash, String bucket, String objectKey) {
        DocumentEntity document = persistAndFinalizeUploadedDocument(new FinalizedUploadCommand(
                requireGroupId(groupId),
                requirePositiveUserId(userId),
                sanitizeFileName(fileName),
                requireText(fileExt, "文件扩展名非法"),
                normalizeContentType(contentType),
                requirePositiveFileSize(fileSize),
                fileHash,
                requireText(bucket, "对象存储桶非法"),
                requireText(objectKey, "对象存储路径非法")
        ));
        return document.getId();
    }

    // ──────────────────────── 内部辅助方法 ────────────────────────

    private NormalizedInitRequest validateInitRequest(UploadInitRequest uploadRequest) {
        if (uploadRequest == null) {
            throw new BusinessException("上传初始化请求不能为空");
        }
        Long groupId = requireGroupId(uploadRequest.groupId());
        String fileName = sanitizeFileName(uploadRequest.fileName());
        String fileExt = extractFileExt(fileName);
        long fileSize = requirePositive(uploadRequest.fileSize(), "fileSize 非法");
        if (fileSize > MAX_FILE_SIZE) {
            throw new BusinessException("上传文件超过大小限制");
        }
        String contentType = normalizeContentType(uploadRequest.contentType());
        String fileHash = normalizeFileHash(uploadRequest.fileHash());
        long chunkSize = requirePositive(uploadRequest.chunkSize(), "chunkSize 非法");
        if (chunkSize > MAX_CHUNK_SIZE) {
            throw new BusinessException("chunkSize 超过限制");
        }
        int chunkCount = requirePositive(uploadRequest.chunkCount(), "chunkCount 非法");
        long expectedChunkCount = (fileSize + chunkSize - 1) / chunkSize;
        if (chunkCount != expectedChunkCount) {
            throw new BusinessException("chunkCount 与文件大小不匹配");
        }
        return new NormalizedInitRequest(groupId, fileName, fileExt, fileSize, contentType, fileHash, chunkSize, chunkCount);
    }

    private DocumentUploadSessionEntity requireOwnedActiveSession(HttpServletRequest request, String uploadId) {
        if (!StringUtils.hasText(uploadId)) {
            throw new BusinessException("uploadId 非法");
        }
        DocumentUploadSessionEntity session = documentUploadSessionMapper.selectByUploadId(uploadId.trim());
        if (session == null) {
            throw new BusinessException("上传会话不存在");
        }
        CurrentUserService.CurrentUser currentUser = groupMembershipService.requireGroupOwner(session.getGroupId());
        if (!currentUser.userId().equals(session.getUploaderUserId())) {
            throw new BusinessException("上传会话不属于当前用户");
        }
        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("上传会话已过期");
        }
        if (UPLOAD_STATUS_COMPLETED.equals(session.getStatus()) || UPLOAD_STATUS_COMPLETING.equals(session.getStatus())) {
            throw new BusinessException("上传会话已完成或正在合并");
        }
        return session;
    }

    private MultipartFile requireChunk(UploadChunkRequest uploadRequest, DocumentUploadSessionEntity session) {
        if (uploadRequest == null) {
            throw new BusinessException("分片上传请求不能为空");
        }
        if (uploadRequest.chunkIndex() == null
                || uploadRequest.chunkIndex() < 0
                || uploadRequest.chunkIndex() >= session.getChunkCount()) {
            throw new BusinessException("chunkIndex 非法");
        }
        MultipartFile chunk = uploadRequest.chunk();
        if (chunk == null || chunk.isEmpty()) {
            throw new BusinessException("上传分片不能为空");
        }
        if (chunk.getSize() > session.getChunkSize()) {
            throw new BusinessException("上传分片超过大小限制");
        }
        return chunk;
    }

    private void ensureAllChunksPresent(DocumentUploadSessionEntity session, List<DocumentUploadChunkEntity> chunks) {
        if (chunks.size() != session.getChunkCount()) {
            throw new BusinessException("缺少分片，无法完成上传");
        }
        for (int index = 0; index < session.getChunkCount(); index++) {
            DocumentUploadChunkEntity chunk = chunks.get(index);
            if (chunk == null || !Integer.valueOf(index).equals(chunk.getChunkIndex())) {
                throw new BusinessException("缺少分片，无法完成上传");
            }
        }
    }

    private String buildChunkObjectKey(Long groupId, String uploadId, Integer chunkIndex) {
        return "uploads/%d/%s/chunks/%d".formatted(groupId, uploadId, chunkIndex);
    }

    private String buildFinalObjectKey(DocumentUploadSessionEntity session) {
        String fileId = UUID.randomUUID().toString().replace("-", "");
        return "groups/%d/users/%d/%s.%s".formatted(
                session.getGroupId(),
                session.getUploaderUserId(),
                fileId,
                session.getFileExt()
        );
    }

    private DocumentUploadSessionEntity buildUploadSession(Long groupId, Long userId, NormalizedInitRequest uploadRequest) {
        LocalDateTime now = LocalDateTime.now();
        DocumentUploadSessionEntity session = new DocumentUploadSessionEntity();
        session.setUploadId(UUID.randomUUID().toString().replace("-", ""));
        session.setGroupId(groupId);
        session.setUploaderUserId(userId);
        session.setFileName(uploadRequest.fileName());
        session.setFileExt(uploadRequest.fileExt());
        session.setContentType(uploadRequest.contentType());
        session.setFileSize(uploadRequest.fileSize());
        session.setFileHash(uploadRequest.fileHash());
        session.setChunkSize(uploadRequest.chunkSize());
        session.setChunkCount(uploadRequest.chunkCount());
        session.setStatus(UPLOAD_STATUS_INIT);
        session.setStorageBucket(objectStorageService.getDefaultBucket());
        session.setMergedObjectKey(null);
        session.setExpiresAt(now.plusHours(SESSION_EXPIRE_HOURS));
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        return session;
    }

    private MultipartFile requireValidFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        if (file.getSize() > MAX_DIRECT_FILE_SIZE) {
            throw new BusinessException("上传文件超过大小限制");
        }
        return file;
    }

    private String extractDirectFileName(MultipartFile file) {
        return sanitizeFileName(file.getOriginalFilename());
    }

    private String buildDirectObjectKey(Long groupId, Long userId, String fileExt) {
        String fileId = UUID.randomUUID().toString().replace("-", "");
        return "groups/%d/users/%d/%s.%s".formatted(groupId, userId, fileId, fileExt);
    }

    private String calculateSha256(MultipartFile file) {
        try {
            return computeSha256(file.getBytes());
        } catch (IOException exception) {
            throw new BusinessException("读取上传文件失败");
        }
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }

    private void uploadDirectFile(String bucket, String objectKey, MultipartFile file) {
        try (java.io.InputStream inputStream = file.getInputStream()) {
            objectStorageService.putObject(bucket, objectKey, inputStream, file.getSize(),
                    normalizeContentType(file.getContentType()));
        } catch (IOException exception) {
            throw new BusinessException("读取上传文件失败");
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException("文档上传失败");
        }
    }

    private void compensateUploadedDirectObject(String bucket, String objectKey, RuntimeException originalException) {
        try {
            objectStorageService.deleteObject(bucket, objectKey);
        } catch (RuntimeException compensationException) {
            originalException.addSuppressed(compensationException);
            log.warn("补偿清理对象存储失败: bucket={}, objectKey={}, reason={}",
                    bucket, objectKey, compensationException.getMessage());
        }
    }

    private void compensateUploadedChunkObject(String bucket, String objectKey) {
        try {
            objectStorageService.deleteObject(bucket, objectKey);
            log.debug("补偿清理分片对象: bucket={}, objectKey={}", bucket, objectKey);
        } catch (RuntimeException ignored) {
            log.warn("补偿清理分片对象失败: bucket={}, objectKey={}", bucket, objectKey);
        }
    }

    private void compensateExternalIndexes(DocumentEntity document) {
        if (document == null || document.getId() == null) return;
        try {
            vectorIngestionService.deleteDocumentVectors(document.getId());
        } catch (RuntimeException exception) {
            log.warn("文档失败补偿时删除向量失败: documentId={}, reason={}",
                    document.getId(), exception.getMessage());
        }
        try {
            elasticsearchChunkIndexService.deleteDocumentChunks(document.getId());
        } catch (RuntimeException exception) {
            log.warn("文档失败补偿时删除 ES 索引失败: documentId={}, reason={}",
                    document.getId(), exception.getMessage());
        }
    }

    private DocumentEntity persistAndFinalizeUploadedDocument(FinalizedUploadCommand command) {
        DocumentEntity document = buildDocument(command);
        documentMapper.insert(document);
        log.info("文档元数据入库完成: documentId={}, groupId={}, status={}",
                document.getId(), command.groupId(), document.getStatus());
        applicationEventPublisher.publishEvent(
                new DocumentIngestionRequestedEvent(document.getId(), command.groupId()));
        log.info("已发布文档异步ETL事件: documentId={}, groupId={}",
                document.getId(), command.groupId());
        return document;
    }

    private DocumentEntity buildDocument(FinalizedUploadCommand command) {
        LocalDateTime now = LocalDateTime.now();
        DocumentEntity document = new DocumentEntity();
        document.setGroupId(command.groupId());
        document.setUploaderUserId(command.userId());
        document.setFileName(command.fileName());
        document.setFileExt(command.fileExt());
        document.setContentType(command.contentType());
        document.setFileSize(command.fileSize());
        document.setFileHash(command.fileHash());
        document.setStorageBucket(command.bucket());
        document.setStorageObjectKey(command.objectKey());
        document.setStatus(DocumentStatus.PROCESSING.name());
        document.setDeleted(false);
        document.setUploadedAt(now);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        return document;
    }

    private Long requireGroupId(Long groupId) {
        if (groupId == null || groupId <= 0) {
            throw new BusinessException("groupId 非法");
        }
        return groupId;
    }

    private String sanitizeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException("文件名非法");
        }
        String normalizedFileName = StringUtils.cleanPath(fileName.trim());
        String sanitizedFileName = normalizedFileName.substring(normalizedFileName.lastIndexOf('/') + 1);
        if (!StringUtils.hasText(sanitizedFileName) || sanitizedFileName.length() > MAX_FILE_NAME_LENGTH) {
            throw new BusinessException("文件名非法");
        }
        return sanitizedFileName;
    }

    private String extractFileExt(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            throw new BusinessException("文件扩展名非法");
        }
        String fileExt = fileName.substring(dotIndex + 1).toLowerCase();
        if (fileExt.length() > MAX_FILE_EXT_LENGTH || !SUPPORTED_EXTENSIONS.contains(fileExt)) {
            throw new BusinessException("文件类型不支持");
        }
        return fileExt;
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "application/octet-stream";
        }
        String normalizedContentType = contentType.trim();
        if (normalizedContentType.length() > MAX_CONTENT_TYPE_LENGTH) {
            throw new BusinessException("文件类型描述过长");
        }
        return normalizedContentType;
    }

    private String normalizeFileHash(String fileHash) {
        if (!StringUtils.hasText(fileHash)) {
            throw new BusinessException("fileHash 非法");
        }
        String normalizedFileHash = fileHash.trim();
        if (normalizedFileHash.length() > MAX_FILE_HASH_LENGTH) {
            throw new BusinessException("fileHash 非法");
        }
        return normalizedFileHash;
    }

    private long requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new BusinessException(message);
        }
        return value;
    }

    private int requirePositive(Integer value, String message) {
        if (value == null || value <= 0) {
            throw new BusinessException(message);
        }
        return value;
    }

    private Long requirePositiveUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("userId 非法");
        }
        return userId;
    }

    private long requirePositiveFileSize(Long fileSize) {
        if (fileSize == null || fileSize <= 0) {
            throw new BusinessException("fileSize 非法");
        }
        return fileSize;
    }

    private String validateReusableFileName(String fileName) {
        return sanitizeFileName(fileName);
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    private record NormalizedInitRequest(
            Long groupId,
            String fileName,
            String fileExt,
            Long fileSize,
            String contentType,
            String fileHash,
            Long chunkSize,
            Integer chunkCount
    ) {
    }

    record FinalizedUploadCommand(
            Long groupId,
            Long userId,
            String fileName,
            String fileExt,
            String contentType,
            Long fileSize,
            String fileHash,
            String bucket,
            String objectKey
    ) {
    }
}
