package com.example.myargus.document.model.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 小文件（一次性直接上传）请求 DTO。
 *
 * @author LeanArgus
 */
@Data
public class UploadDocumentRequest {

    /** 目标群组 ID，文档将归属于该群组 */
    private Long groupId;

    /** 要上传的文件内容 */
    private MultipartFile file;
}
