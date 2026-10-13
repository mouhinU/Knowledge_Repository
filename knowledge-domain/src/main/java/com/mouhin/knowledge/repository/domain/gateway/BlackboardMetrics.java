package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import java.time.Duration;

/**
 * 黑板可观测指标网关接口
 *
 * <p>暴露 Prometheus/Grafana 可消费的指标，用于监控 Agent 流水线的健康度和性能。
 *
 * <p>典型指标：
 *
 * <ul>
 *   <li>blackboard_agent_duration_seconds{agent,phase} — Agent 执行耗时
 *   <li>blackboard_agent_total{agent,phase,status} — Agent 执行次数（按状态分）
 *   <li>blackboard_quality_gate_total{gate,phase,result} — 门禁执行次数（按结果分）
 *   <li>blackboard_pipeline_duration_seconds — 流水线总耗时
 *   <li>blackboard_token_total{agent} — Agent token 消耗
 * </ul>
 *
 * @author mouhinU
 * @date 2026-09-28
 */
public interface BlackboardMetrics {

    /**
     * 记录 Agent 执行耗时
     *
     * @param agentName Agent 名称
     * @param phase 阶段
     * @param duration 耗时
     */
    void recordAgentDuration(String agentName, BlackboardPhase phase, Duration duration);

    /**
     * 记录 Agent 执行次数（按状态）
     *
     * @param agentName Agent 名称
     * @param phase 阶段
     * @param status 状态（success / failed / skipped）
     */
    void incrementAgentCount(String agentName, BlackboardPhase phase, String status);

    /**
     * 记录质量门禁执行次数
     *
     * @param gateName 门禁名称
     * @param phase 阶段
     * @param passed 是否通过
     */
    void incrementQualityGateCount(String gateName, BlackboardPhase phase, boolean passed);

    /**
     * 记录流水线总耗时
     *
     * @param duration 总耗时
     */
    void recordPipelineDuration(Duration duration);

    /**
     * 记录 Agent token 消耗
     *
     * @param agentName Agent 名称
     * @param tokenCount token 数量
     */
    void recordAgentTokenCount(String agentName, int tokenCount);

    /**
     * 记录流水线最终质量评分
     *
     * @param qualityScore 质量评分（0~100）
     */
    void recordFinalQualityScore(double qualityScore);
}
