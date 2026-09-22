package com.mouhin.knowledge.repository.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.WritingHistoryDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 写作历史 Mapper
 *
 * @author mouhinU
 * @date 2026-09-13
 */
@Mapper
public interface WritingHistoryMapper extends BaseMapper<WritingHistoryDO> {}
