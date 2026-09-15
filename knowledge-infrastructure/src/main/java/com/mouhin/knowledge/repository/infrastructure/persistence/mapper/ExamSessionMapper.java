package com.mouhin.knowledge.repository.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.ExamSessionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 考试场次 Mapper
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@Mapper
public interface ExamSessionMapper extends BaseMapper<ExamSessionDO> {
}
