package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;

import java.util.List;
import java.util.Optional;

/**
 * 考试场次仓储接口
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
public interface ExamSessionGateway {

    void save(ExamSession session);

    void update(ExamSession session);

    Optional<ExamSession> findById(Long id);

    Optional<ExamSession> findBySessionKey(String sessionKey);

    List<ExamSession> listByStudentId(Long studentId);

    /**
     * 按状态查询考试场次（用于管理端复核列表）
     */
    List<ExamSession> listByStatus(String status, int limit, int offset);

    /**
     * 统计指定状态的场次数量
     */
    long countByStatus(String status);

    /**
     * 查询所有需要评分的场次（SUBMITTED 状态）
     */
    List<ExamSession> listPendingGrading(int limit);

    /**
     * 查询所有需要复核的场次（AI_GRADED 状态）
     */
    List<ExamSession> listPendingReview(int limit, int offset);

    /**
     * 统计需要复核的场次数量
     */
    long countPendingReview();

    /**
     * 按考生和状态列表查询考试场次
     *
     * @param studentId 考生 ID
     * @param statuses  状态列表
     * @return 符合条件的场次列表
     */
    List<ExamSession> listByStudentIdAndStatuses(Long studentId, List<String> statuses);

    /**
     * 按状态列表查询所有考试场次
     *
     * @param statuses 状态列表
     * @return 符合条件的场次列表
     */
    List<ExamSession> listByStatuses(List<String> statuses);
}
