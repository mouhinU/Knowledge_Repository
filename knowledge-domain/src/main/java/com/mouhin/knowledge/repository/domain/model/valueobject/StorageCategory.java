package com.mouhin.knowledge.repository.domain.model.valueobject;

/**
 * 对象存储分类标签（P5-T1）。
 *
 * <p>标识文件在存储桶 / 本地根目录下的逻辑分类，决定路径前缀与访问模式：
 *
 * <ul>
 *   <li>{@link #DOCUMENT} — 用户上传的源文档（PDF / Word / Excel 等），路径前缀 {@code {tenantKey}/documents/}。
 *   <li>{@link #EXAM_ASSET} — 文档配图二进制（由图片提取器写入），路径前缀 {@code {tenantKey}/exam-assets/}。
 * </ul>
 *
 * <p>放在 domain 层作为值对象枚举，infra 实现据此拼出存储路径 / 对象 key，app 层调用方传入分类标签。
 *
 * @author mouhinU
 * @date 2026-10-12
 */
public enum StorageCategory {

    /**
     * 用户上传的源文档。
     */
    DOCUMENT("documents"),

    /**
     * 文档配图二进制（考试看图题等）。
     */
    EXAM_ASSET("exam-assets");

    private final String pathSegment;

    StorageCategory(String pathSegment) {
        this.pathSegment = pathSegment;
    }

    /**
     * 返回该分类在存储路径中的段名（如 {@code "documents"} / {@code "exam-assets"}）。
     */
    public String pathSegment() {
        return pathSegment;
    }
}
