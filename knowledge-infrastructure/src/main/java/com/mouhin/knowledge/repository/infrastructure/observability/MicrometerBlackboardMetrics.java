package com.mouhin.knowledge.repository.infrastructure.observability;

import com.mouhin.knowledge.repository.domain.gateway.BlackboardMetrics;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * 黑板 Agent 流水线可观测埋点（Phase R2）。
 *
 * <p>以 Micrometer 登记 Agent 执行次数/耗时、质量门禁结果、流水线总耗时、token 消耗与最终质量评分。 <b>标签仅含 agent 名 / phase / status
 * / gate 名</b>，绝不放 sessionId（高基数）。 指标经 actuator 的 {@code /actuator/metrics} 暴露，Grafana 面板可基于此构建。
 *
 * <p>指标清单：
 *
 * <ul>
 *   <li>{@code knowledge.blackboard.agent.duration} — Agent 执行耗时（Timer，标签 agent/phase）
 *   <li>{@code knowledge.blackboard.agent.total} — Agent 执行次数（Counter，标签 agent/phase/status）
 *   <li>{@code knowledge.blackboard.quality_gate.total} — 门禁执行次数（Counter，标签 gate/phase/result）
 *   <li>{@code knowledge.blackboard.pipeline.duration} — 流水线总耗时（Timer）
 *   <li>{@code knowledge.blackboard.agent.tokens} — Agent token 消耗（Counter，标签 agent）
 *   <li>{@code knowledge.blackboard.pipeline.quality_score} — 最终质量评分（Gauge 最近一次值）
 * </ul>
 *
 * @author mouhinU
 * @date 2026-09-28
 */
@Component
public class MicrometerBlackboardMetrics implements BlackboardMetrics {

    private static final String M_AGENT_DURATION = "knowledge.blackboard.agent.duration";
    private static final String M_AGENT_TOTAL = "knowledge.blackboard.agent.total";
    private static final String M_QUALITY_GATE_TOTAL = "knowledge.blackboard.quality_gate.total";
    private static final String M_PIPELINE_DURATION = "knowledge.blackboard.pipeline.duration";
    private static final String M_AGENT_TOKENS = "knowledge.blackboard.agent.tokens";
    private static final String M_PIPELINE_QUALITY_SCORE =
            "knowledge.blackboard.pipeline.quality_score";

    private static final String TAG_AGENT = "agent";
    private static final String TAG_PHASE = "phase";
    private static final String TAG_STATUS = "status";
    private static final String TAG_GATE = "gate";
    private static final String TAG_RESULT = "result";
    private static final String NONE = "none";

    private final MeterRegistry registry;

    /** 最近一次流水线质量评分（供 Gauge 读取） */
    private volatile double lastQualityScore = 0.0;

    public MicrometerBlackboardMetrics(MeterRegistry registry) {
        this.registry = registry;
        // 注册 Gauge（只读最近一次值）
        registry.gauge(
                M_PIPELINE_QUALITY_SCORE, this, MicrometerBlackboardMetrics::getLastQualityScore);
    }

    @Override
    public void recordAgentDuration(String agentName, BlackboardPhase phase, Duration duration) {
        registry.timer(M_AGENT_DURATION, TAG_AGENT, nullToNone(agentName), TAG_PHASE, phase.name())
                .record(duration);
    }

    @Override
    public void incrementAgentCount(String agentName, BlackboardPhase phase, String status) {
        registry.counter(
                        M_AGENT_TOTAL,
                        TAG_AGENT,
                        nullToNone(agentName),
                        TAG_PHASE,
                        phase.name(),
                        TAG_STATUS,
                        nullToNone(status))
                .increment();
    }

    @Override
    public void incrementQualityGateCount(String gateName, BlackboardPhase phase, boolean passed) {
        registry.counter(
                        M_QUALITY_GATE_TOTAL,
                        TAG_GATE,
                        nullToNone(gateName),
                        TAG_PHASE,
                        phase.name(),
                        TAG_RESULT,
                        passed ? "passed" : "failed")
                .increment();
    }

    @Override
    public void recordPipelineDuration(Duration duration) {
        registry.timer(M_PIPELINE_DURATION).record(duration);
    }

    @Override
    public void recordAgentTokenCount(String agentName, int tokenCount) {
        registry.counter(M_AGENT_TOKENS, TAG_AGENT, nullToNone(agentName)).increment(tokenCount);
    }

    @Override
    public void recordFinalQualityScore(double qualityScore) {
        this.lastQualityScore = qualityScore;
    }

    private double getLastQualityScore() {
        return lastQualityScore;
    }

    private String nullToNone(String value) {
        return value == null || value.isBlank() ? NONE : value;
    }
}
