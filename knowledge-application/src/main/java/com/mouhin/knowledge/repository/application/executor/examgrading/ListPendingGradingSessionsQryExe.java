package com.mouhin.knowledge.repository.application.executor.examgrading;

import com.mouhin.knowledge.repository.application.converter.ExamTakingConverter;
import com.mouhin.knowledge.repository.client.dto.ExamSessionDTO;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 查询待评分考试列表执行器（SUBMITTED 状态）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class ListPendingGradingSessionsQryExe {

    private final ExamSessionGateway examSessionGateway;

    public ListPendingGradingSessionsQryExe(ExamSessionGateway examSessionGateway) {
        this.examSessionGateway = examSessionGateway;
    }

    public List<ExamSessionDTO> execute(int limit, int offset) {
        return examSessionGateway.listByStatus("SUBMITTED", limit, offset).stream()
                .map(ExamTakingConverter::toSessionDTO)
                .toList();
    }
}
