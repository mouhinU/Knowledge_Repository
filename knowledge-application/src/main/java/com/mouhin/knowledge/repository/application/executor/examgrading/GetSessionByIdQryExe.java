package com.mouhin.knowledge.repository.application.executor.examgrading;

import com.mouhin.knowledge.repository.application.converter.ExamTakingConverter;
import com.mouhin.knowledge.repository.client.dto.ExamSessionDTO;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import org.springframework.stereotype.Component;

/**
 * 根据场次 ID 查询考试场次执行器
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class GetSessionByIdQryExe {

    private final ExamSessionGateway examSessionGateway;

    public GetSessionByIdQryExe(ExamSessionGateway examSessionGateway) {
        this.examSessionGateway = examSessionGateway;
    }

    public ExamSessionDTO execute(Long sessionId) {
        return examSessionGateway.findById(sessionId)
                .map(ExamTakingConverter::toSessionDTO)
                .orElseThrow(() -> new IllegalArgumentException("考试场次不存在: " + sessionId));
    }
}
