package com.mouhin.knowledge.repository.domain.constant;

/**
 * 题型中文别名常量。
 *
 * <p>{@code ScoreRuleEngine} 内部既从 {@link QuestionTypes} 英文码解析题型，也接受用户/LLM 输出 的中文别名（{@code "单选题"}
 * 等），且展示映射方向也回吐这些字面量。同一串在两方向共享，依 AGENTS.md 红线 #2 集中维护。
 */
public final class QuestionTypeAliases {

    private QuestionTypeAliases() {}

    /** 单选题。 */
    public static final String SINGLE_CHOICE_ZH = "单选题";

    /** 多选题。 */
    public static final String MULTI_CHOICE_ZH = "多选题";

    /** 判断题。 */
    public static final String TRUE_FALSE_ZH = "判断题";

    /** 填空题。 */
    public static final String FILL_BLANK_ZH = "填空题";

    /** 简答题。 */
    public static final String SHORT_ANSWER_ZH = "简答题";

    /** 论述题。 */
    public static final String ESSAY_ZH = "论述题";
}
