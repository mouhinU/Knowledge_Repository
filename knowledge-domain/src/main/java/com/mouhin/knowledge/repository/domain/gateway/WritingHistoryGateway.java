package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.WritingHistory;
import java.util.List;
import java.util.Optional;

/**
 * AI 写作历史仓储接口
 *
 * @author mouhinU
 * @date 2026-09-13
 */
public interface WritingHistoryGateway {

    /**
     * 持久化新写作历史记录，回填自增主键到入参实体。
     *
     * @param history 写作历史实体，{@code id} 应为 {@code null}
     */
    void save(WritingHistory history);

    /**
     * 更新已有写作历史记录。
     *
     * @param history 写作历史实体，{@code id} 必填
     */
    void update(WritingHistory history);

    /**
     * 按会话标识查询写作历史。
     *
     * @param sessionId 会话标识
     * @return 命中的写作历史；不存在时返回 {@link Optional#empty()}
     */
    Optional<WritingHistory> findBySessionId(String sessionId);

    /**
     * 按创建时间倒序查询最近的历史记录。
     *
     * @param limit 最大返回条数
     * @return 历史记录列表（按创建时间倒序）；无记录返回空列表
     */
    List<WritingHistory> listRecent(int limit);
}
