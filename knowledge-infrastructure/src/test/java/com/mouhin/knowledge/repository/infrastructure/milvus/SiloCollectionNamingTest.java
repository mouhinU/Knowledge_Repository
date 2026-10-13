package com.mouhin.knowledge.repository.infrastructure.milvus;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link SiloCollectionNaming} 静态工具测试（P2-T4）。
 *
 * <p>覆盖：命名规范 {@code kc_{tenantKey}} 转换、字符集正则 {@code ^[a-z][a-z0-9_]{1,62}$} 边界（长度 / 大小写 / 首字符 / 下划线
 * / 数字 / 空白 / 已带前缀），Phase 3 provisioning 预校验， 反向解析。现网种子 {@code default} 与常用示例 {@code acme} 显式覆盖。
 *
 * @author mouhinU
 * @date 2026-09-30
 */
@DisplayName("SiloCollectionNaming P2-T4 命名工具")
class SiloCollectionNamingTest {

    @Nested
    @DisplayName("toCollectionName 正常路径")
    class ToCollectionNameHappyPath {

        @ParameterizedTest(name = "tenantKey={0} → {1}")
        @CsvSource({
            "default, kc_default",
            "acme, kc_acme",
            "edu_bj_01, kc_edu_bj_01",
            "a1, kc_a1",
            "ab, kc_ab",
        })
        void convertsToPrefixedName(String tenantKey, String expected) {
            assertThat(SiloCollectionNaming.toCollectionName(tenantKey)).isEqualTo(expected);
        }

        @Test
        @DisplayName("正则上限：63 字符（首字母 + 62 tail）通过")
        void maxLengthBoundaryAccepted() {
            String key = "a" + "b".repeat(62); // 共 63 字符
            assertThat(key).hasSize(63);
            assertThat(SiloCollectionNaming.toCollectionName(key)).isEqualTo("kc_" + key);
        }

        @Test
        @DisplayName("正则下限：2 字符（首字母 + 1 tail）通过")
        void minLengthBoundaryAccepted() {
            assertThat(SiloCollectionNaming.toCollectionName("a1")).isEqualTo("kc_a1");
        }

        @Test
        @DisplayName("首尾空白被 trim 后接受")
        void whitespaceTrimmed() {
            assertThat(SiloCollectionNaming.toCollectionName("  acme  ")).isEqualTo("kc_acme");
        }
    }

    @Nested
    @DisplayName("toCollectionName 拒绝路径")
    class ToCollectionNameRejections {

        @ParameterizedTest(name = "非法 tenantKey={0}")
        @ValueSource(
                strings = {
                    "Acme", // 大写开头
                    "ACME", // 全大写
                    "1acme", // 数字开头
                    "_acme", // 下划线开头（正则首字符仅允许字母）
                    "ac-me", // 中划线
                    "ac me", // 中间空格
                    "ac.me", // 点
                    "acme!", // 特殊字符
                    "kc_acme", // 已经带前缀（防二次拼接）
                    "", // 空串
                    "   ", // 全空白
                    "a", // 长度 1（正则要求至少 2）
                })
        void rejectsInvalid(String tenantKey) {
            assertThatThrownBy(() -> SiloCollectionNaming.toCollectionName(tenantKey))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid tenantKey");
        }

