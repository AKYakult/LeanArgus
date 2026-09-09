package com.example.myargus.ingestion.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.myargus.common.enums.DocumentStatus;
import com.example.myargus.document.mapper.DocumentMapper;
import com.example.myargus.document.model.entity.DocumentEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 异步文档摄入服务的默认占位实现。
 * <p>
 * 在 ingestion 模块正式就绪前，收到事件后将文档状态安全转换为 READY，确保文档模块业务全流程跑通。
 * </p>
 *
 * @author LeanArgus
 */
@Service
@ConditionalOnMissingBean(DocumentIngestionAsyncService.class)
@Slf4j
public class DefaultDocumentIngestionAsyncService implements DocumentIngestionAsyncService {

    private final DocumentMapper documentMapper;

    public DefaultDocumentIngestionAsyncService(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    @Override
    public void ingestDocument(Long documentId, Long groupId) {
        log.info("异步文档摄入事件开始处理: documentId={}, groupId={}", documentId, groupId);
        // 自动完成状态流转：PROCESSING -> READY
        documentMapper.update(null, new LambdaUpdateWrapper<DocumentEntity>()
                .eq(DocumentEntity::getId, documentId)
                .eq(DocumentEntity::getGroupId, groupId)
                .set(DocumentEntity::getStatus, DocumentStatus.READY.name())
                .set(DocumentEntity::getProcessedAt, LocalDateTime.now())
        );
        log.info("文档异步状态流转完成(READY): documentId={}", documentId);
    }
}
