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

    /**
     * 原子状态流转（CAS）：仅当前状态等于 {@code expectedStatus} 时才更新为 {@code newStatus}。
     * <p>用于并发认领场景（如评分 {@code SUBMITTED → GRADING}），返回 {@code true} 表示本次成功抢占。</p>
     *
     * @param id             场次主键
     * @param expectedStatus 期望的当前状态
     * @param newStatus      目标新状态
     * @return 是否更新成功（受影响行数为 1）
     */
    boolean casUpdateStatus(Long id, String expectedStatus, String newStatus);

    /**
     * 评分心跳续约：仅当场次当前仍为 {@code GRADING} 时刷新 {@code update_time}（不改动状态）。
     * <p>用于长时间评分过程中周期性续命，使超时回收任务只回收"真正卡死"（update_time 停滞）的场次，
     * 而不会把仍在运行的评分误判为卡死并抢占，导致同一场次被两个评分流程交叉写。</p>
     *
     * @param id 场次主键
     * @return 是否仍在 GRADING（受影响行数 &gt; 0）；false 表示已被其它流程接管或已终态，调用方应放弃写入
     */
    boolean touchGradingHeartbeat(Long id);

    /**
     * 评分终态原子落库（CAS）：仅当当前状态为 {@code expectedStatus}（GRADING）时，一次性写入
     * 目标状态 {@code newStatus}（AI_GRADED）及评分结果（ai_score / total_score / grade_time / update_time）。
     * <p>杜绝"回收已把场次改回 SUBMITTED、慢速原评分者完成时又无条件 updateById 覆盖为 AI_GRADED"的丢失更新。</p>
     *
     * @param id             场次主键
     * @param expectedStatus 期望的当前状态（GRADING）
     * @param newStatus      目标终态（AI_GRADED）
     * @param aiScore        AI 总分
     * @param totalScore     卷面总分
     * @return 是否落库成功；false 表示已不再持有 GRADING 所有权（已被接管），调用方不应再上报完成
     */
    boolean completeGrading(Long id, String expectedStatus, String newStatus, int aiScore, int totalScore);

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
