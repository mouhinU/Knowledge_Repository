package com.mouhin.knowledge.repository.domain.model.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link UsageMetric} 单测（P6-T1）。
 */
@DisplayName("UsageMetric 枚举 (P6-T1)")
class UsageMetricTest {

    @Nested
    @DisplayName("code · 存储码")
    class Code {

        @Test
        @DisplayName("各枚举 code 不为空且为小写下划线")
        void allCodesAreSnakeCase() {
            for (UsageMetric m : UsageMetric.values()) {
                assertThat(m.code()).matches("[a-z_]+");
            }
        }

        @Test
        @DisplayName("CHAT_TOKENS → chat_tokens")
        void chatTokensCode() {
            assertThat(UsageMetric.CHAT_TOKENS.code()).isEqualTo("chat_tokens");
        }
    }

    @Nested
    @DisplayName("fromCode · 反查")
    class FromCode {

        @Test
        @DisplayName("已知 code 可正确反查")
        void knownCodes() {
            assertThat(UsageMetric.fromCode("chat_tokens")).isEqualTo(UsageMetric.CHAT_TOKENS);
            assertThat(UsageMetric.fromCode("embedding_tokens"))
                    .isEqualTo(UsageMetric.EMBEDDING_TOKENS);
            assertThat(UsageMetric.fromCode("doc_count")).isEqualTo(UsageMetric.DOC_COUNT);
            assertThat(UsageMetric.fromCode("storage_bytes")).isEqualTo(UsageMetric.STORAGE_BYTES);
        }

        @Test
        @DisplayName("未知 code 抛 IllegalArgumentException")
        void unknownCodeThrows() {
            assertThatThrownBy(() -> UsageMetric.fromCode("unknown_metric"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown metric");
        }
    }
}
