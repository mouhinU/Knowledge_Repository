package com.mouhin.knowledge.repository.domain.model.valueobject;

/**
 * 考试链路告警类型（2-F 可观测性）。
 *
 * <p>覆盖出卷与评分链路中"机器无法自助兜底、需要人工 / 运维关注"的关键异常信号， 用于统一指标埋点与结构化告警日志的维度标签。
 *
 * @author Knowledge-Repository
 * @date 2026-09-18
 */
public enum ExamAlertType {

    /** 出卷契约校验不通过 / 出卷即切分异常，试卷被置为 VALIDATION_FAILED */
    VALIDATION_FAILED("validation_failed", "出卷契约校验未通过"),

    /** 出卷质量分低于自动发布门禁阈值 */
    LOW_QUALITY("low_quality", "出卷质量分低于门禁阈值"),

    /** 评分时发现题目缺少标准答案，判零并转人工 */
    ANSWER_KEY_MISSING("answer_key_missing", "标准答案缺失"),

    /** 评分场次超时停留在 GRADING，被定时任务回收回退 */
    GRADING_TIMEOUT("grading_timeout", "评分超时回收");

    /** 指标标签值（稳定、小写蛇形，供 metrics tag 与日志字段使用） */
    private final String tag;

    /** 人类可读描述 */
    private final String description;

    ExamAlertType(String tag, String description) {
        this.tag = tag;
        this.description = description;
    }

    public String getTag() {
        return tag;
    }

    public String getDescription() {
        return description;
    }
}
