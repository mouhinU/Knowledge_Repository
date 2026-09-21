package com.mouhin.knowledge.repository.application.executor.articlegeneration;

import com.mouhin.knowledge.repository.application.converter.ArticleConverter;
import com.mouhin.knowledge.repository.client.dto.WritingHistoryDTO;
import com.mouhin.knowledge.repository.domain.gateway.WritingHistoryGateway;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 查询最近写作历史列表执行器
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class ListHistoryQryExe {

    private final WritingHistoryGateway writingHistoryGateway;

    public ListHistoryQryExe(WritingHistoryGateway writingHistoryGateway) {
        this.writingHistoryGateway = writingHistoryGateway;
    }

    public List<WritingHistoryDTO> execute(int limit) {
        return writingHistoryGateway.listRecent(limit > 0 ? limit : 20).stream()
                .map(ArticleConverter::toHistoryDTO)
                .toList();
    }
}
