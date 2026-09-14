package com.mouhin.knowledge.repository.domain.repository;

import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;

import java.util.List;
import java.util.Optional;

/**
 * AI 出卷历史仓储接口
 *
 * @author Knowledge-Repository
 * @date 2026-09-14
 */
public interface ExamHistoryRepository {

    void save(ExamHistory history);

    void update(ExamHistory history);

    Optional<ExamHistory> findBySessionId(String sessionId);

    /**
     * 按创建时间倒序查询历史记录
     */
    List<ExamHistory> listRecent(int limit);
}
