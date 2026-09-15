package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.domain.repository.ExamSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 考试评分定时任务
 * <p>
 * 每 5 分钟扫描一次 SUBMITTED 状态的考试，
 * 当交卷时间超过配置的延迟时间后自动触发 AI 评分流程。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@Component
public class ExamGradingScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ExamGradingScheduler.class);

    private final ExamSessionRepository examSessionRepository;
    private final ExamGradingApplicationService gradingService;

    @Value("${knowledge.exam.grading-delay-minutes:30}")
    private int gradingDelayMinutes;

    public ExamGradingScheduler(ExamSessionRepository examSessionRepository,
                                ExamGradingApplicationService gradingService) {
        this.examSessionRepository = examSessionRepository;
        this.gradingService = gradingService;
    }

    /**
     * 定时扫描待评分考试并自动触发
     * <p>
     * 每 5 分钟执行一次，扫描 SUBMITTED 状态且交卷时间超过延迟阈值的考试。
     * </p>
     */
    @Scheduled(fixedRate = 300000)
    public void scheduleAutoGrading() {
        List<ExamSession> pending = examSessionRepository.listPendingGrading(50);
        if (pending.isEmpty()) {
            return;
        }

        LocalDateTime threshold = LocalDateTime.now().minusMinutes(gradingDelayMinutes);
        int triggered = 0;

        for (ExamSession session : pending) {
            if (session.getSubmitTime() != null && session.getSubmitTime().isBefore(threshold)) {
                try {
                    gradingService.triggerGrading(session.getId());
                    triggered++;
                } catch (Exception e) {
                    logger.error("定时评分失败 [session={}]", session.getId(), e);
                }
            }
        }

        if (triggered > 0) {
            logger.info("定时评分：本轮触发 {} 场考试（延迟阈值 {} 分钟）", triggered, gradingDelayMinutes);
        }
    }
}
