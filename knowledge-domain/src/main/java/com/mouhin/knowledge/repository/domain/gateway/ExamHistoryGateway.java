package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;

import java.util.List;
import java.util.Optional;

/**
 * AI 出卷历史仓储接口
 *
 * @author Knowledge-Repository
 * @date 2026-09-14
 */
public interface ExamHistoryGateway {

    void save(ExamHistory history);

    void update(ExamHistory history);

    Optional<ExamHistory> findById(Long id);

    Optional<ExamHistory> findBySessionId(String sessionId);

    /**
     * 按创建时间倒序查询历史记录
     */
    List<ExamHistory> listRecent(int limit);

    /**
     * 分页查询历史记录（按创建时间倒序）
     *
     * @param limit  每页数量
     * @param offset 偏移量
     * @return 当前页历史记录
     */
    List<ExamHistory> listPage(int limit, int offset);

    /**
     * 统计历史记录总数
     */
    long countAll();

    /**
     * 根据会话 ID 删除出卷历史
     */
    void deleteBySessionId(String sessionId);
}
