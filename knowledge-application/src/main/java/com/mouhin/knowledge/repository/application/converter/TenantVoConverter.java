package com.mouhin.knowledge.repository.application.converter;

import com.mouhin.knowledge.repository.client.dto.TenantVO;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;

/**
 * 租户 领域实体 → 视图对象 转换器（app 层，P3-T1）。
 *
 * <p>字段一一直映，无业务归一；与 infra 层 {@code TenantConverter}（Entity ⇄ DO）分属不同层边界， 遵循 COLA 红线 #6（转换只在层边界发生）。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
public final class TenantVoConverter {

    private TenantVoConverter() {}

    /** 领域实体 → VO；入参为 {@code null} 时返回 {@code null}。 */
    public static TenantVO toVO(Tenant tenant) {
        if (tenant == null) {
            return null;
        }
        TenantVO vo = new TenantVO();
        vo.setTenantId(tenant.getTenantId());
        vo.setTenantKey(tenant.getTenantKey());
        vo.setName(tenant.getName());
        vo.setPlan(tenant.getPlan());
        vo.setStatus(tenant.getStatus());
        vo.setProvisionRequestId(tenant.getProvisionRequestId());
        vo.setDeletionRequestId(tenant.getDeletionRequestId());
        vo.setArchivedAt(tenant.getArchivedAt());
        vo.setDeletedAt(tenant.getDeletedAt());
        vo.setDataRegion(tenant.getDataRegion());
        vo.setCreateTime(tenant.getCreateTime());
        vo.setUpdateTime(tenant.getUpdateTime());
        return vo;
    }
}
