package com.mouhin.knowledge.repository.client.api;

import com.alibaba.cola.dto.MultiResponse;
import com.alibaba.cola.dto.Response;
import com.alibaba.cola.dto.SingleResponse;
import com.mouhin.knowledge.repository.client.dto.ExamAnswerDTO;
import com.mouhin.knowledge.repository.client.dto.ExamSessionDTO;

/**
 * 考试评分应用服务契约（client 层）
 *
 * <p>仅承载传输无关的读 / 写用例：待评分与待复核列表、场次与答题详情查询、 同步触发评分、人工复核与成绩发布。返回值统一采用 COLA 契约类型 （{@link
 * SingleResponse} / {@link MultiResponse} / {@link Response}），适配层负责还原为前端 JSON 形状； 校验失败仍以 {@link
 * IllegalArgumentException} / {@link IllegalStateException} 抛出。
 *
 * <p>依赖领域回调 {@code ExamGradingProgressCallback} 的异步 / SSE 评分路径 （{@code gradeExamAsync} / {@code
 * triggerGradingAsync}）刻意不纳入本契约， 由 app 层执行器承载并供适配层直接调用，以保证 client 层不引用 domain 类型。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
public interface ExamGradingServiceI {

    MultiResponse<ExamSessionDTO> listPendingGradingSessions(int limit, int offset);

    SingleResponse<Long> countPendingGrading();

    MultiResponse<Long> listPendingGradingSessionIds();

    MultiResponse<ExamSessionDTO> listPendingReview(int limit, int offset);

    SingleResponse<Long> countPendingReview();

    SingleResponse<ExamSessionDTO> getSessionById(Long sessionId);

    MultiResponse<ExamAnswerDTO> listAnswersWithGrading(Long sessionId);

    Response triggerGrading(Long sessionId);

    Response reviewAnswer(
            Long answerId, Integer reviewScore, String reviewFeedback, String reviewer);

    Response publishScore(Long sessionId, String reviewer);
}
