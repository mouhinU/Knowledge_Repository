package com.mouhin.knowledge.repository.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantProvisionStepDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户开通步骤 Mapper（P3-T2）。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Mapper
public interface TenantProvisionStepMapper extends BaseMapper<TenantProvisionStepDO> {}
