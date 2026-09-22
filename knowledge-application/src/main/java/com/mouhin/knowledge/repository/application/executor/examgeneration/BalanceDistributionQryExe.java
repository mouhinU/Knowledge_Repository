package com.mouhin.knowledge.repository.application.executor.examgeneration;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.service.ScoreRuleEngine;
import org.springframework.stereotype.Component;

/**
 * 题型分布方案分值自动平衡执行器（出入参为领域 VO，返回 BalanceResult）
 *
 * <p>因出入参含领域类型，不纳入 client 契约，由适配层直接调用并映射为响应。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
public class BalanceDistributionQryExe {

    private final ExamGenerationSupport support;

    public BalanceDistributionQryExe(ExamGenerationSupport support) {
        this.support = support;
    }

    public ScoreRuleEngine.BalanceResult execute(ExamPlan plan) {
        return support.balanceDistribution(plan);
    }
}
