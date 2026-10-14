package com.mouhin.knowledge.repository.domain.constant;

/**
 * 考试会话状态常量。
 *
 * <p>{@code ExamSession.status} 的取值词表，统一在此维护，禁止硬编码字符串字面量（AGENTS.md 红线 #2）。 此前 {@code ExamSession}
 * 内联赋值、{@code ExamSessionGatewayImpl} 与多个 {@code CmdExe}/{@code QryExe} 各自重复声明 {@code STATUS_*}
 * 局部常量，现收敛为单一来源。
 *
 * <p>状态流转：{@link #IN_PROGRESS} → {@link #SUBMITTED} → {@link #GRADING} → {@link #AI_GRADED} →
 * {@link #REVIEWED} → {@link #PUBLISHED}。
 */
public final class ExamSessionStatuses {

    private ExamSessionStatuses() {}

    /** 作答中。 */
    public static final String IN_PROGRESS = "IN_PROGRESS";

    /** 已提交，待评分。 */
    public static final String SUBMITTED = "SUBMITTED";

    /** 评分中。 */
    public static final String GRADING = "GRADING";

    /** AI 已评分。 */
    public static final String AI_GRADED = "AI_GRADED";

    /** 已人工复核。 */
    public static final String REVIEWED = "REVIEWED";

    /** 已发布成绩。 */
    public static final String PUBLISHED = "PUBLISHED";
}
