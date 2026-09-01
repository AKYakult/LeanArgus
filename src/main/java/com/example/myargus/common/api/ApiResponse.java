package com.example.myargus.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一 API 响应体 (基于 Record)
 *
 * @param <T>     数据类型
 * @param success 请求是否成功
 * @param data    响应数据
 * @param message 提示信息（成功或失败说明）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, String message) {
    /** 成功响应（带数据） */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "操作成功");
    }

    /** 成功响应（无数据） */
    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, "操作成功");
    }

    /** 失败响应 */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
