package com.mouhin.knowledge.repository.domain.service;

import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.model.valueobject.QualityGateResult;

/**
 * 黑板质量门禁领域服务接口
 *
 * <p>每个阶段完成后，编排层调用对应的 QualityGate 校验产出质量。 门禁不通过时返回建议动作（重试/跳过/终止），由编排层决策。
 *
 * <p>典型门禁：
 *
 * <ul>
 *   <li>Research 阶段：检索结果数 ≥ N，最低相关度 ≥ threshold
 *   <li>Writing 阶段：输出非空、长度 ≥ 最小值、无敏感内容
 *   <li>Review 阶段：质量评分 ≥ 阈值
 * </ul>
 *
 * @author mouhinU
 * @date 2026-09-28
 */
public interface QualityGate {

    /**
     * 执行质量门禁检查
     *
     * @param phase 当前阶段
     * @param blackboard 当前黑板状态
     * @return 门禁结果（通过/不通过 + 建议动作）
     */
    QualityGateResult evaluate(BlackboardPhase phase, BlackboardState blackboard);

    /**
     * 门禁名称（用于日志和追踪）
     *
     * @return 门禁名称
     */
    String getName();
}
