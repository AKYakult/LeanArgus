package com.example.myargus.common.enums;

/**
 * 文档处理状态枚举。
 *
 * @author LeanArgus
 */
public enum DocumentStatus {
    /** 已上传，等待异步解析处理 */
    UPLOADED,
    /** 正在切片/向量化处理中 */
    PROCESSING,
    /** 处理完成，可被检索使用 */
    READY,
    /** 处理失败 */
    FAILED
}
