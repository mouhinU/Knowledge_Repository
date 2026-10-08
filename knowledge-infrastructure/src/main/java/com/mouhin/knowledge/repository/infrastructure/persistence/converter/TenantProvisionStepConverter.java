package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.mouhin.knowledge.repository.domain.model.entity.TenantProvisionStep;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantProvisionStepDO;

/**
 * {@link TenantProvisionStep} ⇄ {@link TenantProvisionStepDO} 转换（P3-T2）。
 *
 * <p>infrastructure 边界静态转换，null-safe，字段一一映射。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
public final class TenantProvisionStepConverter {

    private TenantProvisionStepConverter() {}

    /** DO → domain entity；{@code null} 输入返回 {@code null}。 */
    public static TenantProvisionStep toDomain(TenantProvisionStepDO source) {
        if (source == null) {
            return null;
        }
        TenantProvisionStep target = new TenantProvisionStep();
        target.setStepId(source.getStepId());
        target.setTenantId(source.getTenantId());
        target.setProvisionRequestId(source.getProvisionRequestId());
        target.setStepName(source.getStepName());
        target.setAttemptNo(source.getAttemptNo());
        target.setStatus(source.getStatus());
        target.setErrorDetail(source.getErrorDetail());
        target.setStartedAt(source.getStartedAt());
        target.setFinishedAt(source.getFinishedAt());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        return target;
    }

    /** domain entity → DO；{@code null} 输入返回 {@code null}。 */
    public static TenantProvisionStepDO toDO(TenantProvisionStep source) {
        if (source == null) {
            return null;
        }
        TenantProvisionStepDO target = new TenantProvisionStepDO();
        target.setStepId(source.getStepId());
        target.setTenantId(source.getTenantId());
        target.setProvisionRequestId(source.getProvisionRequestId());
        target.setStepName(source.getStepName());
        target.setAttemptNo(source.getAttemptNo());
        target.setStatus(source.getStatus());
        target.setErrorDetail(source.getErrorDetail());
        target.setStartedAt(source.getStartedAt());
        target.setFinishedAt(source.getFinishedAt());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        return target;
    }
}
