package com.mouhin.knowledge.repository.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.ExamHistoryDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 出卷历史 Mapper
 *
 * @author mouhinU
 * @date 2026-09-14
 */
@Mapper
public interface ExamHistoryMapper extends BaseMapper<ExamHistoryDO> {}
