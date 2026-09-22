package com.mouhin.knowledge.repository.domain.model.valueobject;

/**
 * 黑板工作阶段枚举
 *
 * @author mouhinU
 * @date 2026-09-12
 */
public enum BlackboardPhase {

    /** 初始化 */
    INIT,

    /** 研究阶段：检索知识库 */
    RESEARCH,

    /** 分值分配阶段：根据题型和难度计算各题型分值 */
    SCORING,

    /** 写作阶段：生成文章/试卷 */
    WRITING,

    /** 答案生成阶段：生成标准答案与评分标准 */
    ANSWER_GENERATING,

    /** 审核阶段：质量检查 */
    REVIEWING,

    /** 难度校准阶段：调整难度分布 */
    CALIBRATING,

    /** 查重去重阶段：与历史试卷对比 */
    DEDUPLICATING,

    /** 已完成 */
    COMPLETED,

    /** 失败 */
    FAILED
}
