package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import java.util.List;
import java.util.Optional;

/**
 * AI 出卷历史仓储接口
 *
 * <p>P0-T3：为管理端列表新增 {@link Permission} 作用域方法。 语义约定：{@code permission == null} 或 {@code
 * permission.isAdmin() == true} → 全局视图；否则在 SQL 层强制 {@code user_id = ? OR department_id = ?} 谓词，null
 * / 空白一侧自动跳过，两侧都空则退化为 1=0（不可见）。 学生端 {@link #listPublished(int)} 保持公开口径不变（仅返回 PUBLISHED 全量），
 * 不受本改造影响。
 *
 * @author mouhinU
 * @date 2026-09-14
 */
public interface ExamHistoryGateway {

    void save(ExamHistory history);

    void update(ExamHistory history);

    Optional<ExamHistory> findById(Long id);

    Optional<ExamHistory> findBySessionId(String sessionId);

    /** 按创建时间倒序查询历史记录 */
    List<ExamHistory> listRecent(int limit);

    /**
     * 查询已发布（学生可开考）的试卷，按创建时间倒序。
     *
     * @param limit 最大返回数量
     * @return 已发布试卷列表
     */
    List<ExamHistory> listPublished(int limit);

    /**
     * 查询待校对队列（状态为 REVIEWABLE 或 VALIDATION_FAILED），按创建时间倒序。
     *
     * @param limit 每页数量
     * @param offset 偏移量
     * @return 待校对试卷列表
     */
    List<ExamHistory> listReviewPending(int limit, int offset);

    /** 统计待校对队列总数（REVIEWABLE + VALIDATION_FAILED）。 */
    long countReviewPending();

    /**
     * 分页查询历史记录（按创建时间倒序）
     *
     * @param limit 每页数量
     * @param offset 偏移量
     * @return 当前页历史记录
     */
    List<ExamHistory> listPage(int limit, int offset);

    /** 统计历史记录总数 */
    long countAll();

    /** 根据会话 ID 删除出卷历史 */
    void deleteBySessionId(String sessionId);

    // ==================== P0-T3 带作用域的方法（管理端专用） ====================

    /** 分页查询历史记录（作用域版）。{@code permission==null}/{@code admin=true} → 全局；否则按 owner/dept 谓词过滤。 */
    List<ExamHistory> listPageScoped(int limit, int offset, Permission permission);

    /** 统计历史记录总数（作用域版）。 */
    long countAllScoped(Permission permission);

    /** 待校对队列分页（作用域版）。 */
    List<ExamHistory> listReviewPendingScoped(int limit, int offset, Permission permission);

    /** 待校对队列总数（作用域版）。 */
    long countReviewPendingScoped(Permission permission);
}
