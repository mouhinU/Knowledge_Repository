package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.mouhin.knowledge.repository.domain.model.entity.TenantDeleteApproval;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantDeleteApprovalDO;

/**
 * {@link TenantDeleteApproval} ⇄ {@link TenantDeleteApprovalDO} 转换（P3-T5）。
 *
 * <p>infrastructure 边界静态转换，null-safe，字段一一映射。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
public final class TenantDeleteApprovalConverter {

    private TenantDeleteApprovalConverter() {
    }

    /**
     * DO → domain entity；{@code null} 输入返回 {@code null}。
     */
    public static TenantDeleteApproval toDomain(TenantDeleteApprovalDO source) {
        if (source == null) {
            return null;
        }
        TenantDeleteApproval target = new TenantDeleteApproval();
        target.setApprovalId(source.getApprovalId());
        target.setTenantId(source.getTenantId());
        target.setApproverOperatorId(source.getApproverOperatorId());
        target.setTokenHash(source.getTokenHash());
        target.setStatus(source.getStatus());
        target.setExpiresAt(source.getExpiresAt());
        target.setUsedAt(source.getUsedAt());
        target.setUsedByOperatorId(source.getUsedByOperatorId());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        return target;
    }

    /**
     * domain entity → DO；{@code null} 输入返回 {@code null}。
     */
    public static TenantDeleteApprovalDO toDO(TenantDeleteApproval source) {
        if (source == null) {
            return null;
        }
        TenantDeleteApprovalDO target = new TenantDeleteApprovalDO();
        target.setApprovalId(source.getApprovalId());
        target.setTenantId(source.getTenantId());
        target.setApproverOperatorId(source.getApproverOperatorId());
        target.setTokenHash(source.getTokenHash());
        target.setStatus(source.getStatus());
        target.setExpiresAt(source.getExpiresAt());
        target.setUsedAt(source.getUsedAt());
        target.setUsedByOperatorId(source.getUsedByOperatorId());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        return target;
    }
}
