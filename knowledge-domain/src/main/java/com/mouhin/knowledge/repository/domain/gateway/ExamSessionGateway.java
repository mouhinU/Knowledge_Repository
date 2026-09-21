package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import java.time.LocalDateTime;
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
     *
     * <p>通用状态机流转使用；评分认领 / 心跳 / 终态请改用下方围栏令牌方法（CONC-1）。
     *
     * @param id 场次主键
     * @param expectedStatus 期望的当前状态
     * @param newStatus 目标新状态
     * @return 是否更新成功（受影响行数为 1）
     */
    boolean casUpdateStatus(Long id, String expectedStatus, String newStatus);

    /**
     * 评分认领（带围栏令牌，CONC-1）：原子地将 {@code SUBMITTED} 抢占为 {@code GRADING} 并写入一次性令牌。
     *
     * <p>后续心跳 / 终态 / 失败回退都以「status==GRADING 且令牌匹配」为谓词。区别于裸 {@code
     * casUpdateStatus}：一旦本场次被超时回收再被他人重新认领，令牌会被轮换，旧评分者随即失去 所有权判定，杜绝回收后新旧评分者交叉写、覆盖终态。
     *
     * @param id 场次主键
     * @return 认领成功返回本次写入的围栏令牌；认领失败（非 SUBMITTED 或已被抢占）返回 {@code null}
     */
    String claimForGrading(Long id);

    /**
     * 评分心跳续约（带围栏令牌，CONC-1）：仅当场次仍为 {@code GRADING} 且令牌匹配时刷新 {@code update_time}。
     *
     * @param id 场次主键
     * @param token {@link #claimForGrading(Long)} 返回的围栏令牌
     * @return {@code true}=仍持有所有权；{@code false}=已被回收/接管或已终态，调用方须立即停止写入
     */
    boolean touchGradingHeartbeat(Long id, String token);

    /**
     * 评分终态原子落库（带围栏令牌，CONC-1）：仅当 {@code GRADING} 且令牌匹配时一次性写入终态与分数。
     *
     * @param id 场次主键
     * @param token 围栏令牌
     * @param newStatus 目标终态（AI_GRADED）
     * @param aiScore AI 总分
     * @param totalScore 卷面总分
     * @return 是否落库成功；{@code false}=已非本次认领的 GRADING（被接管），调用方不得再上报完成
     */
    boolean completeGrading(Long id, String token, String newStatus, int aiScore, int totalScore);

    /**
     * 评分失败安全回退（带围栏令牌，CONC-1）：仅当 {@code GRADING} 且令牌匹配时回退为 {@code SUBMITTED}
     * 并清空令牌，供异步评分异常时重新纳入调度；不误伤已被他人重新认领的场次。
     *
     * @param id 场次主键
     * @param token 围栏令牌
     * @return 是否回退成功（受影响行数为 1）
     */
    boolean releaseGradingToSubmitted(Long id, String token);

    /**
     * 超时回收（轮换令牌，CONC-1）：原子地将「仍为 {@code GRADING} 且 {@code update_time} 早于 {@code deadline}」的场次回退为
     * {@code SUBMITTED} 并清空围栏令牌。时间判定下沉到 SQL 谓词， 消除调度器「先查后改」的 TOCTOU；令牌置空使在途旧评分者心跳 / 终态立即失效。
     *
     * @param id 场次主键
     * @param deadline 超时阈值（update_time 早于此值视为卡死）
     * @return 是否回收成功（受影响行数为 1）
     */
    boolean reclaimStuckGrading(Long id, LocalDateTime deadline);

    Optional<ExamSession> findById(Long id);

    Optional<ExamSession> findBySessionKey(String sessionKey);

    List<ExamSession> listByStudentId(Long studentId);

    /** 按状态查询考试场次（用于管理端复核列表） */
    List<ExamSession> listByStatus(String status, int limit, int offset);

    /** 统计指定状态的场次数量 */
    long countByStatus(String status);

    /** 查询所有需要评分的场次（SUBMITTED 状态） */
    List<ExamSession> listPendingGrading(int limit);

    /** 查询所有需要复核的场次（AI_GRADED 状态） */
    List<ExamSession> listPendingReview(int limit, int offset);

    /** 统计需要复核的场次数量 */
    long countPendingReview();

    /**
     * 按考生和状态列表查询考试场次
     *
     * @param studentId 考生 ID
     * @param statuses 状态列表
     * @return 符合条件的场次列表
     */
    List<ExamSession> listByStudentIdAndStatuses(Long studentId, List<String> statuses);

    /**
     * 「一人一卷一次」系统级守卫：判断某考生是否已对指定试卷开过场次（任意状态均计入， 含 IN_PROGRESS / SUBMITTED / AI_GRADED / ... ）。
     *
     * @param studentId 考生 ID
     * @param examHistoryId 出卷历史 ID（试卷唯一标识）
     * @return true=已存在历史场次，不允许再次开考
     */
    boolean existsByStudentIdAndExamHistoryId(Long studentId, Long examHistoryId);

    /**
     * 查询某考生已开过场次的试卷 ID 集合（去重）。供「可用考试」列表按学生过滤已考卷使用。
     *
     * @param studentId 考生 ID
     * @return 已考过的 examHistoryId 集合（不含即时卷：即时卷 examHistoryId 为空自动排除）
     */
    java.util.List<Long> listExamHistoryIdsByStudentId(Long studentId);

    /**
     * 作废 / 撤销作废级联：按试卷 ID 批量回写其下所有考试场次的 voided 标记。
     *
     * @param examHistoryId 出卷历史 ID
     * @param voided true=标注作废；false=撤销作废（试卷重新发布时回滚）
     * @return 受影响行数
     */
    int markVoidedByExamHistoryId(Long examHistoryId, boolean voided);

    /**
     * 按状态列表查询所有考试场次
     *
     * @param statuses 状态列表
     * @return 符合条件的场次列表
     */
    List<ExamSession> listByStatuses(List<String> statuses);
}
