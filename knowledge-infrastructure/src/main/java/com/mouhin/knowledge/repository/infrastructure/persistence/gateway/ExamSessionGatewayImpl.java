package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.infrastructure.persistence.converter.ExamSessionConverter;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.ExamSessionDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.ExamSessionMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 考试场次仓储实现
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@Repository
public class ExamSessionGatewayImpl implements ExamSessionGateway {

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
    public List<ExamSession> listByStatuses(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<ExamSessionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ExamSessionDO::getStatus, statuses)
                .orderByDesc(ExamSessionDO::getCreateTime);
        return examSessionMapper.selectList(wrapper).stream()
                .map(ExamSessionConverter::toDomain)
                .toList();
    }
}
