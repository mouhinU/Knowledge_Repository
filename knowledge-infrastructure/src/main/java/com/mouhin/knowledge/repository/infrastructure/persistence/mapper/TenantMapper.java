package com.mouhin.knowledge.repository.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户主表 Mapper（P1-T7，SaaS 租户脊柱）。
 *
 * <p>纯 {@link BaseMapper} 即可满足 key→id 解析；平台表 sys_tenant 本身在 {@code
 * KnowledgeTenantLineHandler#IGNORE_TABLES} 中被跳过，不会被自动注入 {@code WHERE tenant_id=?}。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@Mapper
public interface TenantMapper extends BaseMapper<TenantDO> {}
