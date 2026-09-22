package com.mouhin.knowledge.repository.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mouhin.knowledge.repository.infrastructure.config.LlmResilienceProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link LlmResilience} 契约测试（Phase R2）：验证 per-role 重试 / 熔断 / 隔离 / 指标。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
class LlmResilienceTest {

    private MeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
    }

    /** 默认角色配置（chat 重试 3 次），仅把 chat 退避压到 1ms 保证测试快。 */
    private LlmResilienceProperties defaultProps() {
        LlmResilienceProperties props = new LlmResilienceProperties();
        props.getChat().getRetry().setWaitDurationMs(1L);
        props.getEmbedding().getRetry().setWaitDurationMs(1L);
        props.getVision().getRetry().setWaitDurationMs(1L);
        return props;
    }

    @Nested
    @DisplayName("重试")
    class Retry {

        @Test
        @DisplayName("chat 失败后按 maxAttempts 重试并最终成功")
        void retriesThenSucceeds() {
            LlmResilience resilience = new LlmResilience(defaultProps(), registry);
            AtomicInteger calls = new AtomicInteger();
            String result =
                    resilience.run(
                            "chat",
                            () -> {
                                if (calls.incrementAndGet() < 3) {
                                    throw new RuntimeException("transient");
                                }
                                return "ok";
                            });
            assertThat(result).isEqualTo("ok");
            assertThat(calls.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("chat-stream 角色不重试，失败仅调用一次")
        void chatStreamDoesNotRetry() {
            LlmResilience resilience = new LlmResilience(defaultProps(), registry);
            AtomicInteger calls = new AtomicInteger();
            assertThatThrownBy(
                            () ->
                                    resilience.run(
                                            "chat-stream",
                                            () -> {
                                                calls.incrementAndGet();
                                                throw new RuntimeException("boom");
                                            }))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("boom");
            assertThat(calls.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("熔断")
    class CircuitBreaker {

        /** chat 关闭重试 + 小窗口，便于在两次失败后即开闸。 */
        private LlmResilienceProperties openAfterTwo() {
            LlmResilienceProperties props = defaultProps();
            props.getChat().getRetry().setMaxAttempts(1);
            LlmResilienceProperties.CircuitBreaker cb = props.getChat().getCircuitBreaker();
            cb.setSlidingWindowSize(2);
            cb.setFailureRateThreshold(50F);
            cb.setWaitDurationSeconds(600);
            cb.setPermittedNumberOfCallsInHalfOpen(1);
            return props;
        }

        @Test
        @DisplayName("失败率超阈值后开闸，快速失败且不再触达底层调用")
        void opensAfterThreshold() {
            LlmResilience resilience = new LlmResilience(openAfterTwo(), registry);
            AtomicInteger calls = new AtomicInteger();
            for (int i = 0; i < 2; i++) {
                assertThatThrownBy(
                                () ->
                                        resilience.run(
                                                "chat",
                                                () -> {
                                                    calls.incrementAndGet();
                                                    throw new RuntimeException("down");
                                                }))
                        .isInstanceOf(RuntimeException.class)
                        .hasMessage("down");
            }
            assertThat(calls.get()).isEqualTo(2);
            // 第三次应被熔断器直接拒绝，calls 不再增长
            assertThatThrownBy(
                            () ->
                                    resilience.run(
                                            "chat",
                                            () -> {
                                                calls.incrementAndGet();
                                                return "never";
                                            }))
                    .isInstanceOf(CallNotPermittedException.class);
            assertThat(calls.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("chat 开闸不影响 embedding 角色（per-role 隔离）")
        void isolationAcrossRoles() {
            LlmResilience resilience = new LlmResilience(openAfterTwo(), registry);
            for (int i = 0; i < 2; i++) {
                assertThatThrownBy(
                                () ->
                                        resilience.run(
                                                "chat",
                                                () -> {
                                                    throw new RuntimeException("down");
                                                }))
                        .isInstanceOf(RuntimeException.class);
            }
            // chat 已 OPEN；embedding 独立熔断器，调用正常
            String out = resilience.run("embedding", () -> "embedded");
            assertThat(out).isEqualTo("embedded");
        }
    }

    @Nested
    @DisplayName("可观测指标")
    class Metrics {

        @Test
        @DisplayName("注册 knowledge.llm.call 计时器与 resilience4j 熔断/重试指标")
        void registersMeters() {
            LlmResilience resilience = new LlmResilience(defaultProps(), registry);
            resilience.run("chat", () -> "ok");

            assertThat(
                            registry.find("knowledge.llm.call")
                                    .tag("role", "chat")
                                    .tag("success", "true")
                                    .timer())
                    .isNotNull();
            assertThat(registry.getMeters().stream())
                    .anyMatch(m -> m.getId().getName().startsWith("resilience4j.circuitbreaker"));
            assertThat(registry.getMeters().stream())
                    .anyMatch(m -> m.getId().getName().startsWith("resilience4j.retry"));
        }
    }
}
