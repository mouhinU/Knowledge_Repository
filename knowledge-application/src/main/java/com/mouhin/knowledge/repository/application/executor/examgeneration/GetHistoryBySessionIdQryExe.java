package com.mouhin.knowledge.repository.application.executor.examgeneration;

import com.mouhin.knowledge.repository.application.converter.ExamGenerationConverter;
import com.mouhin.knowledge.repository.client.dto.ExamHistoryDTO;
import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import org.springframework.stereotype.Component;

/**
 * 根据会话 ID 查询出卷历史详情执行器（不存在时返回 null）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component("examGetHistoryBySessionIdQryExe")
public class GetHistoryBySessionIdQryExe {

    private final ExamHistoryGateway examHistoryGateway;

    public GetHistoryBySessionIdQryExe(ExamHistoryGateway examHistoryGateway) {
        this.examHistoryGateway = examHistoryGateway;
    }

    public ExamHistoryDTO execute(String sessionId) {
        return examHistoryGateway.findBySessionId(sessionId)
                .map(ExamGenerationConverter::toHistoryDTO)
                .orElse(null);
    }
}
