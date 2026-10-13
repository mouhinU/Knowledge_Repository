package com.mouhin.knowledge.repository.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantPlanLimitDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 套餐限额 MyBatis-Plus Mapper（P6-T2）。
 *
 * @author mouhinU
 * @date 2026-10-12
 */
@Mapper
public interface TenantPlanLimitMapper extends BaseMapper<TenantPlanLimitDO> {
}
