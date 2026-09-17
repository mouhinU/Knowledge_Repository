package com.mouhin.knowledge.repository.application.executor.articlegeneration;

import com.mouhin.knowledge.repository.application.converter.ArticleConverter;
import com.mouhin.knowledge.repository.client.dto.WritingHistoryDTO;
import com.mouhin.knowledge.repository.domain.gateway.WritingHistoryGateway;
import org.springframework.stereotype.Component;

/**
 * 根据 sessionId 查询写作历史详情执行器（不存在时返回 null）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class GetHistoryBySessionIdQryExe {

    private final WritingHistoryGateway writingHistoryGateway;

    public GetHistoryBySessionIdQryExe(WritingHistoryGateway writingHistoryGateway) {
        this.writingHistoryGateway = writingHistoryGateway;
    }

    public WritingHistoryDTO execute(String sessionId) {
        return writingHistoryGateway.findBySessionId(sessionId)
                .map(ArticleConverter::toHistoryDTO)
                .orElse(null);
    }
}
