package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.infrastructure.persistence.converter.ExamHistoryConverter;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.ExamHistoryDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.ExamHistoryMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * AI 出卷历史仓储实现
 *
 * @author Knowledge-Repository
 * @date 2026-09-14
 */
@Repository
public class ExamHistoryGatewayImpl implements ExamHistoryGateway {

    private final ExamHistoryMapper examHistoryMapper;

    public ExamHistoryGatewayImpl(ExamHistoryMapper examHistoryMapper) {
        this.examHistoryMapper = examHistoryMapper;
    }

    @Override
    public void save(ExamHistory history) {
        ExamHistoryDO doObj = ExamHistoryConverter.toDO(history);
        examHistoryMapper.insert(doObj);
        history.setId(doObj.getId());
    }

    @Override
    public void update(ExamHistory history) {
        ExamHistoryDO doObj = ExamHistoryConverter.toDO(history);
        examHistoryMapper.updateById(doObj);
    }

    @Override
    public Optional<ExamHistory> findById(Long id) {
        ExamHistoryDO doObj = examHistoryMapper.selectById(id);
        return Optional.ofNullable(ExamHistoryConverter.toDomain(doObj));
    }

    @Override
    public Optional<ExamHistory> findBySessionId(String sessionId) {
        LambdaQueryWrapper<ExamHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamHistoryDO::getSessionId, sessionId);
        ExamHistoryDO doObj = examHistoryMapper.selectOne(wrapper);
        return Optional.ofNullable(ExamHistoryConverter.toDomain(doObj));
    }

    @Override
    public List<ExamHistory> listRecent(int limit) {
        LambdaQueryWrapper<ExamHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ExamHistoryDO::getCreateTime).last("LIMIT " + limit);
        return examHistoryMapper.selectList(wrapper).stream()
                .map(ExamHistoryConverter::toDomain)
                .toList();
    }

    @Override
    public List<ExamHistory> listPublished(int limit) {
        LambdaQueryWrapper<ExamHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamHistoryDO::getStatus, ExamHistory.STATUS_PUBLISHED)
                .orderByDesc(ExamHistoryDO::getCreateTime)
                .last("LIMIT " + (limit > 0 ? limit : 20));
        return examHistoryMapper.selectList(wrapper).stream()
                .map(ExamHistoryConverter::toDomain)
                .toList();
    }

    @Override
    public List<ExamHistory> listReviewPending(int limit, int offset) {
        int safeLimit = limit > 0 ? limit : 20;
        int safeOffset = Math.max(offset, 0);
        LambdaQueryWrapper<ExamHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(
                        ExamHistoryDO::getStatus,
                        ExamHistory.STATUS_REVIEWABLE,
                        ExamHistory.STATUS_VALIDATION_FAILED)
                .orderByDesc(ExamHistoryDO::getCreateTime)
                .last("LIMIT " + safeLimit + " OFFSET " + safeOffset);
        return examHistoryMapper.selectList(wrapper).stream()
                .map(ExamHistoryConverter::toDomain)
                .toList();
    }

    @Override
    public long countReviewPending() {
        LambdaQueryWrapper<ExamHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(
                ExamHistoryDO::getStatus,
                ExamHistory.STATUS_REVIEWABLE,
                ExamHistory.STATUS_VALIDATION_FAILED);
        return examHistoryMapper.selectCount(wrapper);
    }

    @Override
    public List<ExamHistory> listPage(int limit, int offset) {
        int safeLimit = limit > 0 ? limit : 20;
        int safeOffset = Math.max(offset, 0);
        LambdaQueryWrapper<ExamHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ExamHistoryDO::getCreateTime)
                .last("LIMIT " + safeLimit + " OFFSET " + safeOffset);
        return examHistoryMapper.selectList(wrapper).stream()
                .map(ExamHistoryConverter::toDomain)
                .toList();
    }

    @Override
    public long countAll() {
        return examHistoryMapper.selectCount(null);
    }

    @Override
    public void deleteBySessionId(String sessionId) {
        LambdaQueryWrapper<ExamHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamHistoryDO::getSessionId, sessionId);
        examHistoryMapper.delete(wrapper);
    }
}
