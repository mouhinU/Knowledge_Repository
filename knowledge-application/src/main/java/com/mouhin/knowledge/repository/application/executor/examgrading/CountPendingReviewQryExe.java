package com.mouhin.knowledge.repository.application.executor.examgrading;

import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import org.springframework.stereotype.Component;

/**
 * 统计待复核考试数量执行器
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
public class CountPendingReviewQryExe {

    private final ExamSessionGateway examSessionGateway;

    public CountPendingReviewQryExe(ExamSessionGateway examSessionGateway) {
        this.examSessionGateway = examSessionGateway;
    }

    public long execute() {
        return examSessionGateway.countPendingReview();
    }
}
