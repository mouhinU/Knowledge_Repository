package com.mouhin.knowledge.repository.application.executor.examgeneration;

import com.mouhin.knowledge.repository.application.converter.ExamGenerationConverter;
import com.mouhin.knowledge.repository.client.dto.ExamHistoryDTO;
import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 查询出卷历史列表执行器
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component("examListHistoryQryExe")
public class ListHistoryQryExe {

    /** limit 非正数时的兜底返回条数。 */
    private static final int DEFAULT_HISTORY_LIMIT = 20;

    private final ExamHistoryGateway examHistoryGateway;

    public ListHistoryQryExe(ExamHistoryGateway examHistoryGateway) {
        this.examHistoryGateway = examHistoryGateway;
    }

    public List<ExamHistoryDTO> execute(int limit) {
        return examHistoryGateway.listRecent(limit > 0 ? limit : DEFAULT_HISTORY_LIMIT).stream()
                .map(ExamGenerationConverter::toHistoryDTO)
                .toList();
    }
}
