package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.knowledge.repository.domain.gateway.WritingHistoryGateway;
import com.mouhin.knowledge.repository.domain.model.entity.WritingHistory;
import com.mouhin.knowledge.repository.infrastructure.persistence.converter.WritingHistoryConverter;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.WritingHistoryDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.WritingHistoryMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * AI 写作历史仓储实现
 *
 * @author Knowledge-Repository
 * @date 2026-09-13
 */
@Repository
public class WritingHistoryGatewayImpl implements WritingHistoryGateway {

    private final WritingHistoryMapper writingHistoryMapper;

    public WritingHistoryGatewayImpl(WritingHistoryMapper writingHistoryMapper) {
        this.writingHistoryMapper = writingHistoryMapper;
    }

    @Override
    public void save(WritingHistory history) {
        WritingHistoryDO doObj = WritingHistoryConverter.toDO(history);
        writingHistoryMapper.insert(doObj);
        history.setId(doObj.getId());
    }

    @Override
    public void update(WritingHistory history) {
        WritingHistoryDO doObj = WritingHistoryConverter.toDO(history);
        writingHistoryMapper.updateById(doObj);
    }

    @Override
    public Optional<WritingHistory> findBySessionId(String sessionId) {
        LambdaQueryWrapper<WritingHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WritingHistoryDO::getSessionId, sessionId);
        WritingHistoryDO doObj = writingHistoryMapper.selectOne(wrapper);
        return Optional.ofNullable(WritingHistoryConverter.toDomain(doObj));
    }

    @Override
    public List<WritingHistory> listRecent(int limit) {
        LambdaQueryWrapper<WritingHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(WritingHistoryDO::getCreateTime).last("LIMIT " + limit);
        return writingHistoryMapper.selectList(wrapper).stream()
                .map(WritingHistoryConverter::toDomain)
                .toList();
    }
}
