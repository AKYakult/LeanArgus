package com.example.myargus.document.model.dto;

/**
 * 分片上传初始化请求 DTO。
 * <p>
 * 客户端提交文件元数据和分片参数，服务端根据 fileHash 判断是否可以秒传或断点续传，否则新建会话。
 * </p>
 *
 * @author LeanArgus
 */
public record UploadInitRequest(
        /** 目标群组 ID，文档将归属于该群组 */
        Long groupId,
        /** 原始文件名 */
        String fileName,
        /** 文件总大小，单位：字节 */
        Long fileSize,
        /** MIME 内容类型（如 "application/pdf"） */
        String contentType,
        /** 文件 SHA-256 哈希值，用于秒传校验 */
        String fileHash,
        /** 每个分片的大小，单位：字节 */
        Long chunkSize,
        /** 总分片数量 */
        Integer chunkCount
) {
}
