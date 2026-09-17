package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.WritingHistory;

import java.util.List;
import java.util.Optional;

/**
 * AI 写作历史仓储接口
 *
 * @author Knowledge-Repository
 * @date 2026-09-13
 */
public interface WritingHistoryGateway {

    void save(WritingHistory history);

    void update(WritingHistory history);

    Optional<WritingHistory> findBySessionId(String sessionId);

    /**
     * 按创建时间倒序查询历史记录
     */
    List<WritingHistory> listRecent(int limit);
}
