package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantDO;

/**
 * 租户 领域实体 ⇄ 数据对象 转换器（infrastructure 层，COLA 红线 #6 的层边界）。
 *
 * <p>字段一一直映，不做业务归一（{@code tenantKey} 的小写归一在 {@code TenantGatewayImpl} 查询入口与 {@code
 * TenantIdResolver} 各自处理，转换器保持无副作用的纯映射，便于单测）。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
public final class TenantConverter {

    private TenantConverter() {}

    /** DO → 领域实体；入参为 {@code null} 时返回 {@code null}。 */
    public static Tenant toDomain(TenantDO doObj) {
        if (doObj == null) {
            return null;
        }
        Tenant tenant = new Tenant();
        tenant.setTenantId(doObj.getTenantId());
        tenant.setTenantKey(doObj.getTenantKey());
        tenant.setName(doObj.getName());
        tenant.setPlan(doObj.getPlan());
        tenant.setStatus(doObj.getStatus());
        tenant.setDataRegion(doObj.getDataRegion());
        tenant.setProvisionRequestId(doObj.getProvisionRequestId());
        tenant.setDeletionRequestId(doObj.getDeletionRequestId());
        tenant.setArchivedAt(doObj.getArchivedAt());
        tenant.setDeletedAt(doObj.getDeletedAt());
        tenant.setCreateTime(doObj.getCreateTime());
        tenant.setUpdateTime(doObj.getUpdateTime());
        return tenant;
    }

    /** 领域实体 → DO；入参为 {@code null} 时返回 {@code null}。 */
    public static TenantDO toDO(Tenant tenant) {
        if (tenant == null) {
            return null;
        }
        TenantDO doObj = new TenantDO();
        doObj.setTenantId(tenant.getTenantId());
        doObj.setTenantKey(tenant.getTenantKey());
        doObj.setName(tenant.getName());
        doObj.setPlan(tenant.getPlan());
        doObj.setStatus(tenant.getStatus());
        doObj.setDataRegion(tenant.getDataRegion());
        doObj.setProvisionRequestId(tenant.getProvisionRequestId());
        doObj.setDeletionRequestId(tenant.getDeletionRequestId());
        doObj.setArchivedAt(tenant.getArchivedAt());
        doObj.setDeletedAt(tenant.getDeletedAt());
        doObj.setCreateTime(tenant.getCreateTime());
        doObj.setUpdateTime(tenant.getUpdateTime());
        return doObj;
    }
}
