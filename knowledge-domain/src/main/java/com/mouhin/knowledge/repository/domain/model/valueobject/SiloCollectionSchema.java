package com.mouhin.knowledge.repository.domain.model.valueobject;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * SILO 独立集合 schema 摘要（domain 值对象，P3-T6）。
 *
 * <p>不可变 record，承载 {@code SiloCollectionAdmin} 读回的集合元数据：集合名、租户键、字段列表、向量字段列表、 是否启用动态字段、分区数。用于 schema
 * 对齐巡检与健康体检端点。
 *
 * @param collectionName     Milvus 侧集合全名（如 {@code kc_default}）
 * @param tenantKey          租户业务键（如 {@code default}）
 * @param fieldNames         标量字段名列表（不可变）
 * @param vectorFieldNames   向量字段名列表（不可变）
 * @param enableDynamicField 是否启用动态字段
 * @param numOfPartitions    分区数（Milvus 侧实际值）
 * @author QoderWork
 * @date 2026-10-12
 */
public record SiloCollectionSchema(
        String collectionName,
        String tenantKey,
        List<String> fieldNames,
        List<String> vectorFieldNames,
        boolean enableDynamicField,
        long numOfPartitions) {

    /**
     * 紧凑构造器：防御性拷贝列表并包装为不可变。
     */
    public SiloCollectionSchema {
        Objects.requireNonNull(collectionName, "collectionName");
        Objects.requireNonNull(tenantKey, "tenantKey");
        fieldNames = fieldNames == null ? Collections.emptyList() : List.copyOf(fieldNames);
        vectorFieldNames =
                vectorFieldNames == null ? Collections.emptyList() : List.copyOf(vectorFieldNames);
    }

    /**
     * 判断该集合是否包含指定标量字段。
     *
     * @param fieldName 字段名
     * @return 包含返回 {@code true}
     */
    public boolean hasField(String fieldName) {
        return fieldNames.contains(fieldName);
    }

    /**
     * 判断该集合是否包含指向量字段。
     *
     * @param fieldName 向量字段名
     * @return 包含返回 {@code true}
     */
    public boolean hasVectorField(String fieldName) {
        return vectorFieldNames.contains(fieldName);
    }
}
