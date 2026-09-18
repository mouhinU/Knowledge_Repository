package com.mouhin.knowledge.repository.application.executor.examgeneration;

import com.mouhin.knowledge.repository.application.converter.ExamGenerationConverter;
import com.mouhin.knowledge.repository.client.dto.ExamHistoryDTO;
import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 查询已发布试卷列表执行器（app 层用例，阶段 1-D）
 * <p>仅返回 {@code PUBLISHED} 状态试卷，供学生端开考入口使用（未发布 / 待校对 / 校验不通过的卷不出现）。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-18
 */
@Component("examListPublishedHistoryQryExe")
public class ListPublishedHistoryQryExe {

    private final ExamHistoryGateway examHistoryGateway;

    public ListPublishedHistoryQryExe(ExamHistoryGateway examHistoryGateway) {
        this.examHistoryGateway = examHistoryGateway;
    }

    public List<ExamHistoryDTO> execute(int limit) {
        return examHistoryGateway.listPublished(limit > 0 ? limit : 20).stream()
                .map(ExamGenerationConverter::toHistoryDTO)
                .toList();
    }
}
