package com.mouhin.knowledge.repository.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.DepartmentDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 部门 Mapper
 *
 * @author mouhinU
 * @date 2026-09-02
 */
@Mapper
public interface DepartmentMapper extends BaseMapper<DepartmentDO> {}
