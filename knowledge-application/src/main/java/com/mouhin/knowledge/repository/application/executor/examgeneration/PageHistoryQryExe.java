package com.mouhin.knowledge.repository.application.executor.examgeneration;

import com.mouhin.knowledge.repository.application.converter.ExamGenerationConverter;
import com.mouhin.knowledge.repository.client.dto.ExamHistoryDTO;
import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 分页查询出卷历史执行器（records / total / page / size）
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
public class PageHistoryQryExe {

    private final ExamHistoryGateway examHistoryGateway;

    public PageHistoryQryExe(ExamHistoryGateway examHistoryGateway) {
        this.examHistoryGateway = examHistoryGateway;
    }

    public Map<String, Object> execute(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size > 0 ? size : 10;
        int offset = safePage * safeSize;
        List<ExamHistoryDTO> records =
                examHistoryGateway.listPage(safeSize, offset).stream()
                        .map(ExamGenerationConverter::toHistoryDTO)
                        .toList();
        long total = examHistoryGateway.countAll();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }
}
