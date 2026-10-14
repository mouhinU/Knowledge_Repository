package com.mouhin.knowledge.repository.domain.constant;

import java.util.Set;

/**
 * 题型标识常量。
 *
 * <p>出卷、评分、审题、拆题、作答等链路统一在此维护题型 code，禁止在业务代码中硬编码字符串字面量（AGENTS.md 红线 #2）。 code 与数据库 {@code
 * exam_question.question_type}、LLM 结构化输出保持一致。
 *
 * <p>客观题（{@link #OBJECTIVE_CODES}）判据在 {@code ExamQuestion} / {@code ExamAnswer} 等处曾各自内联，现统一由 {@link
 * #isObjective(String)} 提供。
 */
public final class QuestionTypes {

    private QuestionTypes() {}

    /** 单选题 */
    public static final String SINGLE_CHOICE = "SINGLE_CHOICE";

    /** 多选题 */
    public static final String MULTI_CHOICE = "MULTI_CHOICE";

    /** 判断题 */
    public static final String TRUE_FALSE = "TRUE_FALSE";

    /** 填空题 */
    public static final String FILL_BLANK = "FILL_BLANK";

    /** 简答题 */
    public static final String SHORT_ANSWER = "SHORT_ANSWER";

    /** 论述题 */
    public static final String ESSAY = "ESSAY";

    /** 客观题集合（机器判分，无需人工复核）。 */
    public static final Set<String> OBJECTIVE_CODES =
            Set.of(SINGLE_CHOICE, MULTI_CHOICE, TRUE_FALSE);

    /**
     * 判断题型是否为客观题。
     *
     * @param questionType 题型 code
     * @return 客观题返回 {@code true}；{@code null} 或未知题型返回 {@code false}
     */
    public static boolean isObjective(String questionType) {
        return questionType != null && OBJECTIVE_CODES.contains(questionType);
    }
}