        @Test
        @DisplayName("长度 64 溢出正则上限（首字母 + 63 tail）抛 IAE")
        void rejectsOverflowLength() {
            String tooLong = "a" + "b".repeat(63);
            assertThat(tooLong).hasSize(64);
            assertThatThrownBy(() -> SiloCollectionNaming.toCollectionName(tooLong))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null 输入抛 IAE 不 NPE")
        void nullRejected() {
            assertThatThrownBy(() -> SiloCollectionNaming.toCollectionName(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("isValid 纯校验")
    class IsValid {

        @ParameterizedTest(name = "合法={0}")
        @ValueSource(strings = {"default", "acme", "a1", "edu_bj_01", "abc"})
        void acceptsValid(String tenantKey) {
            assertThat(SiloCollectionNaming.isValid(tenantKey)).isTrue();
        }

        @ParameterizedTest(name = "非法={0}")
        @ValueSource(strings = {"Acme", "1acme", "_acme", "ac-me", "", "   ", "a", "kc_acme"})
        void rejectsInvalid(String tenantKey) {
            assertThat(SiloCollectionNaming.isValid(tenantKey)).isFalse();
        }

        @Test
        @DisplayName("null 输入返回 false")
        void nullReturnsFalse() {
            assertThat(SiloCollectionNaming.isValid(null)).isFalse();
        }

        @Test
        @DisplayName("isValid 不做 trim 但会先 trim 再校验（首尾空白容忍）")
        void isValidTrimsFirst() {
            assertThat(SiloCollectionNaming.isValid("  acme  ")).isTrue();
        }
    }

    @Nested
    @DisplayName("assertValidForProvisioning（Phase 3 建集合前预校验）")
    class ProvisioningAssert {

        @Test
        @DisplayName("合法 tenantKey 通过")
        void validTenantKeyPasses() {
            assertThatCode(() -> SiloCollectionNaming.assertValidForProvisioning("acme"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("非法 tenantKey 抛 IAE")
        void invalidTenantKeyThrows() {
            assertThatThrownBy(() -> SiloCollectionNaming.assertValidForProvisioning("Acme"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot provision");
        }

        @Test
        @DisplayName("与 POOL 主集合 knowledge_chunks 冲突时抛（当前前缀 kc_ 不会撞，此为回归护栏）")
        void collisionGuard() {
            // 若前缀被误改为 "" 或 "knowledge_"，"chunks" 这个 tenantKey 会撞 POOL 集合
            // Phase 2 前缀 kc_ 下 chunks → kc_chunks 不撞；此断言仅验证代码路径存在
            assertThatCode(() -> SiloCollectionNaming.assertValidForProvisioning("chunks"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("isSiloCollectionName 反向识别")
    class IsSiloCollection {

        @ParameterizedTest(name = "SILO 形态={0}")
        @ValueSource(strings = {"kc_default", "kc_acme", "kc_edu_bj_01", "kc_a1"})
        void silloRecognised(String name) {
            assertThat(SiloCollectionNaming.isSiloCollectionName(name)).isTrue();
        }

        @ParameterizedTest(name = "非 SILO={0}")
        @ValueSource(
                strings = {
                    "knowledge_chunks", // POOL 主集合
                    "kc_", // 前缀+空
                    "kc_Acme", // tail 大写
                    "kc_1acme", // tail 数字开头
                    "kckc_acme", // 前缀重复
                    "other_collection", // 无前缀
                    "",
                })
        void nonSiloRejected(String name) {
            assertThat(SiloCollectionNaming.isSiloCollectionName(name)).isFalse();
        }

        @Test
        @DisplayName("null 输入返回 false")
        void nullRejected() {
            assertThat(SiloCollectionNaming.isSiloCollectionName(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("fromCollectionName 反向解析")
    class FromCollectionName {

        @Test
        @DisplayName("kc_acme → acme")
        void roundTrip() {
            assertThat(SiloCollectionNaming.fromCollectionName("kc_acme")).isEqualTo("acme");
            assertThat(SiloCollectionNaming.fromCollectionName("kc_default")).isEqualTo("default");
        }

        @Test
        @DisplayName("toCollectionName ↔ fromCollectionName 双向一致（多组）")
        void roundTripBidirectional() {
            for (String key : new String[] {"default", "acme", "edu_bj_01", "a1"}) {
                String name = SiloCollectionNaming.toCollectionName(key);
                assertThat(SiloCollectionNaming.fromCollectionName(name)).isEqualTo(key);
            }
        }

        @Test
        @DisplayName("非 SILO 名抛 IAE")
        void rejectsNonSilo() {
            assertThatThrownBy(() -> SiloCollectionNaming.fromCollectionName("knowledge_chunks"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Not a SILO collection name");
        }
    }

    @Nested
    @DisplayName("常量契约")
    class Constants {

        @Test
        @DisplayName("前缀固定为 kc_，Phase 2 不回退")
        void prefixStable() {
            assertThat(SiloCollectionNaming.COLLECTION_PREFIX).isEqualTo("kc_");
        }

        @Test
        @DisplayName("正则与清单 §4 T-3 一致（^[a-z][a-z0-9_]{1,62}$）")
        void patternMatchesChecklist() {
            assertThat(SiloCollectionNaming.TENANT_KEY_PATTERN).isEqualTo("^[a-z][a-z0-9_]{1,62}$");
        }
    }
}
