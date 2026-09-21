package com.mouhin.knowledge.repository.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.ExamQuestionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 结构化题目 Mapper
 *
 * @author Knowledge-Repository
 * @date 2026-09-18
 */
@Mapper
public interface ExamQuestionMapper extends BaseMapper<ExamQuestionDO> {}
