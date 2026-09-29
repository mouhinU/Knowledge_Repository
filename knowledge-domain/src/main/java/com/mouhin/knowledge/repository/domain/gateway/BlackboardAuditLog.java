package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.valueobject.AgentConfidence;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import java.time.Duration;

/**
 * 黑板审计日志网关接口
 *
 * <p>记录每个 Agent 的执行元数据（耗时、token 消耗、置信度、输入输出摘要）， 用于调试、成本分析和可观测性。
 *
 * <p>实现方可以是 SLF4J 日志、数据库持久化、或两者结合。
 *
 * @author mouhinU
 * @date 2026-09-28
 */
public interface BlackboardAuditLog {

    /**
     * 记录 Agent 开始执行
     *
     * @param sessionId 会话 ID
     * @param agentName Agent 名称
     * @param phase 当前阶段
     * @param inputSummary 输入摘要（如 "检索到 5 条知识片段"）
     */
    void logAgentStart(
            String sessionId, String agentName, BlackboardPhase phase, String inputSummary);

    /**
     * 记录 Agent 执行完成
     *
     * @param sessionId 会话 ID
     * @param agentName Agent 名称
     * @param phase 当前阶段
     * @param duration 执行耗时
     * @param tokenCount token 消耗（可空，非流式场景可能未知）
     * @param confidence 置信度（可空）
     * @param outputSummary 输出摘要（如 "生成 350 字文章草稿"）
     */
    void logAgentComplete(
            String sessionId,
            String agentName,
            BlackboardPhase phase,
            Duration duration,
            Integer tokenCount,
            AgentConfidence confidence,
            String outputSummary);

    /**
     * 记录 Agent 执行失败
     *
     * @param sessionId 会话 ID
     * @param agentName Agent 名称
     * @param phase 当前阶段
     * @param duration 执行耗时（到失败点）
     * @param error 错误信息
     */
    void logAgentFailed(
            String sessionId,
            String agentName,
            BlackboardPhase phase,
            Duration duration,
            String error);

    /**
     * 记录质量门禁结果
     *
     * @param sessionId 会话 ID
     * @param gateName 门禁名称
     * @param phase 当前阶段
     * @param passed 是否通过
     * @param reason 判定原因
     */
    void logQualityGate(
            String sessionId,
            String gateName,
            BlackboardPhase phase,
            boolean passed,
            String reason);

    /**
     * 记录流水线整体完成
     *
     * @param sessionId 会话 ID
     * @param totalDuration 总耗时
     * @param totalTokenCount 总 token 消耗
     * @param finalQualityScore 最终质量评分
     * @param agentCount 参与的 Agent 数量
     */
    void logPipelineComplete(
            String sessionId,
            Duration totalDuration,
            Integer totalTokenCount,
            double finalQualityScore,
            int agentCount);
}
