package com.mouhin.knowledge.repository.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.mouhin.knowledge.repository.domain.exception.QuotaExceededException;
import com.mouhin.knowledge.repository.domain.exception.ThrottleRequiredException;
import com.mouhin.knowledge.repository.domain.gateway.TenantPlanLimitGateway;
import com.mouhin.knowledge.repository.domain.gateway.TenantUsageRecorder;
import com.mouhin.knowledge.repository.domain.model.valueobject.QuotaPolicy;
import com.mouhin.knowledge.repository.domain.model.valueobject.TenantPlanLimit;
import com.mouhin.knowledge.repository.domain.model.valueobject.UsageMetric;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link QuotaGuard} 单测（P6-T3）。
 *
 * <p>覆盖：无限额配置直通、PASS/ALERT/THROTTLE/BLOCK 四种路径、多 metric 最严策略、无限额 (-1) 跳过、 空 tenantKey 校验。
 */
@DisplayName("QuotaGuard 配额前置拦截 (P6-T3)")
class QuotaGuardTest {

    private final TenantUsageRecorder usageRecorder = mock(TenantUsageRecorder.class);
    private final TenantPlanLimitGateway limitGateway = mock(TenantPlanLimitGateway.class);
    private QuotaGuard guard;

    @BeforeEach
    void setUp() {
        guard = new QuotaGuard(usageRecorder, limitGateway);
    }

    @Nested
    @DisplayName("参数校验")
    class ParamValidation {

