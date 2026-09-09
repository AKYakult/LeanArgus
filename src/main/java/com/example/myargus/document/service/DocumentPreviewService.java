package com.example.myargus.document.service;

import com.example.myargus.common.enums.DocumentStatus;
import com.example.myargus.common.exception.BusinessException;
import com.example.myargus.document.mapper.DocumentMapper;
import com.example.myargus.document.model.entity.DocumentEntity;
import com.example.myargus.document.model.vo.DocumentPreviewVO;
import com.example.myargus.engine.storage.ObjectStorageService;
import com.example.myargus.group.service.GroupMembershipService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 文档预览服务。
 *
 * @author LeanArgus
 */
@Service
@Slf4j
public class DocumentPreviewService {

    private final DocumentMapper documentMapper;
    private final GroupMembershipService groupMembershipService;
    private final ObjectStorageService storageService;

    public DocumentPreviewService(DocumentMapper documentMapper,
                                  GroupMembershipService groupMembershipService,
                                  ObjectStorageService storageService) {
        this.documentMapper = documentMapper;
        this.groupMembershipService = groupMembershipService;
        this.storageService = storageService;
    }

    /**
     * 读取文档内容并返回预览文本。
     */
    public DocumentPreviewVO previewDocument(Long userId, Long groupId, Long documentId) {
        requireGroupId(groupId);
        groupMembershipService.requireGroupReadable(groupId);
        DocumentEntity document = loadDocument(documentId, groupId);

        log.info("从对象存储读取文档预览内容: documentId={}, fileName={}", documentId, document.getFileName());

        String fullText;
        if ("md".equalsIgnoreCase(document.getFileExt()) || "txt".equalsIgnoreCase(document.getFileExt())) {
            fullText = readRawContent(document);
        } else if (document.getPreviewText() != null && !document.getPreviewText().isBlank()) {
            fullText = document.getPreviewText();
        } else {
            fullText = readRawContent(document);
        }

        if (fullText == null || fullText.isBlank()) {
            throw new BusinessException("文档解析后无文本内容");
        }

        DocumentPreviewVO preview = new DocumentPreviewVO();
        preview.setDocumentId(document.getId());
        preview.setFileName(document.getFileName());
        preview.setPreviewText(fullText);
        return preview;
    }

    /**
     * 直接从对象存储读取文本/Markdown文件的原始内容。
     */
    private String readRawContent(DocumentEntity document) {
        String bucket = resolveBucket(document);
        String objectKey = document.getStorageObjectKey();
        try (InputStream inputStream = storageService.getObject(bucket, objectKey)) {
            byte[] bytes = inputStream.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("读取文件失败", e);
        }
    }

    private String resolveBucket(DocumentEntity document) {
        if (document.getStorageBucket() != null && !document.getStorageBucket().isBlank()) {
            return document.getStorageBucket();
        }
        return storageService.getDefaultBucket();
    }

    private DocumentEntity loadDocument(Long documentId, Long groupId) {
        if (documentId == null || documentId <= 0) {
            throw new BusinessException("文档ID非法");
        }
        DocumentEntity document = documentMapper.selectByIdAndGroupId(documentId, groupId);
        if (document == null) {
            throw new BusinessException("文档不存在或已删除");
        }
        if (!DocumentStatus.READY.name().equals(document.getStatus())) {
            throw new BusinessException("文档尚未就绪，暂不可预览");
        }
        return document;
    }

    private void requireGroupId(Long groupId) {
        if (groupId == null || groupId <= 0) {
            throw new BusinessException("groupId 非法");
        }
    }
}
