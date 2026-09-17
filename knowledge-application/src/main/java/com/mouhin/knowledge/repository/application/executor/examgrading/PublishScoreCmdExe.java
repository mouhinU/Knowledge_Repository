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
import java.util.List;

/**
 * 完成复核并发布成绩执行器
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class PublishScoreCmdExe {

    private static final Logger logger = LoggerFactory.getLogger(PublishScoreCmdExe.class);

    private final ExamSessionGateway examSessionGateway;
    private final ExamAnswerGateway examAnswerGateway;

    public PublishScoreCmdExe(ExamSessionGateway examSessionGateway, ExamAnswerGateway examAnswerGateway) {
        this.examSessionGateway = examSessionGateway;
        this.examAnswerGateway = examAnswerGateway;
    }

    @Transactional
    public void execute(Long sessionId, String reviewer) {
        ExamSession session = examSessionGateway.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("考试场次不存在: " + sessionId));

        // 重新计算最终成绩（人工复核分数优先）
        List<ExamAnswer> answers = examAnswerGateway.listBySessionId(sessionId);
        int finalScore = answers.stream()
                .mapToInt(ExamAnswer::getEffectiveScore)
                .sum();

        session.markReviewed(finalScore);
        session.markPublished();
        session.setUpdateTime(LocalDateTime.now());
        examSessionGateway.update(session);

        logger.info("成绩已发布 [session={}, finalScore={}, reviewer={}]",
                sessionId, finalScore, reviewer);
    }
}
