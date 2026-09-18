package com.mouhin.knowledge.repository.application.executor.examgrading;

import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.domain.gateway.ExamAnswerGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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

    public ReviewAnswerCmdExe(ExamAnswerGateway examAnswerGateway,
                              ExamSessionGateway examSessionGateway) {
        this.examAnswerGateway = examAnswerGateway;
        this.examSessionGateway = examSessionGateway;
    }

    @Transactional
    public void execute(Long answerId, Integer reviewScore, String reviewFeedback, String reviewer) {
        ExamAnswer answer = examAnswerGateway.findById(answerId)
                .orElseThrow(() -> new IllegalArgumentException("答题记录不存在: " + answerId));

        // 阶段 2-D：仅允许在已评分（AI_GRADED）或复核中（REVIEWED）的场次上改分，
        // 防止对进行中 / 未评分 / 已发布场次误改（IllegalState → 全局异常处理器返回 409）。
        ExamSession session = examSessionGateway.findById(answer.getSessionId())
                .orElseThrow(() -> new IllegalArgumentException("考试场次不存在: " + answer.getSessionId()));
        String status = session.getStatus();
        if (!"AI_GRADED".equals(status) && !"REVIEWED".equals(status)) {
            throw new IllegalStateException("当前场次状态不允许人工复核：" + status);
        }

        // 阶段 2-D：复核分数边界校验（null 视为清除人工改分，回落 AI 分）
        int maxScore = answer.getMaxScore() == null ? 0 : answer.getMaxScore();
        if (reviewScore != null && (reviewScore < 0 || reviewScore > maxScore)) {
            throw new IllegalArgumentException(
                    "复核分数越界：应为 0~" + maxScore + "，实际 " + reviewScore);
        }

        answer.setReviewScore(reviewScore);
        answer.setReviewFeedback(reviewFeedback);
        answer.setReviewedBy(reviewer);
        answer.setReviewTime(LocalDateTime.now());
        answer.setUpdateTime(LocalDateTime.now());
        examAnswerGateway.update(answer);

        logger.info("人工复核单题 [answerId={}, score={}, reviewer={}]",
                answerId, reviewScore, reviewer);
    }
}
