package com.mouhin.knowledge.repository.domain.model.valueobject;

/**
 * Agent 输出置信度
 *
 * <p>每个 Agent 执行完毕后附带置信度评分（0~1）与原因说明， 供下游 QualityGate 决策是否放行或触发重试。
 *
 * @author mouhinU
 * @date 2026-09-28
 */
public final class AgentConfidence {

    /** 置信度分数（0.0 ~ 1.0） */
    private final double score;

    /** 置信度原因（人类可读，如 "检索到 5 条高相关片段" / "LLM 返回空内容"） */
    private final String reason;

    /** 是否达到最低置信度阈值（默认 0.3） */
    private final boolean acceptable;

    public AgentConfidence(double score, String reason) {
        this(score, reason, 0.3);
    }

    public AgentConfidence(double score, String reason, double threshold) {
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException(
                    "Confidence score must be between 0.0 and 1.0, got: " + score);
        }
        this.score = score;
        this.reason = reason != null ? reason : "";
        this.acceptable = score >= threshold;
    }

    /** 高置信度快捷构造（score >= 0.7） */
    public static AgentConfidence high(String reason) {
        return new AgentConfidence(0.9, reason);
    }

    /** 中等置信度快捷构造（0.4 <= score < 0.7） */
    public static AgentConfidence medium(String reason) {
        return new AgentConfidence(0.5, reason);
    }

    /** 低置信度快捷构造（score < 0.4） */
    public static AgentConfidence low(String reason) {
        return new AgentConfidence(0.2, reason);
    }

    /** 未知/未评估 */
    public static AgentConfidence unknown() {
        return new AgentConfidence(0.0, "未评估");
    }

    public double getScore() {
        return score;
    }

    public String getReason() {
        return reason;
    }

    public boolean isAcceptable() {
        return acceptable;
    }

    @Override
    public String toString() {
        return String.format(
                "AgentConfidence{score=%.2f, acceptable=%s, reason='%s'}",
                score, acceptable, reason);
    }
}
