package com.mouhin.knowledge.repository.domain.model.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * {@link SiloCollectionSchema} 值对象单测（P3-T6）。
 *
 * @author QoderWork
 * @date 2026-10-12
 */
@DisplayName("SILO 集合 schema 摘要 (SiloCollectionSchema)")
class SiloCollectionSchemaTest {

    @Test
    @DisplayName("正常构造 → 字段可访问")
    void normalConstruction() {
        SiloCollectionSchema schema =
                new SiloCollectionSchema(
                        "kc_acme",
                        "acme",
                        List.of("id", "metadata"),
                        List.of("embedding"),
                        true,
                        16L);

        assertThat(schema.collectionName()).isEqualTo("kc_acme");
        assertThat(schema.tenantKey()).isEqualTo("acme");
        assertThat(schema.fieldNames()).containsExactly("id", "metadata");
        assertThat(schema.vectorFieldNames()).containsExactly("embedding");
        assertThat(schema.enableDynamicField()).isTrue();
        assertThat(schema.numOfPartitions()).isEqualTo(16L);
    }

    @Test
    @DisplayName("null 列表 → 转为空不可变列表")
    void nullListsBecomeEmpty() {
        SiloCollectionSchema schema =
                new SiloCollectionSchema("kc_test", "test", null, null, false, 0L);

        assertThat(schema.fieldNames()).isEmpty();
        assertThat(schema.vectorFieldNames()).isEmpty();
    }

    @Test
    @DisplayName("collectionName 为 null → NPE")
    void nullCollectionNameThrows() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new SiloCollectionSchema(
                                        null, "test", List.of(), List.of(), false, 0L));
    }

    @Test
    @DisplayName("tenantKey 为 null → NPE")
    void nullTenantKeyThrows() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new SiloCollectionSchema(
                                        "kc_test", null, List.of(), List.of(), false, 0L));
    }

    @Test
    @DisplayName("列表不可变 → 外部修改不影响内部")
    void listsAreImmutable() {
        var mutableFields = new java.util.ArrayList<>(List.of("id", "metadata"));
        SiloCollectionSchema schema =
                new SiloCollectionSchema(
                        "kc_test", "test", mutableFields, List.of("embedding"), true, 8L);

        mutableFields.add("hacked");

        assertThat(schema.fieldNames()).containsExactly("id", "metadata");
    }

    @Test
    @DisplayName("hasField → 正确判断字段存在性")
    void hasFieldWorks() {
        SiloCollectionSchema schema =
                new SiloCollectionSchema(
                        "kc_test",
                        "test",
                        List.of("id", "metadata", "tenant_id"),
                        List.of("embedding"),
                        true,
                        8L);

        assertThat(schema.hasField("metadata")).isTrue();
        assertThat(schema.hasField("nonexistent")).isFalse();
    }

    @Test
    @DisplayName("hasVectorField → 正确判断向量字段存在性")
    void hasVectorFieldWorks() {
        SiloCollectionSchema schema =
                new SiloCollectionSchema(
                        "kc_test",
                        "test",
                        List.of("id"),
                        List.of("embedding", "title_vector"),
                        true,
                        8L);

        assertThat(schema.hasVectorField("embedding")).isTrue();
        assertThat(schema.hasVectorField("nonexistent")).isFalse();
    }

    @Test
    @DisplayName("equals / hashCode → 同值相等")
    void equalsAndHashCode() {
        SiloCollectionSchema a =
                new SiloCollectionSchema(
                        "kc_test", "test", List.of("id"), List.of("embedding"), true, 8L);
        SiloCollectionSchema b =
                new SiloCollectionSchema(
                        "kc_test", "test", List.of("id"), List.of("embedding"), true, 8L);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
