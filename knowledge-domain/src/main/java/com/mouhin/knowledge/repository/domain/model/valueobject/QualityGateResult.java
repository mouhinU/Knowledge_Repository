package com.mouhin.knowledge.repository.domain.model.valueobject;

/**
 * 质量门禁判定结果
 *
 * <p>QualityGate 执行后返回此对象，包含是否通过、原因、以及建议动作。 编排层根据建议动作决定继续/重试/跳过/终止。
 *
 * @author mouhinU
 * @date 2026-09-28
 */
public final class QualityGateResult {

    /** 是否通过 */
    private final boolean passed;

    /** 门禁名称（如 "research-min-chunks"） */
    private final String gateName;

    /** 判定原因 */
    private final String reason;

    /** 建议动作 */
    private final Action suggestedAction;

    /** 关联的置信度（可空） */
    private final AgentConfidence confidence;

    public enum Action {
        /** 继续下一阶段 */
        CONTINUE,
        /** 重试当前阶段（如检索结果不足，重新检索） */
        RETRY_CURRENT,
        /** 跳过当前阶段（如非关键门禁未通过） */
        SKIP,
        /** 终止流水线（如关键数据缺失） */
        TERMINATE
    }

    public QualityGateResult(
            boolean passed,
            String gateName,
            String reason,
            Action suggestedAction,
            AgentConfidence confidence) {
        this.passed = passed;
        this.gateName = gateName;
        this.reason = reason != null ? reason : "";
        this.suggestedAction = suggestedAction;
        this.confidence = confidence;
    }

    public static QualityGateResult pass(String gateName, String reason) {
        return new QualityGateResult(true, gateName, reason, Action.CONTINUE, null);
    }

    public static QualityGateResult pass(
            String gateName, String reason, AgentConfidence confidence) {
        return new QualityGateResult(true, gateName, reason, Action.CONTINUE, confidence);
    }

    public static QualityGateResult fail(String gateName, String reason, Action action) {
        return new QualityGateResult(false, gateName, reason, action, null);
    }

    public static QualityGateResult fail(
            String gateName, String reason, Action action, AgentConfidence confidence) {
        return new QualityGateResult(false, gateName, reason, action, confidence);
    }

    public boolean isPassed() {
        return passed;
    }

    public String getGateName() {
        return gateName;
    }

    public String getReason() {
        return reason;
    }

    public Action getSuggestedAction() {
        return suggestedAction;
    }

    public AgentConfidence getConfidence() {
        return confidence;
    }

    @Override
    public String toString() {
        return String.format(
                "QualityGateResult{gate='%s', passed=%s, action=%s, reason='%s'}",
                gateName, passed, suggestedAction, reason);
    }
}
