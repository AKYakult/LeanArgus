package com.example.myargus.document.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档分片上传块实体，对应 document_upload_chunks 表。
 * <p>
 * 记录大文件分片上传中每个分片的元数据。
 * 支持断点续传：客户端可通过已上传分片列表跳过已成功的块。
 * </p>
 *
 * @author LeanArgus
 */
@Data
@TableName("document_upload_chunks")
public class DocumentUploadChunkEntity {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属上传会话标识，UUID 字符串，对应 document_upload_sessions 表的 uploadId */
    private String uploadId;

    /** 分片序号，从 0 开始递增 */
    private Integer chunkIndex;

    /** 当前分片大小，单位：字节 */
    private Long chunkSize;

    /** 当前分片的 SHA-256 哈希值，用于校验分片完整性 */
    private String chunkHash;

    /** 对象存储桶名称 */
    private String storageBucket;

    /** 对象存储键（Object Key），该分片在对象存储中的临时路径标识 */
    private String storageObjectKey;

    /** 该分片上传完成的时间 */
    private LocalDateTime uploadedAt;

    /** 记录创建时间 */
    private LocalDateTime createdAt;

    /** 记录最后更新时间 */
    private LocalDateTime updatedAt;
}
