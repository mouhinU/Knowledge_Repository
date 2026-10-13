package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import java.util.List;

/**
 * 题型分布方案生成网关（领域层端口，由基础设施层多阶段 Agent 实现）
 *
 * <p>屏蔽 ChatModel / Prompt / 分阶段编排等技术细节，应用层仅依赖本接口获取已归一化 （Σ=满分）的 {@link ExamPlan}。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
public interface ExamDistributionGateway {

    /**
     * 生成题型分布方案
     *
     * @param topic 考试主题 / 科目
     * @param difficulty 难度（EASY / MEDIUM / HARD）
     * @param schoolLevelCode 学段编码（可空自动识别）
     * @param knowledgeHint 知识点摘要（可空）
     * @return 已归一化的分布方案
     */
    ExamPlan generate(
            String topic, String difficulty, String schoolLevelCode, String knowledgeHint);

    /**
     * 生成题型分布方案（可推送逐阶段过程事件）
     *
     * @param callback 进度回调（可空；非空时逐阶段推送输入 / 思考 / 输出）
     */
    ExamPlan generate(
            String topic,
            String difficulty,
            String schoolLevelCode,
            String knowledgeHint,
            BlackboardProgressCallback callback);

    /**
     * 基于校验反馈修正方案（LLM 调用）
     *
     * <p>将当前方案连同硬校验问题与建议一并交给 LLM，由其调整题型 / 题量 / 分值以消除问题。 返回的方案已经过 {@code normalizePlan} 归一化（Σ=满分）。
     *
     * @param topic 考试主题
     * @param plan 当前方案
     * @param issues 硬校验问题（来自 ScorePlanValidator）
     * @param suggestions 软建议（来自 ScorePlanValidator + evaluationNotes）
     * @param callback 进度回调（可空）
     * @return 修正后的方案（已归一化），调用失败时返回 null
     */
    ExamPlan adjustPlan(
            String topic,
            ExamPlan plan,
            List<String> issues,
            List<String> suggestions,
            BlackboardProgressCallback callback);

    /**
     * 按轮次生成题型分布方案（四阶段完整重跑）。
     *
     * <p>用于"评估不合理 → 回退重新生成"的循环场景。每轮使用独立的 SSE agent 名称前缀（{@code r{round}-classify} 等），
     * 使前端能为每轮创建独立的进度卡片，保留历史评估结果。
     *
     * @param topic 考试主题 / 科目
     * @param difficulty 难度（EASY / MEDIUM / HARD）
     * @param schoolLevelCode 学段编码（可空自动识别）
     * @param knowledgeHint 知识点摘要（可空）
     * @param round 当前轮次（1-based），用于 SSE agent 名称前缀
     * @param prevEvaluationNotes 上一轮的合理性评估建议 + 硬校验问题（首轮传 null 或空列表）
     * @param prevPlan 上一轮的分布方案（首轮传 null），供评估阶段对比判断反馈是否已落实
     * @param callback 进度回调（可空）
     * @return 已归一化（Σ=满分）的分布方案
     */
    ExamPlan generateForRound(
            String topic,
            String difficulty,
            String schoolLevelCode,
            String knowledgeHint,
            int round,
            List<String> prevEvaluationNotes,
            ExamPlan prevPlan,
            BlackboardProgressCallback callback);
}
