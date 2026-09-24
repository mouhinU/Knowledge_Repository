package com.mouhin.knowledge.repository.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.SystemConfigDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统配置 Mapper
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfigDO> {
    // 全部 CRUD 继承自 BaseMapper
}
