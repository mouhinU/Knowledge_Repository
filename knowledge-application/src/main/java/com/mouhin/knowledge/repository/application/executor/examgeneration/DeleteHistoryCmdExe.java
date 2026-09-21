package com.mouhin.knowledge.repository.application.executor.examgeneration;

import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 删除出卷历史记录执行器
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
@Slf4j
public class DeleteHistoryCmdExe {

    private final ExamHistoryGateway examHistoryGateway;

    public DeleteHistoryCmdExe(ExamHistoryGateway examHistoryGateway) {
        this.examHistoryGateway = examHistoryGateway;
    }

    public void execute(String sessionId) {
        examHistoryGateway.deleteBySessionId(sessionId);
        log.info("出卷历史记录已删除 [session={}]", sessionId);
    }
}
