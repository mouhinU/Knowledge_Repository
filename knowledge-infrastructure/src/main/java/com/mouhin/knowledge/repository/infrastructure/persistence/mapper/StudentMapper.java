package com.mouhin.knowledge.repository.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.StudentDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 考生 Mapper
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@Mapper
public interface StudentMapper extends BaseMapper<StudentDO> {
}
