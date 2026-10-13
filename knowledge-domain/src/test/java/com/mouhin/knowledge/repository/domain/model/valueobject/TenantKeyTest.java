package com.mouhin.knowledge.repository.domain.model.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TenantKey} 租户键规范单测（P3-T1 单一事实源）。
 *
 * <p>锁定归一化（trim + 小写）与严格校验规则 {@code ^[a-z][a-z0-9_]{1,62}$} 的边界，保证 app 开通校验与 infra SILO
 * 集合命名口径一致、不漂移。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@DisplayName("TenantKey 租户键规范 (P3-T1)")
class TenantKeyTest {

    @Test
    @DisplayName("normalize: trim + 小写；null 安全")
    void normalizeTrimsAndLowercases() {
        assertThat(TenantKey.normalize("  AcMe  ")).isEqualTo("acme");
        assertThat(TenantKey.normalize(null)).isNull();
    }

    @ParameterizedTest(name = "合法={0}")
    @ValueSource(strings = {"default", "acme", "a1", "edu_bj_01", "abc"})
    @DisplayName("isValid 接受合法键")
    void acceptsValid(String key) {
        assertThat(TenantKey.isValid(key)).isTrue();
    }

    @ParameterizedTest(name = "非法={0}")
    @ValueSource(strings = {"Acme", "1acme", "_acme", "ac-me", "ac me", "", "   ", "a"})
    @DisplayName("isValid 拒绝非法键（大写/数字或下划线开头/中划线/空格/过短/空白）")
    void rejectsInvalid(String key) {
        assertThat(TenantKey.isValid(key)).isFalse();
    }

    @Test
    @DisplayName("isValid: null 返回 false 不 NPE")
    void nullReturnsFalse() {
        assertThat(TenantKey.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("isValid 容忍首尾空白但大小写敏感（大写拒绝）")
    void toleratesWhitespaceButRejectsUppercase() {
        assertThat(TenantKey.isValid("  acme  ")).isTrue();
        assertThat(TenantKey.isValid("ACME")).isFalse();
        assertThat(TenantKey.isValid("  Acme  ")).isFalse();
    }

    @Test
    @DisplayName("边界：63 字符通过、64 字符拒绝")
    void lengthBoundary() {
        String max = "a" + "b".repeat(62);
        String overflow = "a" + "b".repeat(63);
        assertThat(max).hasSize(63);
        assertThat(overflow).hasSize(64);
        assertThat(TenantKey.isValid(max)).isTrue();
        assertThat(TenantKey.isValid(overflow)).isFalse();
    }
}
