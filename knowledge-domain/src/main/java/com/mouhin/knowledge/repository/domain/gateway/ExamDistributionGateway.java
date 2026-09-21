package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;

/**
 * 题型分布方案生成网关（领域层端口，由基础设施层多阶段 Agent 实现）
 *
 * <p>屏蔽 ChatModel / Prompt / 分阶段编排等技术细节，应用层仅依赖本接口获取已归一化 （Σ=满分）的 {@link ExamPlan}。
 *
 * @author Knowledge-Repository
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
}