        @Test
        @DisplayName("null tenantKey → IllegalArgumentException")
        void nullTenantKey() {
            assertThatThrownBy(() -> guard.check(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tenantKey");
        }

        @Test
        @DisplayName("空串 tenantKey → IllegalArgumentException")
        void blankTenantKey() {
            assertThatThrownBy(() -> guard.check("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tenantKey");
        }
    }

    @Nested
    @DisplayName("无限额配置")
    class NoLimits {

        @Test
        @DisplayName("租户无限额配置 → 直通，不查用量")
        void noLimitsPassThrough() {
            when(limitGateway.findByTenantKey("acme")).thenReturn(Collections.emptyList());
            guard.check("acme");
            verify(usageRecorder, never()).getCurrentUsage(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("单 metric 检查")
    class SingleMetric {

        @Test
        @DisplayName("用量 < 限额 → PASS，不抛异常")
        void underQuotaPasses() {
            when(limitGateway.findByTenantKey("acme"))
                    .thenReturn(
                            List.of(
                                    new TenantPlanLimit(
                                            UsageMetric.CHAT_TOKENS, 1000, QuotaPolicy.BLOCK)));
            when(usageRecorder.getCurrentUsage(eq("acme"), eq(UsageMetric.CHAT_TOKENS), any()))
                    .thenReturn(500L);
            guard.check("acme");
        }

        @Test
        @DisplayName("用量 >= 限额 + BLOCK → QuotaExceededException")
        void blockThrows() {
            when(limitGateway.findByTenantKey("acme"))
                    .thenReturn(
                            List.of(
                                    new TenantPlanLimit(
                                            UsageMetric.CHAT_TOKENS, 100, QuotaPolicy.BLOCK)));
            when(usageRecorder.getCurrentUsage(eq("acme"), eq(UsageMetric.CHAT_TOKENS), any()))
                    .thenReturn(200L);
            assertThatThrownBy(() -> guard.check("acme"))
                    .isInstanceOf(QuotaExceededException.class)
                    .satisfies(
                            ex -> {
                                QuotaExceededException qee = (QuotaExceededException) ex;
                                assertThat(qee.result().metric())
                                        .isEqualTo(UsageMetric.CHAT_TOKENS);
                                assertThat(qee.result().quota()).isEqualTo(100);
                                assertThat(qee.result().currentUsage()).isEqualTo(200);
                            });
        }

        @Test
        @DisplayName("用量 >= 限额 + THROTTLE → ThrottleRequiredException")
        void throttleThrows() {
            when(limitGateway.findByTenantKey("acme"))
                    .thenReturn(
                            List.of(
                                    new TenantPlanLimit(
                                            UsageMetric.DOC_COUNT, 50, QuotaPolicy.THROTTLE)));
            when(usageRecorder.getCurrentUsage(eq("acme"), eq(UsageMetric.DOC_COUNT), any()))
                    .thenReturn(50L);
            assertThatThrownBy(() -> guard.check("acme"))
                    .isInstanceOf(ThrottleRequiredException.class);
        }

        @Test
        @DisplayName("用量 >= 限额 + ALERT → 仅日志，不抛异常")
        void alertOnlyLogs() {
            when(limitGateway.findByTenantKey("acme"))
                    .thenReturn(
                            List.of(
                                    new TenantPlanLimit(
                                            UsageMetric.STORAGE_BYTES, 1000, QuotaPolicy.ALERT)));
            when(usageRecorder.getCurrentUsage(eq("acme"), eq(UsageMetric.STORAGE_BYTES), any()))
                    .thenReturn(2000L);
            guard.check("acme"); // 不抛异常
        }

        @Test
        @DisplayName("无限额 (-1) → 跳过该 metric")
        void unlimitedSkipped() {
            when(limitGateway.findByTenantKey("acme"))
                    .thenReturn(
                            List.of(
                                    new TenantPlanLimit(
                                            UsageMetric.CHAT_TOKENS, -1, QuotaPolicy.BLOCK)));
            guard.check("acme");
            verify(usageRecorder, never()).getCurrentUsage(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("多 metric 最严策略")
    class MultiMetric {

        @Test
        @DisplayName("ALERT + BLOCK → 取 BLOCK")
        void alertPlusBlockTakesBlock() {
            when(limitGateway.findByTenantKey("acme"))
                    .thenReturn(
                            List.of(
                                    new TenantPlanLimit(
                                            UsageMetric.CHAT_TOKENS, 100, QuotaPolicy.ALERT),
                                    new TenantPlanLimit(
                                            UsageMetric.DOC_COUNT, 50, QuotaPolicy.BLOCK)));
            when(usageRecorder.getCurrentUsage(eq("acme"), eq(UsageMetric.CHAT_TOKENS), any()))
                    .thenReturn(200L);
            when(usageRecorder.getCurrentUsage(eq("acme"), eq(UsageMetric.DOC_COUNT), any()))
                    .thenReturn(60L);
            assertThatThrownBy(() -> guard.check("acme"))
                    .isInstanceOf(QuotaExceededException.class)
                    .satisfies(
                            ex -> {
                                QuotaExceededException qee = (QuotaExceededException) ex;
                                assertThat(qee.result().metric()).isEqualTo(UsageMetric.DOC_COUNT);
                            });
        }

        @Test
        @DisplayName("PASS + ALERT → 取 ALERT（仅日志）")
        void passPlusAlertTakesAlert() {
            when(limitGateway.findByTenantKey("acme"))
                    .thenReturn(
                            List.of(
                                    new TenantPlanLimit(
                                            UsageMetric.CHAT_TOKENS, 1000, QuotaPolicy.BLOCK),
                                    new TenantPlanLimit(
                                            UsageMetric.DOC_COUNT, 50, QuotaPolicy.ALERT)));
            when(usageRecorder.getCurrentUsage(eq("acme"), eq(UsageMetric.CHAT_TOKENS), any()))
                    .thenReturn(500L); // PASS
            when(usageRecorder.getCurrentUsage(eq("acme"), eq(UsageMetric.DOC_COUNT), any()))
                    .thenReturn(60L); // ALERT
            guard.check("acme"); // 不抛异常，ALERT 仅日志
        }

        @Test
        @DisplayName("全部 PASS → 不抛异常")
        void allPassNoException() {
            when(limitGateway.findByTenantKey("acme"))
                    .thenReturn(
                            List.of(
                                    new TenantPlanLimit(
                                            UsageMetric.CHAT_TOKENS, 1000, QuotaPolicy.BLOCK),
                                    new TenantPlanLimit(
                                            UsageMetric.DOC_COUNT, 500, QuotaPolicy.THROTTLE)));
            when(usageRecorder.getCurrentUsage(eq("acme"), eq(UsageMetric.CHAT_TOKENS), any()))
                    .thenReturn(100L);
            when(usageRecorder.getCurrentUsage(eq("acme"), eq(UsageMetric.DOC_COUNT), any()))
                    .thenReturn(50L);
            guard.check("acme");
        }
    }

    @Nested
    @DisplayName("边界：用量恰好等于限额")
    class Boundary {

        @Test
        @DisplayName("current == quota → 视为超额（>= 语义）")
        void exactQuotaIsExceeded() {
            when(limitGateway.findByTenantKey("acme"))
                    .thenReturn(
                            List.of(
                                    new TenantPlanLimit(
                                            UsageMetric.CHAT_TOKENS, 100, QuotaPolicy.BLOCK)));
            when(usageRecorder.getCurrentUsage(eq("acme"), eq(UsageMetric.CHAT_TOKENS), any()))
                    .thenReturn(100L);
            assertThatThrownBy(() -> guard.check("acme"))
                    .isInstanceOf(QuotaExceededException.class);
        }
    }

    @Nested
    @DisplayName("period 参数")
    class PeriodParam {

        @Test
        @DisplayName("查询用量时 period 为当月 1 日")
        void periodIsFirstDayOfMonth() {
            when(limitGateway.findByTenantKey("acme"))
                    .thenReturn(
                            List.of(
                                    new TenantPlanLimit(
                                            UsageMetric.CHAT_TOKENS, 1000, QuotaPolicy.BLOCK)));
            when(usageRecorder.getCurrentUsage(eq("acme"), eq(UsageMetric.CHAT_TOKENS), any()))
                    .thenReturn(0L);
            guard.check("acme");
            LocalDate expectedPeriod = LocalDate.now().withDayOfMonth(1);
            verify(usageRecorder).getCurrentUsage("acme", UsageMetric.CHAT_TOKENS, expectedPeriod);
        }
    }
}
