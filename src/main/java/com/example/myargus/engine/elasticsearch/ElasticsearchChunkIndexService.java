package com.example.myargus.engine.elasticsearch;

/**
 * Elasticsearch chunk 索引服务接口。
 *
 * @author LeanArgus
 */
public interface ElasticsearchChunkIndexService {

    /**
     * 删除指定文档的所有 ES 分块索引。
     *
     * @param documentId 文档 ID
     */
    void deleteDocumentChunks(Long documentId);
}
