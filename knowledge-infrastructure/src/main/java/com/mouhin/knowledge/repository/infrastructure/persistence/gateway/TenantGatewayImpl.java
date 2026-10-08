package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.infrastructure.persistence.converter.TenantConverter;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.TenantMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 租户仓储实现（infrastructure 层，P3-T1 平台端生命周期管理的持久化落地）。
 *
 * <p>{@code sys_tenant} 是平台级表，已在 {@code KnowledgeTenantLineHandler#IGNORE_TABLES} 中登记， 不会被 {@code
 * TenantLineInnerInterceptor} 自动注入 {@code WHERE tenant_id=?}（否则形成「查租户表也要先知道租户」的悖论）。
 *
 * <p>查询入口对 {@code tenantKey} 统一做 {@code trim + 小写} 归一，与 {@code TenantIdResolver}、{@code
 * TenantResolverFilter} 的口径一致（库 collation 为 utf8mb4_general_ci，等价忽略大小写，此处显式归一避免依赖 collation）。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Repository
public class TenantGatewayImpl implements TenantGateway {

    private final TenantMapper tenantMapper;

    public TenantGatewayImpl(TenantMapper tenantMapper) {
        this.tenantMapper = tenantMapper;
    }

    @Override
    public Optional<Tenant> findById(Long tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(TenantConverter.toDomain(tenantMapper.selectById(tenantId)));
    }

    @Override
    public Optional<Tenant> findByTenantKey(String tenantKey) {
        String normalized = normalize(tenantKey);
        if (normalized == null) {
            return Optional.empty();
        }
        LambdaQueryWrapper<TenantDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TenantDO::getTenantKey, normalized).last("LIMIT 1");
        return Optional.ofNullable(TenantConverter.toDomain(tenantMapper.selectOne(wrapper)));
    }

    @Override
    public List<Tenant> listAll() {
        return tenantMapper.selectList(null).stream().map(TenantConverter::toDomain).toList();
    }

    @Override
    public void save(Tenant tenant) {
        java.util.Objects.requireNonNull(tenant, "Tenant must not be null on save");
        TenantDO doObj = TenantConverter.toDO(tenant);
        LocalDateTime now = LocalDateTime.now();
        doObj.setCreateTime(now);
        doObj.setUpdateTime(now);
        tenantMapper.insert(doObj);
        tenant.setTenantId(doObj.getTenantId());
        tenant.setCreateTime(doObj.getCreateTime());
        tenant.setUpdateTime(doObj.getUpdateTime());
    }

    @Override
    public void update(Tenant tenant) {
        java.util.Objects.requireNonNull(tenant, "Tenant must not be null on update");
        TenantDO doObj = TenantConverter.toDO(tenant);
        doObj.setUpdateTime(LocalDateTime.now());
        tenantMapper.updateById(doObj);
        tenant.setUpdateTime(doObj.getUpdateTime());
    }

    private static String normalize(String tenantKey) {
        if (tenantKey == null) {
            return null;
        }
        String trimmed = tenantKey.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
