package com.mouhin.knowledge.repository.domain.model.valueobject;

import java.time.Instant;
import java.util.Objects;

/**
 * 对象存储句柄值对象（P5-T1）。
 *
 * <p>封装一次存储操作的返回结果，承载调用方后续检索 / 删除 / 元数据查询所需的不可变信息。 设计原则：
 *
 * <ul>
 *   <li>不可变——所有字段在构造时确定，无 setter。
 *   <li>不含 infra 特有类型（如 MinIO {@code Item} / {@code StatObjectResponse}）， 符合红线 #7「domain 层保持纯净」。
 *   <li>{@link #storageKey} 是 opaque 标识符——本地磁盘实现返回绝对路径，MinIO 实现返回对象 key， 调用方只存入 DB {@code
 *       storage_path} 列，不做语义解析。
 * </ul>
 *
 * @author mouhinU
 * @date 2026-10-12
 */
public final class StoredObject {

    private final String storageKey;
    private final String originalName;
    private final String contentType;
    private final long size;
    private final String tenantKey;
    private final StorageCategory category;
    private final Instant createdAt;

    /**
     * 全参构造。
     *
     * @param storageKey   不透明存储标识（本地路径 or MinIO 对象 key）
     * @param originalName 用户上传时的原始文件名，可为 null
     * @param contentType  MIME 类型，可为 null（由调用方后续探测）
     * @param size         文件大小（字节），负数表示未知
     * @param tenantKey    租户键
     * @param category     存储分类
     * @param createdAt    存储时间戳，可为 null（由实现填充）
     */
    public StoredObject(
            String storageKey,
            String originalName,
            String contentType,
            long size,
            String tenantKey,
            StorageCategory category,
            Instant createdAt) {
        Objects.requireNonNull(storageKey, "storageKey must not be null");
        Objects.requireNonNull(tenantKey, "tenantKey must not be null");
        Objects.requireNonNull(category, "category must not be null");
        if (storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey must not be blank");
        }
        this.storageKey = storageKey;
        this.originalName = originalName;
        this.contentType = contentType;
        this.size = size;
        this.tenantKey = tenantKey;
        this.category = category;
        this.createdAt = createdAt;
    }

    /**
     * 便捷构造（不含可选元数据），{@link #createdAt} 取当前时刻。
     */
    public StoredObject(
            String storageKey, String tenantKey, StorageCategory category, String originalName) {
        this(storageKey, originalName, null, -1L, tenantKey, category, Instant.now());
    }

    public String storageKey() {
        return storageKey;
    }

    public String originalName() {
        return originalName;
    }

    public String contentType() {
        return contentType;
    }

    /**
     * 文件大小（字节）；负数表示未知。
     */
    public long size() {
        return size;
    }

    public String tenantKey() {
        return tenantKey;
    }

    public StorageCategory category() {
        return category;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StoredObject that)) return false;
        return size == that.size
                && storageKey.equals(that.storageKey)
                && tenantKey.equals(that.tenantKey)
                && category == that.category;
    }

    @Override
    public int hashCode() {
        return Objects.hash(storageKey, tenantKey, category, size);
    }

    @Override
    public String toString() {
        return "StoredObject[key="
                + storageKey
                + ", tenant="
                + tenantKey
                + ", category="
                + category
                + ", size="
                + size
                + "]";
    }
}
