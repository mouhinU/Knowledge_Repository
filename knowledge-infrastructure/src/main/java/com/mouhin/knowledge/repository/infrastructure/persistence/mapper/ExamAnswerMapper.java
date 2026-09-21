package com.mouhin.knowledge.repository.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.ExamAnswerDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 答题记录 Mapper
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@Mapper
public interface ExamAnswerMapper extends BaseMapper<ExamAnswerDO> {}
