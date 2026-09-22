package com.mouhin.knowledge.repository.infrastructure.observability;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * 文档解析可观测埋点（Phase D）。
 *
 * <p>以 Micrometer 登记解析调用量、耗时、缓存命中/未命中与视觉调用/失败计数。<b>标签仅含策略名 / MIME / 结果 / 原因</b>， 绝不放 {@code
 * documentKey} / {@code fileName} 等高基数或敏感字段（AGENTS.md 红线 #4）。指标经 actuator 的 {@code
 * /actuator/metrics} 暴露，Grafana 面板见 {@code docs/grafana/extraction-dashboard.json}。
 *
 * <p>视觉 token 计数暂未接入（{@link com.mouhin.knowledge.repository.domain.gateway.VisionChatGateway}
 * 现仅返回纯文本、不含 usage）， 故以 {@code vision.invocations{model}} 调用量作成本代理，待协议补齐 usage 后再引入 {@code tokens}
 * 维度。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Component
public class ExtractionMetrics {

    private static final String M_INVOCATIONS = "knowledge.extraction.invocations";
    private static final String M_LATENCY = "knowledge.extraction.latency";
    private static final String M_CACHE_HIT = "knowledge.extraction.cache.hit";
    private static final String M_CACHE_MISS = "knowledge.extraction.cache.miss";
    private static final String M_VISION_INVOCATIONS = "knowledge.extraction.vision.invocations";
    private static final String M_VISION_FAILURES = "knowledge.extraction.vision.failures";

    private static final String TAG_STRATEGY = "strategy";
    private static final String TAG_MIME = "mime";
    private static final String TAG_OUTCOME = "outcome";
    private static final String TAG_MODEL = "model";
    private static final String TAG_REASON = "reason";
    private static final String NONE = "none";
    private static final String UNKNOWN = "unknown";

    private final MeterRegistry registry;

    public ExtractionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** 记录一次策略调用：strategy × mime × outcome（ok / fallback / error / empty）。 */
    public void recordInvocation(String strategy, String mime, String outcome) {
        registry.counter(
                        M_INVOCATIONS,
                        TAG_STRATEGY,
                        nullToNone(strategy),
                        TAG_MIME,
                        nullToNone(mime),
                        TAG_OUTCOME,
                        nullToNone(outcome))
                .increment();
    }

    /** 记录一次成功解析耗时（按策略维度）。 */
    public void recordLatency(String strategy, Duration duration) {
        registry.timer(M_LATENCY, TAG_STRATEGY, nullToNone(strategy)).record(duration);
    }

    /** 解析缓存命中。 */
    public void recordCacheHit(String strategy) {
        registry.counter(M_CACHE_HIT, TAG_STRATEGY, nullToNone(strategy)).increment();
    }

    /** 解析缓存未命中。 */
    public void recordCacheMiss(String strategy) {
        registry.counter(M_CACHE_MISS, TAG_STRATEGY, nullToNone(strategy)).increment();
    }

    /** 视觉模型一次调用（成本代理，按 model 维度）。 */
    public void recordVisionInvocation(String model) {
        registry.counter(M_VISION_INVOCATIONS, TAG_MODEL, nullToUnknown(model)).increment();
    }

    /** 视觉模型一次失败（按 reason 维度：timeout / 429 / 5xx / schema / error）。 */
    public void recordVisionFailure(String reason) {
        registry.counter(M_VISION_FAILURES, TAG_REASON, nullToNone(reason)).increment();
    }

    private String nullToNone(String value) {
        return value == null || value.isBlank() ? NONE : value;
    }

    private String nullToUnknown(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }
}
