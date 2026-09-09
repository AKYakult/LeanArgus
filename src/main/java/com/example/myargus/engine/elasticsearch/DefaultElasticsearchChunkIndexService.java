package com.example.myargus.engine.elasticsearch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Elasticsearch 索引服务的默认占位实现。
 *
 * @author LeanArgus
 */
@Service
@ConditionalOnMissingBean(ElasticsearchChunkIndexService.class)
@Slf4j
public class DefaultElasticsearchChunkIndexService implements ElasticsearchChunkIndexService {

    @Override
    public void deleteDocumentChunks(Long documentId) {
        log.debug("No-op deleteDocumentChunks: documentId={}", documentId);
    }
}
