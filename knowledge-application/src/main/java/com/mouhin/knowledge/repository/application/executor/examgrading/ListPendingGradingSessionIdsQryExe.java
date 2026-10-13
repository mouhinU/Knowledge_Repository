package com.mouhin.knowledge.repository.application.executor.examgrading;

import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 查询待评分场次 ID 列表执行器（供批量异步评分使用，最多 100 场）
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
public class ListPendingGradingSessionIdsQryExe {

    private final ExamSessionGateway examSessionGateway;

    public ListPendingGradingSessionIdsQryExe(ExamSessionGateway examSessionGateway) {
        this.examSessionGateway = examSessionGateway;
    }

    public List<Long> execute() {
        return examSessionGateway.listPendingGrading(100).stream().map(ExamSession::getId).toList();
    }
}
