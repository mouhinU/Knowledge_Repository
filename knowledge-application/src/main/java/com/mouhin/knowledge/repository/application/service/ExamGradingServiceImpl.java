package com.mouhin.knowledge.repository.application.service;

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

import java.util.List;

/**
 * 考试评分应用服务实现（app 层，仅分发到执行器）
 *
 * <p>回调 / SSE 耦合的异步评分路径不经此契约，由适配层直接调用
 * {@code GradeExamAsyncCmdExe} / {@code TriggerGradingAsyncCmdExe}。</p>
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

    public ExamGradingServiceImpl(ListPendingGradingSessionsQryExe listPendingGradingSessionsQryExe,
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
    public List<ExamSessionDTO> listPendingGradingSessions(int limit, int offset) {
        return listPendingGradingSessionsQryExe.execute(limit, offset);
    }

    @Override
    public long countPendingGrading() {
        return countPendingGradingQryExe.execute();
    }

    @Override
    public List<Long> listPendingGradingSessionIds() {
        return listPendingGradingSessionIdsQryExe.execute();
    }

    @Override
    public List<ExamSessionDTO> listPendingReview(int limit, int offset) {
        return listPendingReviewQryExe.execute(limit, offset);
    }

    @Override
    public long countPendingReview() {
        return countPendingReviewQryExe.execute();
    }

    @Override
    public ExamSessionDTO getSessionById(Long sessionId) {
        return getSessionByIdQryExe.execute(sessionId);
    }

    @Override
    public List<ExamAnswerDTO> listAnswersWithGrading(Long sessionId) {
        return listAnswersWithGradingQryExe.execute(sessionId);
    }

    @Override
    public void triggerGrading(Long sessionId) {
        triggerGradingCmdExe.execute(sessionId);
    }

    @Override
    public void reviewAnswer(Long answerId, Integer reviewScore, String reviewFeedback, String reviewer) {
        reviewAnswerCmdExe.execute(answerId, reviewScore, reviewFeedback, reviewer);
    }

    @Override
    public void publishScore(Long sessionId, String reviewer) {
        publishScoreCmdExe.execute(sessionId, reviewer);
    }
}
