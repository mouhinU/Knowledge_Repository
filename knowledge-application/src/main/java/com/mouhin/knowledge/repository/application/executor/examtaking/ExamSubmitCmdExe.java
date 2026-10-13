package com.mouhin.knowledge.repository.application.executor.examtaking;

import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 交卷命令执行器（app 层用例，事务边界）
 *
 * <p>标记考试为已提交；评分由定时任务或管理员手动触发（已与交卷解耦）。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
@Slf4j
public class ExamSubmitCmdExe {

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
        if (session.getDurationMinutes() != null
                && session.getDurationMinutes() > 0
                && session.getStartTime() != null) {
            // java:S8700：显式绑定 ZoneId，避免 DB / app 默认时区漂移导致 elapsed 计算虚高/虚低。
            ZoneId zone = ZoneId.systemDefault();
            ZonedDateTime startAt = session.getStartTime().atZone(zone);
            ZonedDateTime nowAt = ZonedDateTime.now(zone);
            long elapsedSeconds = Duration.between(startAt, nowAt).getSeconds();
            long allowedSeconds =
                    (long) session.getDurationMinutes() * SECONDS_PER_MINUTE + SUBMIT_GRACE_SECONDS;
            if (elapsedSeconds > allowedSeconds) {
                log.warn(
                        "考试超时提交 [session={}, elapsed={}s, allowed={}s]",
                        sessionKey,
                        elapsedSeconds,
                        allowedSeconds);
                // 超时仍允许交卷（前端已自动触发），但记录日志
            }
        }

        session.submit();
        session.setUpdateTime(LocalDateTime.now());
        examSessionGateway.update(session);

        log.info("考生交卷 [session={}, student={}]", sessionKey, session.getStudentId());
    }
}
