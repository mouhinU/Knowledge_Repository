package com.mouhin.knowledge.repository.infrastructure.llm;

import com.mouhin.knowledge.repository.infrastructure.config.LlmResilienceProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LLM 调用韧性执行器（Phase R2）。
 *
 * <p>按角色（chat / chat-stream / embedding / vision）各维护一套独立的 Resilience4j {@link CircuitBreaker} 与
 * {@link Retry}（{@code maxAttempts<=1} 时不建 Retry），熔断/重试实例统一挂在专属 {@link CircuitBreakerRegistry} /
 * {@link RetryRegistry} 上，经 resilience4j-micrometer 桥接到 {@link MeterRegistry}；同时记录每角色调用耗时与成败计数 （计时器
 * {@code knowledge.llm.call}，标签 {@code role}/{@code success}）。装饰顺序为外层 Retry → 内层
 * CircuitBreaker：每次尝试都计入熔断窗口，达到阈值即开闸快速失败，一角色抖动不影响他角色。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Component
@Slf4j
public class LlmResilience {

    /** 角色 → 熔断器。 */
    private final Map<String, CircuitBreaker> circuitBreakers = new LinkedHashMap<>();

    /** 角色 → 重试器（可能为空表示不重试）。 */
    private final Map<String, Retry> retries = new LinkedHashMap<>();

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final MeterRegistry meterRegistry;

    public LlmResilience(LlmResilienceProperties props, MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.circuitBreakerRegistry =
                CircuitBreakerRegistry.of(
                        toCircuitBreakerConfig(props.getChat().getCircuitBreaker()));
        this.retryRegistry = RetryRegistry.of(toRetryConfig(props.getChat().getRetry()));
        register("chat", props.getChat());
        register("chat-stream", props.getChatStream());
        register("embedding", props.getEmbedding());
        register("vision", props.getVision());
        bindMetrics();
        log.info("LLM resilience initialized for roles={}", circuitBreakers.keySet());
    }

    /**
     * 以指定角色的熔断 + 重试 + 计时包裹执行一次调用。
     *
     * @param role 角色标识（chat / chat-stream / embedding / vision）
     * @param action 实际调用（抛非受检异常表示失败）
     * @param <T> 返回类型
     * @return 调用结果
     */
    public <T> T run(String role, Supplier<T> action) {
        CircuitBreaker cb = circuitBreakers.get(role);
        Retry retry = retries.get(role);
        Supplier<T> decorated = cb != null ? cb.decorateSupplier(action) : action;
        if (retry != null) {
            decorated = Retry.decorateSupplier(retry, decorated);
        }
        long start = System.nanoTime();
        try {
            T result = decorated.get();
            recordCall(role, start, true);
            return result;
        } catch (RuntimeException e) {
            recordCall(role, start, false);
            throw e;
        }
    }

    private void register(String role, LlmResilienceProperties.Role cfg) {
        CircuitBreaker cb =
                circuitBreakerRegistry.circuitBreaker(
                        role, toCircuitBreakerConfig(cfg.getCircuitBreaker()));
        circuitBreakers.put(role, cb);
        LlmResilienceProperties.Retry rc = cfg.getRetry();
        if (rc.getMaxAttempts() > 1) {
            retries.put(role, retryRegistry.retry(role, toRetryConfig(rc)));
        }
    }

    private void bindMetrics() {
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry)
                .bindTo(meterRegistry);
        TaggedRetryMetrics.ofRetryRegistry(retryRegistry).bindTo(meterRegistry);
    }

    private void recordCall(String role, long startNanos, boolean success) {
        meterRegistry
                .timer("knowledge.llm.call", "role", role, "success", String.valueOf(success))
                .record(Duration.ofNanos(System.nanoTime() - startNanos));
    }

    private CircuitBreakerConfig toCircuitBreakerConfig(LlmResilienceProperties.CircuitBreaker c) {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(c.getFailureRateThreshold())
                .slowCallRateThreshold(c.getSlowCallRateThreshold())
                .slowCallDurationThreshold(Duration.ofSeconds(c.getSlowCallDurationSeconds()))
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(c.getSlidingWindowSize())
                .minimumNumberOfCalls(c.getSlidingWindowSize())
                .permittedNumberOfCallsInHalfOpenState(c.getPermittedNumberOfCallsInHalfOpen())
                .waitDurationInOpenState(Duration.ofSeconds(c.getWaitDurationSeconds()))
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
    }

    private RetryConfig toRetryConfig(LlmResilienceProperties.Retry r) {
        return RetryConfig.custom()
                .maxAttempts(r.getMaxAttempts())
                .intervalFunction(
                        IntervalFunction.ofExponentialBackoff(
                                Duration.ofMillis(Math.max(r.getWaitDurationMs(), 1L)),
                                Math.max(r.getBackoffMultiplier(), 1.0D)))
                .build();
    }
}
