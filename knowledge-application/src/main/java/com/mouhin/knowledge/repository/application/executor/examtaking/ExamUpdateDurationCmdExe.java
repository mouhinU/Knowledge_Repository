package com.mouhin.knowledge.repository.application.executor.examtaking;

import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员覆盖考试时长命令执行器（app 层用例，事务边界）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class ExamUpdateDurationCmdExe {

    private static final Logger logger = LoggerFactory.getLogger(ExamUpdateDurationCmdExe.class);

    private final ExamSessionGateway examSessionGateway;

    public ExamUpdateDurationCmdExe(ExamSessionGateway examSessionGateway) {
        this.examSessionGateway = examSessionGateway;
    }

    @Transactional
    public void execute(String sessionKey, Integer durationMinutes) {
        ExamSession session =
                examSessionGateway
                        .findBySessionKey(sessionKey)
                        .orElseThrow(() -> new IllegalArgumentException("考试场次不存在: " + sessionKey));
        session.setDurationMinutes(durationMinutes);
        session.setUpdateTime(LocalDateTime.now());
        examSessionGateway.update(session);
        logger.info("已更新考试时长 [session={}, duration={}]", sessionKey, durationMinutes);
    }
}
