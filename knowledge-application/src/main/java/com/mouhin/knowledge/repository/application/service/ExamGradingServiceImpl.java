package com.mouhin.knowledge.repository.application.service;

import com.alibaba.cola.dto.MultiResponse;
import com.alibaba.cola.dto.Response;
import com.alibaba.cola.dto.SingleResponse;
import com.mouhin.knowledge.repository.application.executor.examgrading.CountPendingGradingQryExe;
import com.mouhin.knowledge.repository.application.executor.examgrading.CountPendingReviewQryExe;
import com.mouhin.knowledge.repository.application.executor.examgrading.GetSessionByIdQryExe;
import com.mouhin.knowledge.repository.application.executor.examgrading.ListAnswersWithGradingQryExe;
import com.mouhin.knowledge.repository.application.executor.examgrading.ListPendingGradingSessionIdsQryExe;
import com.mouhin.knowledge.repository.application.executor.examgrading.ListPendingGradingSessionsQryExe;
import com.mouhin.knowledge.repository.application.executor.examgrading.ListPendingReviewQryExe;
import com.mouhin.knowledge.repository.application.executor.examgrading.PublishScoreCmdExe;
import com.mouhin.knowledge.repository.application.executor.examgrading.ReviewAnswerCmdExe;
import com.mouhin.knowledge.repository.application.executor.examgrading.TriggerGradingCmdExe;
import com.mouhin.knowledge.repository.client.api.ExamGradingServiceI;
import com.mouhin.knowledge.repository.client.dto.ExamAnswerDTO;
import com.mouhin.knowledge.repository.client.dto.ExamSessionDTO;
import org.springframework.stereotype.Service;

/**
 * 考试评分应用服务实现（app 层，仅分发到执行器）
 *
 * <p>回调 / SSE 耦合的异步评分路径不经此契约，由适配层直接调用 {@code GradeExamAsyncCmdExe} / {@code
 * TriggerGradingAsyncCmdExe}。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Service
public class ExamGradingServiceImpl implements ExamGradingServiceI {

    private final ListPendingGradingSessionsQryExe listPendingGradingSessionsQryExe;
    private final CountPendingGradingQryExe countPendingGradingQryExe;
    private final ListPendingGradingSessionIdsQryExe listPendingGradingSessionIdsQryExe;
    private final ListPendingReviewQryExe listPendingReviewQryExe;
    private final CountPendingReviewQryExe countPendingReviewQryExe;
    private final GetSessionByIdQryExe getSessionByIdQryExe;
    private final ListAnswersWithGradingQryExe listAnswersWithGradingQryExe;
    private final TriggerGradingCmdExe triggerGradingCmdExe;
    private final ReviewAnswerCmdExe reviewAnswerCmdExe;
    private final PublishScoreCmdExe publishScoreCmdExe;

    public ExamGradingServiceImpl(
            ListPendingGradingSessionsQryExe listPendingGradingSessionsQryExe,
            CountPendingGradingQryExe countPendingGradingQryExe,
            ListPendingGradingSessionIdsQryExe listPendingGradingSessionIdsQryExe,
            ListPendingReviewQryExe listPendingReviewQryExe,
            CountPendingReviewQryExe countPendingReviewQryExe,
            GetSessionByIdQryExe getSessionByIdQryExe,
            ListAnswersWithGradingQryExe listAnswersWithGradingQryExe,
            TriggerGradingCmdExe triggerGradingCmdExe,
            ReviewAnswerCmdExe reviewAnswerCmdExe,
            PublishScoreCmdExe publishScoreCmdExe) {
        this.listPendingGradingSessionsQryExe = listPendingGradingSessionsQryExe;
        this.countPendingGradingQryExe = countPendingGradingQryExe;
        this.listPendingGradingSessionIdsQryExe = listPendingGradingSessionIdsQryExe;
        this.listPendingReviewQryExe = listPendingReviewQryExe;
        this.countPendingReviewQryExe = countPendingReviewQryExe;
        this.getSessionByIdQryExe = getSessionByIdQryExe;
        this.listAnswersWithGradingQryExe = listAnswersWithGradingQryExe;
        this.triggerGradingCmdExe = triggerGradingCmdExe;
        this.reviewAnswerCmdExe = reviewAnswerCmdExe;
        this.publishScoreCmdExe = publishScoreCmdExe;
    }

    @Override
    public MultiResponse<ExamSessionDTO> listPendingGradingSessions(int limit, int offset) {
        return MultiResponse.of(listPendingGradingSessionsQryExe.execute(limit, offset));
    }

    @Override
    public SingleResponse<Long> countPendingGrading() {
        return SingleResponse.of(countPendingGradingQryExe.execute());
    }

    @Override
    public MultiResponse<Long> listPendingGradingSessionIds() {
        return MultiResponse.of(listPendingGradingSessionIdsQryExe.execute());
    }

    @Override
    public MultiResponse<ExamSessionDTO> listPendingReview(int limit, int offset) {
        return MultiResponse.of(listPendingReviewQryExe.execute(limit, offset));
    }

    @Override
    public SingleResponse<Long> countPendingReview() {
        return SingleResponse.of(countPendingReviewQryExe.execute());
    }

    @Override
    public SingleResponse<ExamSessionDTO> getSessionById(Long sessionId) {
        return SingleResponse.of(getSessionByIdQryExe.execute(sessionId));
    }

    @Override
    public MultiResponse<ExamAnswerDTO> listAnswersWithGrading(Long sessionId) {
        return MultiResponse.of(listAnswersWithGradingQryExe.execute(sessionId));
    }

    @Override
    public Response triggerGrading(Long sessionId) {
        triggerGradingCmdExe.execute(sessionId);
        return Response.buildSuccess();
    }

    @Override
    public Response reviewAnswer(
            Long answerId, Integer reviewScore, String reviewFeedback, String reviewer) {
        reviewAnswerCmdExe.execute(answerId, reviewScore, reviewFeedback, reviewer);
        return Response.buildSuccess();
    }

    @Override
    public Response publishScore(Long sessionId, String reviewer) {
        publishScoreCmdExe.execute(sessionId, reviewer);
        return Response.buildSuccess();
    }
}
