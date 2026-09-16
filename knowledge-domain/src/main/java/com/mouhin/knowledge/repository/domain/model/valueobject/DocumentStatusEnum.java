package com.mouhin.knowledge.repository.domain.model.valueobject;

/**
 * 文档处理状态枚举
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
public enum DocumentStatusEnum {

    /**
     * 已上传，等待处理
     */
    UPLOADED,

    /**
     * 正在解析和分块
     */
    PROCESSING,

    /**
     * 已向量化并存入 Milvus
     */
    INDEXED,

    /**
     * 处理失败
     */
    FAILED,

    /**
     * 已归档，不参与检索
     */
    ARCHIVED
}
