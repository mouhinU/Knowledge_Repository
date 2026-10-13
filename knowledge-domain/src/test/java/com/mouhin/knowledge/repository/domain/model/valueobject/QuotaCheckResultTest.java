package com.mouhin.knowledge.repository.domain.model.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link QuotaCheckResult} 单测（P6-T3）。
 *
 * <p>覆盖 merge 最严策略选取、actionFromPolicy 映射、null 安全。
 */
@DisplayName("QuotaCheckResult 值对象 (P6-T3)")
class QuotaCheckResultTest {

    @Nested
    @DisplayName("merge · 最严策略选取")
    class Merge {

        @Test
        @DisplayName("null + PASS → PASS")
        void nullAndPass() {
            QuotaCheckResult pass =
                    new QuotaCheckResult(
                            QuotaAction.PASS, "acme", UsageMetric.CHAT_TOKENS, 100, 50);
            assertThat(QuotaCheckResult.merge(null, pass)).isSameAs(pass);
            assertThat(QuotaCheckResult.merge(pass, null)).isSameAs(pass);
        }

        @Test
        @DisplayName("null + null → null")
        void bothNull() {
            assertThat(QuotaCheckResult.merge(null, null)).isNull();
        }

        @Test
        @DisplayName("BLOCK > THROTTLE")
        void blockBeatsThrottle() {
            QuotaCheckResult block =
                    new QuotaCheckResult(
                            QuotaAction.BLOCK, "acme", UsageMetric.CHAT_TOKENS, 100, 200);
            QuotaCheckResult throttle =
                    new QuotaCheckResult(
                            QuotaAction.THROTTLE, "acme", UsageMetric.DOC_COUNT, 50, 60);
            assertThat(QuotaCheckResult.merge(block, throttle)).isSameAs(block);
            assertThat(QuotaCheckResult.merge(throttle, block)).isSameAs(block);
        }

        @Test
        @DisplayName("THROTTLE > ALERT")
        void throttleBeatsAlert() {
            QuotaCheckResult throttle =
                    new QuotaCheckResult(
                            QuotaAction.THROTTLE, "acme", UsageMetric.CHAT_TOKENS, 100, 120);
            QuotaCheckResult alert =
                    new QuotaCheckResult(
                            QuotaAction.ALERT, "acme", UsageMetric.EMBEDDING_TOKENS, 500, 600);
            assertThat(QuotaCheckResult.merge(throttle, alert)).isSameAs(throttle);
        }

        @Test
        @DisplayName("ALERT > PASS")
        void alertBeatsPass() {
            QuotaCheckResult alert =
                    new QuotaCheckResult(
                            QuotaAction.ALERT, "acme", UsageMetric.STORAGE_BYTES, 1000, 1100);
            QuotaCheckResult pass =
                    new QuotaCheckResult(
                            QuotaAction.PASS, "acme", UsageMetric.CHAT_TOKENS, 100, 50);
            assertThat(QuotaCheckResult.merge(alert, pass)).isSameAs(alert);
        }

        @Test
        @DisplayName("同级 BLOCK vs BLOCK → 取前者")
        void sameSeverityKeepsFirst() {
            QuotaCheckResult a =
                    new QuotaCheckResult(
                            QuotaAction.BLOCK, "acme", UsageMetric.CHAT_TOKENS, 100, 200);
            QuotaCheckResult b =
                    new QuotaCheckResult(QuotaAction.BLOCK, "acme", UsageMetric.DOC_COUNT, 50, 60);
            assertThat(QuotaCheckResult.merge(a, b)).isSameAs(a);
        }
    }

    @Nested
    @DisplayName("actionFromPolicy · 策略到动作映射")
    class ActionFromPolicy {

        @Test
        @DisplayName("BLOCK → BLOCK")
        void blockMapping() {
            assertThat(QuotaCheckResult.actionFromPolicy(QuotaPolicy.BLOCK))
                    .isEqualTo(QuotaAction.BLOCK);
        }

        @Test
        @DisplayName("THROTTLE → THROTTLE")
        void throttleMapping() {
            assertThat(QuotaCheckResult.actionFromPolicy(QuotaPolicy.THROTTLE))
                    .isEqualTo(QuotaAction.THROTTLE);
        }

        @Test
        @DisplayName("ALERT → ALERT")
        void alertMapping() {
            assertThat(QuotaCheckResult.actionFromPolicy(QuotaPolicy.ALERT))
                    .isEqualTo(QuotaAction.ALERT);
        }
    }
}
