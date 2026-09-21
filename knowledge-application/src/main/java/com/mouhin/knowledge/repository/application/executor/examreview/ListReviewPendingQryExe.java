package com.mouhin.knowledge.repository.application.executor.examreview;

import com.mouhin.knowledge.repository.application.converter.ExamGenerationConverter;
import com.mouhin.knowledge.repository.client.dto.ExamHistoryDTO;
import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 查询待校对试卷列表执行器（app 层用例，阶段 1-D）
 *
 * <p>返回状态为 REVIEWABLE / VALIDATION_FAILED 的试卷，按创建时间倒序分页。
 *
 * @author Knowledge-Repository
 * @date 2026-09-18
 */
@Component("paperReviewListPendingQryExe")
public class ListReviewPendingQryExe {

    private final ExamHistoryGateway examHistoryGateway;

    public ListReviewPendingQryExe(ExamHistoryGateway examHistoryGateway) {
        this.examHistoryGateway = examHistoryGateway;
    }

    public List<ExamHistoryDTO> execute(int limit, int offset) {
        return examHistoryGateway.listReviewPending(limit, offset).stream()
                .map(ExamGenerationConverter::toHistoryDTO)
                .toList();
    }

    public long count() {
        return examHistoryGateway.countReviewPending();
    }
}
