package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExamAlertType;

/**
 * 考试链路告警网关（2-F 可观测性）。
 *
 * <p>领域层仅声明"发生了什么需要告警"的语义，具体的指标埋点与日志 / 上报实现落在基础设施层。 出入参均为领域概念或基础类型，不出现 DO / DTO。
 *
 * @author mouhinU
 * @date 2026-09-18
 */
public interface ExamAlertGateway {

    /**
     * 上报出卷契约校验失败（试卷被置为 VALIDATION_FAILED）。
     *
     * @param paperSessionKey 试卷标识（出卷历史 sessionId）
     * @param issueCount 校验问题条数
     */
    void validationFailed(String paperSessionKey, int issueCount);

    /**
     * 上报出卷质量分低于自动发布门禁阈值。
     *
     * @param paperSessionKey 试卷标识
     * @param quality 实际质量分
     * @param threshold 门禁阈值
     */
    void lowQualityScore(String paperSessionKey, int quality, int threshold);

    /**
     * 上报评分时发现题目缺少标准答案。
     *
     * @param sessionId 考试场次 ID
     * @param questionNumber 题号（可能为空）
     */
    void answerKeyMissing(Long sessionId, Integer questionNumber);

    /**
     * 上报评分场次超时被回收回退。
     *
     * @param sessionId 考试场次 ID
     */
    void gradingTimeout(Long sessionId);

    /**
     * 通用告警埋点（供上述语义方法复用，也便于扩展新信号）。
     *
     * @param type 告警类型
     * @param objectId 关联对象标识（试卷键 / 场次 ID 等）
     * @param detail 补充明细，可为 {@code null}
     */
    void alert(ExamAlertType type, String objectId, String detail);
}
