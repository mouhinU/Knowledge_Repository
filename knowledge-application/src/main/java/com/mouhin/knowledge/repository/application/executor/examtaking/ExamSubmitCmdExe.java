package com.mouhin.knowledge.repository.application.executor.examtaking;

import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 交卷命令执行器（app 层用例，事务边界）
 * <p>标记考试为已提交；评分由定时任务或管理员手动触发（已与交卷解耦）。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class ExamSubmitCmdExe {

    private static final Logger logger = LoggerFactory.getLogger(ExamSubmitCmdExe.class);

    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    /** 交卷宽限期（秒） */
    private static final int SUBMIT_GRACE_SECONDS = 30;

    private static final int SECONDS_PER_MINUTE = 60;

    private final ExamSessionGateway examSessionGateway;
    private final ExamTakingSupport support;

    public ExamSubmitCmdExe(ExamSessionGateway examSessionGateway, ExamTakingSupport support) {
        this.examSessionGateway = examSessionGateway;
        this.support = support;
    }

    @Transactional
    public void execute(String sessionKey, String studentToken) {
        ExamSession session = support.resolveSession(sessionKey, studentToken);

        if (!STATUS_IN_PROGRESS.equals(session.getStatus())) {
            throw new IllegalStateException("考试已提交，请勿重复操作");
        }

        // 服务端校验：检查是否超过考试时长（30 秒宽限期）
        if (session.getDurationMinutes() != null && session.getDurationMinutes() > 0
                && session.getStartTime() != null) {
            long elapsedSeconds = Duration.between(session.getStartTime(), LocalDateTime.now()).getSeconds();
            long allowedSeconds = (long) session.getDurationMinutes() * SECONDS_PER_MINUTE + SUBMIT_GRACE_SECONDS;
            if (elapsedSeconds > allowedSeconds) {
                logger.warn("考试超时提交 [session={}, elapsed={}s, allowed={}s]",
                        sessionKey, elapsedSeconds, allowedSeconds);
                // 超时仍允许交卷（前端已自动触发），但记录日志
            }
        }

        session.submit();
        session.setUpdateTime(LocalDateTime.now());
        examSessionGateway.update(session);

        logger.info("考生交卷 [session={}, student={}]", sessionKey, session.getStudentId());
    }
}
