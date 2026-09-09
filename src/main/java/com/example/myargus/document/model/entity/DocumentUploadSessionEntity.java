package com.example.myargus.document.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档上传会话实体，对应 document_upload_sessions 表。
 * <p>
 * 记录大文件分片上传的会话信息。
 * 支持秒传复用和断点续传。
 * </p>
 *
 * @author LeanArgus
 */
@Data
@TableName("document_upload_sessions")
public class DocumentUploadSessionEntity {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上传会话唯一标识，UUID 字符串 */
    private String uploadId;

    /** 所属群组 ID，外键引用 groups 表 */
    private Long groupId;

    /** 上传者用户 ID，外键引用 users 表 */
    private Long uploaderUserId;

    /** 原始文件名 */
    private String fileName;

    /** 文件扩展名（不含点号） */
    private String fileExt;

    /** MIME 内容类型 */
    private String contentType;

    /** 文件总大小，单位：字节 */
    private Long fileSize;

    /** 文件 SHA-256 哈希值，用于秒传校验 */
    private String fileHash;

    /** 每个分片的大小，单位：字节 */
    private Long chunkSize;

    /** 总分片数量 */
    private Integer chunkCount;

    /**
     * 上传会话状态。
     * <ul>
     *   <li>INIT -- 会话已初始化</li>
     *   <li>UPLOADING -- 分片上传中</li>
     *   <li>COMPLETING -- 正在合并分片</li>
     *   <li>COMPLETED -- 合并完成</li>
     *   <li>EXPIRED -- 会话已过期</li>
     * </ul>
     */
    private String status;

    /** 对象存储桶名称 */
    private String storageBucket;

    /** 合并后的对象存储键（Object Key） */
    private String mergedObjectKey;

    /** 会话过期时间 */
    private LocalDateTime expiresAt;

    /** 会话创建时间 */
    private LocalDateTime createdAt;

    /** 会话最后更新时间 */
    private LocalDateTime updatedAt;
}
