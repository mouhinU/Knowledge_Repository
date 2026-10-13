package com.mouhin.knowledge.repository.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.StudentSessionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 考生会话 Mapper（P0-T5）
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@Mapper
public interface StudentSessionMapper extends BaseMapper<StudentSessionDO> {}
