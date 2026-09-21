package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.client.api.ExamGradingServiceI;
import com.mouhin.knowledge.repository.domain.gateway.ExamAlertGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 考试评分定时任务
 *
 * <p>每 5 分钟执行一次：先回收超时卡在 GRADING 的场次（进程崩溃兜底）， 再扫描 SUBMITTED 状态且交卷时间超过延迟阈值的考试自动触发 AI 评分。
 * 真正的并发去重由评分入口的 {@code SUBMITTED→GRADING} CAS 认领保证。
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@Component
@Slf4j
public class ExamGradingScheduler {

    /** 每轮扫描的待评分场次上限 */
    private static final int PENDING_BATCH_SIZE = 50;

    /** 超时回收单轮扫描上限 */
    private static final int RECOVER_BATCH_SIZE = 100;

    /** 场次状态：评分中（并发认领态） */
    private static final String STATUS_GRADING = "GRADING";

    private final ExamSessionGateway examSessionGateway;
    private final ExamGradingServiceI gradingService;
    private final ExamAlertGateway examAlertGateway;

    @Value("${knowledge.exam.grading-delay-minutes:30}")
    private int gradingDelayMinutes;

    @Value("${knowledge.exam.grading-timeout-minutes:15}")
    private int gradingTimeoutMinutes;

    public ExamGradingScheduler(
            ExamSessionGateway examSessionGateway,
            ExamGradingServiceI gradingService,
            ExamAlertGateway examAlertGateway) {
        this.examSessionGateway = examSessionGateway;
        this.gradingService = gradingService;
        this.examAlertGateway = examAlertGateway;
    }

    /**
     * 定时扫描待评分考试并自动触发
     *
     * <p>每 5 分钟执行一次，先回收超时卡在 GRADING 的场次，再扫描 SUBMITTED 状态且交卷时间超过延迟阈值的考试。
     */
    @Scheduled(fixedRate = 300000)
    public void scheduleAutoGrading() {
        // 阶段 2-B：超时回收 —— 进程崩溃等导致评分卡在 GRADING 的场次，回退为 SUBMITTED 重新纳入调度
        recoverStuckGrading();

        List<ExamSession> pending = examSessionGateway.listPendingGrading(PENDING_BATCH_SIZE);
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
                    log.error("定时评分失败 [session={}]", session.getId(), e);
                }
            }
        }

        if (triggered > 0) {
            log.info("定时评分：本轮触发 {} 场考试（延迟阈值 {} 分钟）", triggered, gradingDelayMinutes);
        }
    }

    /**
     * 回收超时卡在 GRADING 的场次。
     *
     * <p>评分中途异常的回退已在异步链路即时处理，本方法专门兜底进程崩溃 / 实例重启等无法回退的场景： 将 {@code update_time} 早于超时阈值仍处于 GRADING
     * 的场次以 CAS 回退为 SUBMITTED，使其重新参与调度。
     */
    private void recoverStuckGrading() {
        try {
            List<ExamSession> grading =
                    examSessionGateway.listByStatus(STATUS_GRADING, RECOVER_BATCH_SIZE, 0);
            if (grading.isEmpty()) {
                return;
            }
            LocalDateTime deadline = LocalDateTime.now().minusMinutes(gradingTimeoutMinutes);
            int recovered = 0;
            for (ExamSession session : grading) {
                // CONC-1：单条原子回收——SQL 内判定 status==GRADING 且 update_time<deadline，
                // 回退 SUBMITTED 同时清空围栏令牌，使在途旧评分者心跳/终态立即失配退出。
                // 仅当确实回收了本场次（affected=1）才告警，避免与刚完成评分的行竞争误报。
                if (examSessionGateway.reclaimStuckGrading(session.getId(), deadline)) {
                    recovered++;
                    log.warn(
                            "回收超时评分场次 [session={}, lastUpdate={}]",
                            session.getId(),
                            session.getUpdateTime());
                    examAlertGateway.gradingTimeout(session.getId());
                }
            }
            if (recovered > 0) {
                log.info("超时评分回收：本轮回退 {} 场（超时阈值 {} 分钟）", recovered, gradingTimeoutMinutes);
            }
        } catch (Exception e) {
            log.error("超时评分回收任务异常", e);
        }
    }
}
