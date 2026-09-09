package com.example.myargus.document.model.dto;

import org.springframework.web.multipart.MultipartFile;

/**
 * 分片上传请求 DTO。
 *
 * @author LeanArgus
 */
public record UploadChunkRequest(
        /** 上传会话标识（UUID） */
        String uploadId,
        /** 分片序号，从 0 开始 */
        Integer chunkIndex,
        /** 当前分片的 SHA-256 哈希值 */
        String chunkHash,
        /** 分片文件的二进制内容 */
        MultipartFile chunk
) {
}
