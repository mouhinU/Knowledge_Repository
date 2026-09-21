package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.infrastructure.persistence.converter.ExamSessionConverter;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.ExamSessionDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.ExamSessionMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * 考试场次仓储实现
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@Repository
public class ExamSessionGatewayImpl implements ExamSessionGateway {

    /** 场次状态：已交卷，待评分。 */
    private static final String STATUS_SUBMITTED = "SUBMITTED";

    /** 场次状态：评分中（并发认领态）。 */
    private static final String STATUS_GRADING = "GRADING";

    private final ExamSessionMapper examSessionMapper;

    public ExamSessionGatewayImpl(ExamSessionMapper examSessionMapper) {
        this.examSessionMapper = examSessionMapper;
    }

    @Override
    public void save(ExamSession session) {
        ExamSessionDO doObj = ExamSessionConverter.toDO(session);
        examSessionMapper.insert(doObj);
        session.setId(doObj.getId());
    }

    @Override
    public void update(ExamSession session) {
        ExamSessionDO doObj = ExamSessionConverter.toDO(session);
        examSessionMapper.updateById(doObj);
    }

    @Override
    public boolean casUpdateStatus(Long id, String expectedStatus, String newStatus) {
        LambdaUpdateWrapper<ExamSessionDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ExamSessionDO::getId, id)
                .eq(ExamSessionDO::getStatus, expectedStatus)
                .set(ExamSessionDO::getStatus, newStatus)
                .set(ExamSessionDO::getUpdateTime, LocalDateTime.now());
        return examSessionMapper.update(null, wrapper) == 1;
    }

    @Override
    public String claimForGrading(Long id) {
        // CONC-1：认领即写入一次性围栏令牌。仅 SUBMITTED 可被抢占为 GRADING，
        // 认领失败（非 SUBMITTED / 已被他人抢占）返回 null，调用方据此跳过重复评分。
        String token = UUID.randomUUID().toString().replace("-", "");
        LambdaUpdateWrapper<ExamSessionDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ExamSessionDO::getId, id)
                .eq(ExamSessionDO::getStatus, STATUS_SUBMITTED)
                .set(ExamSessionDO::getStatus, STATUS_GRADING)
                .set(ExamSessionDO::getGradingToken, token)
                .set(ExamSessionDO::getUpdateTime, LocalDateTime.now());
        return examSessionMapper.update(null, wrapper) == 1 ? token : null;
    }

    @Override
    public boolean touchGradingHeartbeat(Long id, String token) {
        // 心跳须同时匹配 status==GRADING 与围栏令牌：令牌不匹配说明本场次已被超时回收并重新认领，
        // 本次评分者已失去所有权，立即返回 false 令上层停止写入，杜绝新旧评分者交叉写。
        if (token == null) {
            return false;
        }
        LambdaUpdateWrapper<ExamSessionDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ExamSessionDO::getId, id)
                .eq(ExamSessionDO::getStatus, STATUS_GRADING)
                .eq(ExamSessionDO::getGradingToken, token)
                .set(ExamSessionDO::getUpdateTime, LocalDateTime.now());
        return examSessionMapper.update(null, wrapper) > 0;
    }

    @Override
    public boolean completeGrading(
            Long id, String token, String newStatus, int aiScore, int totalScore) {
        // 终态落库以围栏令牌为所有权证明：令牌不匹配（已被接管/回收）时放弃写入，
        // 成功则进入终态并清空令牌（不再处于评分认领态）。
        if (token == null) {
            return false;
        }
        LambdaUpdateWrapper<ExamSessionDO> wrapper = new LambdaUpdateWrapper<>();
        LocalDateTime now = LocalDateTime.now();
        wrapper.eq(ExamSessionDO::getId, id)
                .eq(ExamSessionDO::getStatus, STATUS_GRADING)
                .eq(ExamSessionDO::getGradingToken, token)
                .set(ExamSessionDO::getStatus, newStatus)
                .set(ExamSessionDO::getAiScore, aiScore)
                .set(ExamSessionDO::getTotalScore, totalScore)
                .set(ExamSessionDO::getGradeTime, now)
                .set(ExamSessionDO::getGradingToken, null)
                .set(ExamSessionDO::getUpdateTime, now);
        return examSessionMapper.update(null, wrapper) == 1;
    }

    @Override
    public boolean releaseGradingToSubmitted(Long id, String token) {
        // 异步评分异常的安全回退：仅当仍持有本次围栏令牌时回退为 SUBMITTED 并清空令牌，
        // 避免把已被他人重新认领（令牌已轮换）的场次误回退，造成重复评分。
        if (token == null) {
            return false;
        }
        LambdaUpdateWrapper<ExamSessionDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ExamSessionDO::getId, id)
                .eq(ExamSessionDO::getStatus, STATUS_GRADING)
                .eq(ExamSessionDO::getGradingToken, token)
                .set(ExamSessionDO::getStatus, STATUS_SUBMITTED)
                .set(ExamSessionDO::getGradingToken, null)
                .set(ExamSessionDO::getUpdateTime, LocalDateTime.now());
        return examSessionMapper.update(null, wrapper) == 1;
    }

    @Override
    public boolean reclaimStuckGrading(Long id, LocalDateTime deadline) {
        // 超时回收：时间判定下沉到 SQL（update_time < deadline），消除调度器先查后改的 TOCTOU；
        // 回退 SUBMITTED 的同时清空围栏令牌，使在途旧评分者的心跳/终态谓词立即失配退出。
        LambdaUpdateWrapper<ExamSessionDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ExamSessionDO::getId, id)
                .eq(ExamSessionDO::getStatus, STATUS_GRADING)
                .lt(ExamSessionDO::getUpdateTime, deadline)
                .set(ExamSessionDO::getStatus, STATUS_SUBMITTED)
                .set(ExamSessionDO::getGradingToken, null)
                .set(ExamSessionDO::getUpdateTime, LocalDateTime.now());
        return examSessionMapper.update(null, wrapper) == 1;
    }

    @Override
    public Optional<ExamSession> findById(Long id) {
        ExamSessionDO doObj = examSessionMapper.selectById(id);
        return Optional.ofNullable(ExamSessionConverter.toDomain(doObj));
    }

    @Override
    public Optional<ExamSession> findBySessionKey(String sessionKey) {
        LambdaQueryWrapper<ExamSessionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamSessionDO::getSessionKey, sessionKey);
        ExamSessionDO doObj = examSessionMapper.selectOne(wrapper);
        return Optional.ofNullable(ExamSessionConverter.toDomain(doObj));
    }

    @Override
    public List<ExamSession> listByStudentId(Long studentId) {
        LambdaQueryWrapper<ExamSessionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamSessionDO::getStudentId, studentId)
                .orderByDesc(ExamSessionDO::getCreateTime);
        return examSessionMapper.selectList(wrapper).stream()
                .map(ExamSessionConverter::toDomain)
                .toList();
    }

    @Override
    public List<ExamSession> listByStatus(String status, int limit, int offset) {
        LambdaQueryWrapper<ExamSessionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamSessionDO::getStatus, status)
                .orderByDesc(ExamSessionDO::getCreateTime)
                .last("LIMIT " + limit + " OFFSET " + offset);
        return examSessionMapper.selectList(wrapper).stream()
                .map(ExamSessionConverter::toDomain)
                .toList();
    }

    @Override
    public long countByStatus(String status) {
        LambdaQueryWrapper<ExamSessionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamSessionDO::getStatus, status);
        return examSessionMapper.selectCount(wrapper);
    }

    @Override
    public List<ExamSession> listPendingGrading(int limit) {
        LambdaQueryWrapper<ExamSessionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamSessionDO::getStatus, "SUBMITTED")
                .orderByAsc(ExamSessionDO::getSubmitTime)
                .last("LIMIT " + limit);
        return examSessionMapper.selectList(wrapper).stream()
                .map(ExamSessionConverter::toDomain)
                .toList();
    }

    @Override
    public List<ExamSession> listPendingReview(int limit, int offset) {
        LambdaQueryWrapper<ExamSessionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamSessionDO::getStatus, "AI_GRADED")
                .orderByAsc(ExamSessionDO::getGradeTime)
                .last("LIMIT " + limit + " OFFSET " + offset);
        return examSessionMapper.selectList(wrapper).stream()
                .map(ExamSessionConverter::toDomain)
                .toList();
    }

    @Override
    public long countPendingReview() {
        LambdaQueryWrapper<ExamSessionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamSessionDO::getStatus, "AI_GRADED");
        return examSessionMapper.selectCount(wrapper);
    }

    @Override
    public List<ExamSession> listByStudentIdAndStatuses(Long studentId, List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<ExamSessionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamSessionDO::getStudentId, studentId)
                .in(ExamSessionDO::getStatus, statuses)
                .orderByDesc(ExamSessionDO::getCreateTime);
        return examSessionMapper.selectList(wrapper).stream()
                .map(ExamSessionConverter::toDomain)
                .toList();
    }

    @Override
    public boolean existsByStudentIdAndExamHistoryId(Long studentId, Long examHistoryId) {
        if (studentId == null || examHistoryId == null) {
            return false;
        }
        LambdaQueryWrapper<ExamSessionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamSessionDO::getStudentId, studentId)
                .eq(ExamSessionDO::getExamHistoryId, examHistoryId)
                .select(ExamSessionDO::getId)
                .last("LIMIT 1");
        return !examSessionMapper.selectList(wrapper).isEmpty();
    }

    @Override
    public List<Long> listExamHistoryIdsByStudentId(Long studentId) {
        if (studentId == null) {
            return List.of();
        }
        LambdaQueryWrapper<ExamSessionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamSessionDO::getStudentId, studentId)
                .isNotNull(ExamSessionDO::getExamHistoryId)
                .select(ExamSessionDO::getExamHistoryId);
        return examSessionMapper.selectList(wrapper).stream()
                .map(ExamSessionDO::getExamHistoryId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    @Override
    public int markVoidedByExamHistoryId(Long examHistoryId, boolean voided) {
        if (examHistoryId == null) {
            return 0;
        }
        LambdaUpdateWrapper<ExamSessionDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ExamSessionDO::getExamHistoryId, examHistoryId)
                .set(ExamSessionDO::getVoided, voided)
                .set(ExamSessionDO::getUpdateTime, LocalDateTime.now());
        return examSessionMapper.update(null, wrapper);
    }

    @Override
    public List<ExamSession> listByStatuses(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<ExamSessionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ExamSessionDO::getStatus, statuses).orderByDesc(ExamSessionDO::getCreateTime);
        return examSessionMapper.selectList(wrapper).stream()
                .map(ExamSessionConverter::toDomain)
                .toList();
    }
}
