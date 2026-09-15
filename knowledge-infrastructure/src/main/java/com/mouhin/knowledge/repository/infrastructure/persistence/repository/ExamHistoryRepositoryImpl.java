package com.mouhin.knowledge.repository.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.repository.ExamHistoryRepository;
import com.mouhin.knowledge.repository.infrastructure.persistence.converter.ExamHistoryConverter;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.ExamHistoryDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.ExamHistoryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AI 出卷历史仓储实现
 *
 * @author Knowledge-Repository
 * @date 2026-09-14
 */
@Repository
public class ExamHistoryRepositoryImpl implements ExamHistoryRepository {

    private final ExamHistoryMapper examHistoryMapper;

    public ExamHistoryRepositoryImpl(ExamHistoryMapper examHistoryMapper) {
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
        wrapper.orderByDesc(ExamHistoryDO::getCreateTime)
                .last("LIMIT " + limit);
        return examHistoryMapper.selectList(wrapper).stream()
                .map(ExamHistoryConverter::toDomain)
                .toList();
    }

    @Override
    public void deleteBySessionId(String sessionId) {
        LambdaQueryWrapper<ExamHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamHistoryDO::getSessionId, sessionId);
        examHistoryMapper.delete(wrapper);
    }
}
