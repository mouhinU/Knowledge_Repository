package com.mouhin.knowledge.repository.application.executor.articlegeneration;

import com.mouhin.knowledge.repository.application.converter.ArticleConverter;
import com.mouhin.knowledge.repository.client.dto.WritingHistoryDTO;
import com.mouhin.knowledge.repository.domain.gateway.WritingHistoryGateway;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 查询最近写作历史列表执行器
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
public class ListHistoryQryExe {

    /** limit 非正数时的兜底返回条数。 */
    private static final int DEFAULT_HISTORY_LIMIT = 20;

    private final WritingHistoryGateway writingHistoryGateway;

    public ListHistoryQryExe(WritingHistoryGateway writingHistoryGateway) {
        this.writingHistoryGateway = writingHistoryGateway;
    }

    public List<WritingHistoryDTO> execute(int limit) {
        return writingHistoryGateway.listRecent(limit > 0 ? limit : DEFAULT_HISTORY_LIMIT).stream()
                .map(ArticleConverter.INSTANCE::toHistoryDTO)
                .toList();
    }
}
