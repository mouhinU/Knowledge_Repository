package com.mouhin.knowledge.repository.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantDeletionStepDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户删除步骤 Mapper（P3-T5）。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Mapper
public interface TenantDeletionStepMapper extends BaseMapper<TenantDeletionStepDO> {
}
