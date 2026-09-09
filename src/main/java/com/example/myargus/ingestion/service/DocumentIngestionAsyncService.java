package com.example.myargus.ingestion.service;

/**
 * 异步文档摄入（ETL）服务接口。
 *
 * @author LeanArgus
 */
public interface DocumentIngestionAsyncService {

    /**
     * 摄入指定文档（解析、分块、写入向量及全文索引）。
     *
     * @param documentId 文档 ID
     * @param groupId    群组 ID
     */
    void ingestDocument(Long documentId, Long groupId);
}
