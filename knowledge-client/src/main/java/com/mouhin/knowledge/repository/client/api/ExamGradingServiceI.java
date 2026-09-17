package com.mouhin.knowledge.repository.client.api;

import com.mouhin.knowledge.repository.client.dto.ExamAnswerDTO;
import com.mouhin.knowledge.repository.client.dto.ExamSessionDTO;

import java.util.List;

/**
 * 考试评分应用服务契约（client 层）
 *
 * <p>仅承载传输无关的读 / 写用例：待评分与待复核列表、场次与答题详情查询、
 * 同步触发评分、人工复核与成绩发布。返回 DTO，键集合与原领域实体序列化结果一致。</p>
 *
 * <p>依赖领域回调 {@code ExamGradingProgressCallback} 的异步 / SSE 评分路径
 * （{@code gradeExamAsync} / {@code triggerGradingAsync}）刻意不纳入本契约，
 * 由 app 层执行器承载并供适配层直接调用，以保证 client 层不引用 domain 类型。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
public interface ExamGradingServiceI {

    List<ExamSessionDTO> listPendingGradingSessions(int limit, int offset);

    long countPendingGrading();

    List<Long> listPendingGradingSessionIds();

    List<ExamSessionDTO> listPendingReview(int limit, int offset);

    long countPendingReview();

    ExamSessionDTO getSessionById(Long sessionId);

    List<ExamAnswerDTO> listAnswersWithGrading(Long sessionId);

    void triggerGrading(Long sessionId);

    void reviewAnswer(Long answerId, Integer reviewScore, String reviewFeedback, String reviewer);

    void publishScore(Long sessionId, String reviewer);
}
