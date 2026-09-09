package com.example.myargus.ingestion.vector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * 向量写入服务的默认占位实现（在向量模块正式接入前提供无侵入解耦）。
 *
 * @author LeanArgus
 */
@Service
@ConditionalOnMissingBean(VectorIngestionService.class)
@Slf4j
public class DefaultVectorIngestionService implements VectorIngestionService {

    @Override
    public void deleteDocumentVectors(Long documentId) {
        log.debug("No-op deleteDocumentVectors: documentId={}", documentId);
    }
}
