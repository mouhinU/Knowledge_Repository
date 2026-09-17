package com.mouhin.knowledge.repository.application.executor.examgrading;

import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.gateway.ExamAnswerGateway;
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

    public ReviewAnswerCmdExe(ExamAnswerGateway examAnswerGateway) {
        this.examAnswerGateway = examAnswerGateway;
    }

    @Transactional
    public void execute(Long answerId, Integer reviewScore, String reviewFeedback, String reviewer) {
        ExamAnswer answer = examAnswerGateway.findById(answerId)
                .orElseThrow(() -> new IllegalArgumentException("答题记录不存在: " + answerId));

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
