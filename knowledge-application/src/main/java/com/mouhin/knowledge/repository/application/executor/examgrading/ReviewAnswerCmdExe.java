package com.mouhin.knowledge.repository.application.executor.examgrading;

import com.mouhin.knowledge.repository.domain.gateway.ExamAnswerGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 人工复核单题执行器
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class ReviewAnswerCmdExe {

    private static final Logger logger = LoggerFactory.getLogger(ReviewAnswerCmdExe.class);

    private final ExamAnswerGateway examAnswerGateway;
    private final ExamSessionGateway examSessionGateway;

    public ReviewAnswerCmdExe(
            ExamAnswerGateway examAnswerGateway, ExamSessionGateway examSessionGateway) {
        this.examAnswerGateway = examAnswerGateway;
        this.examSessionGateway = examSessionGateway;
    }

    @Transactional
    public void execute(
            Long answerId, Integer reviewScore, String reviewFeedback, String reviewer) {
        ExamAnswer answer =
                examAnswerGateway
                        .findById(answerId)
                        .orElseThrow(() -> new IllegalArgumentException("答题记录不存在: " + answerId));

        // 阶段 2-D：仅允许在已评分（AI_GRADED）或复核中（REVIEWED）的场次上改分，
        // 防止对进行中 / 未评分 / 已发布场次误改（IllegalState → 全局异常处理器返回 409）。
        ExamSession session =
                examSessionGateway
                        .findById(answer.getSessionId())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "考试场次不存在: " + answer.getSessionId()));
        String status = session.getStatus();
        if (!"AI_GRADED".equals(status) && !"REVIEWED".equals(status)) {
            throw new IllegalStateException("当前场次状态不允许人工复核：" + status);
        }

        // 阶段 2-D：复核分数边界校验（null 视为清除人工改分，回落 AI 分）
        int maxScore = answer.getMaxScore() == null ? 0 : answer.getMaxScore();
        if (reviewScore != null && (reviewScore < 0 || reviewScore > maxScore)) {
            throw new IllegalArgumentException("复核分数越界：应为 0~" + maxScore + "，实际 " + reviewScore);
        }

        if (reviewScore == null) {
            // 清除人工改分：回落 AI 分。updateById 会跳过 null 列，故走显式置 NULL 的专用通道。
            answer.setReviewScore(null);
            answer.setReviewFeedback(null);
            answer.setReviewedBy(null);
            answer.setReviewTime(null);
            examAnswerGateway.clearReviewOverride(answerId);
            logger.info("人工复核清除改分覆盖 [answerId={}, reviewer={}]", answerId, reviewer);
        } else {
            answer.setReviewScore(reviewScore);
            answer.setReviewFeedback(reviewFeedback);
            answer.setReviewedBy(reviewer);
            answer.setReviewTime(LocalDateTime.now());
            answer.setUpdateTime(LocalDateTime.now());
            examAnswerGateway.update(answer);
            logger.info(
                    "人工复核单题 [answerId={}, score={}, reviewer={}]", answerId, reviewScore, reviewer);
        }

        // DATA-2：单题复核（含清除改分）后立即按全场有效分之和回刷场次 final_score，
        // 并转入 REVIEWED 复核态。此前聚合仅在「发布成绩」时才计算，导致复核后成绩列表 / 详情
        // 长期停留在旧的 AI 合计、与实际人工改分不一致（改分未发布期间数据漂移）。
        recomputeSessionAggregate(session);
    }

    /**
     * 依据本场次全部答题记录的有效分（人工复核分优先，否则 AI 分）重算并回刷场次聚合。
     *
     * <p>复用调用方已加载且状态校验通过的 {@code session} 对象，避免多余查询； {@code updateById} 对非空列生效，{@code
     * final_score}/{@code status}/{@code update_time} 均被写入；{@code grading_token}
     * 在领域对象中不承载，故不会被本更新覆盖。
     *
     * @param session 已通过状态校验的场次聚合
     */
    private void recomputeSessionAggregate(ExamSession session) {
        List<ExamAnswer> answers = examAnswerGateway.listBySessionId(session.getId());
        int finalScore = answers.stream().mapToInt(ExamAnswer::getEffectiveScore).sum();
        session.markReviewed(finalScore);
        session.setUpdateTime(LocalDateTime.now());
        examSessionGateway.update(session);
        logger.info("复核回刷场次聚合 [session={}, finalScore={}]", session.getId(), finalScore);
    }
}
