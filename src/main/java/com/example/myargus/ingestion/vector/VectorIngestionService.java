package com.example.myargus.ingestion.vector;

/**
 * 向量写入服务接口。
 *
 * @author LeanArgus
 */
public interface VectorIngestionService {

    /**
     * 删除指定文档的所有向量记录。
     *
     * @param documentId 文档 ID
     */
    void deleteDocumentVectors(Long documentId);
}
