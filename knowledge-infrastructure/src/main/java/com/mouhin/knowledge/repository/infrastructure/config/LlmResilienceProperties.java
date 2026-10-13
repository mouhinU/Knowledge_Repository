package com.mouhin.knowledge.repository.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 调用韧性配置（{@code knowledge.llm.resilience.*}）—— Phase R2 五独立之「熔断 / 重试」按角色隔离。
 *
 * <p>为 chat / chat-stream / embedding（vision 于 Phase B）各配一套熔断 + 重试参数，彼此独立触发：某端点抖动只打开该角色
 * 熔断器，不殃及他角色。流式角色默认关闭重试（重试会向已吐字的 UI 重复推送增量），仅保留熔断快速失败。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "knowledge.llm.resilience")
public class LlmResilienceProperties {

    /** 非流式对话角色。 */
    private Role chat = Role.of(3, 500L);

    /** 流式对话角色：不重试（maxAttempts=1），避免增量重复推送。 */
    private Role chatStream = Role.of(1, 0L);

    /** 向量角色：短重试。 */
    private Role embedding = Role.of(2, 200L);

    /** 视觉角色（Phase B 起消费）。 */
    private Role vision = Role.of(2, 300L);

    /** 单个角色的熔断 + 重试配置。 */
    @Getter
    @Setter
    public static class Role {

        private final CircuitBreaker circuitBreaker = new CircuitBreaker();
        private final Retry retry = new Retry();

        /**
         * 构造角色配置并套用重试默认值。
         *
         * @param maxAttempts 最大尝试次数（1 表示不重试）
         * @param waitDurationMs 重试间隔基准毫秒
         * @return 角色配置
         */
        public static Role of(int maxAttempts, long waitDurationMs) {
            Role role = new Role();
            role.getRetry().setMaxAttempts(maxAttempts);
            role.getRetry().setWaitDurationMs(waitDurationMs);
            return role;
        }
    }

    /** 熔断器参数（count-based 滑动窗口 + 慢调用比例）。 */
    @Getter
    @Setter
    public static class CircuitBreaker {

        /** 滑动窗口大小（调用次数）。 */
        private int slidingWindowSize = 20;

        /** 失败率阈值（百分比），超过则开闸。 */
        private float failureRateThreshold = 50F;

        /** 慢调用判定阈值（秒）。 */
        private int slowCallDurationSeconds = 60;

        /** 慢调用比例阈值（百分比）。 */
        private float slowCallRateThreshold = 100F;

        /** 开闸后进入半开的等待时长（秒）。 */
        private int waitDurationSeconds = 30;

        /** 半开状态允许的探测调用数。 */
        private int permittedNumberOfCallsInHalfOpen = 5;
    }

    /** 重试器参数（指数退避）。 */
    @Getter
    @Setter
    public static class Retry {

        /** 最大尝试次数（含首次），1 表示不重试。 */
        private int maxAttempts = 3;

        /** 首次重试等待毫秒。 */
        private long waitDurationMs = 500L;

        /** 退避倍数。 */
        private double backoffMultiplier = 2.0D;
    }
}
