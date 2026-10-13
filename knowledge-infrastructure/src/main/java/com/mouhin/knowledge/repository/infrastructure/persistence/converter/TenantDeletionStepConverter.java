package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.mouhin.knowledge.repository.domain.model.entity.TenantDeletionStep;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantDeletionStepDO;

/**
 * {@link TenantDeletionStep} ⇄ {@link TenantDeletionStepDO} 转换（P3-T5）。
 *
 * <p>infrastructure 边界静态转换，null-safe，字段一一映射；镜像 {@code TenantProvisionStepConverter}。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
public final class TenantDeletionStepConverter {

    private TenantDeletionStepConverter() {}

    /** DO → domain entity；{@code null} 输入返回 {@code null}。 */
    public static TenantDeletionStep toDomain(TenantDeletionStepDO source) {
        if (source == null) {
            return null;
        }
        TenantDeletionStep target = new TenantDeletionStep();
        target.setStepId(source.getStepId());
        target.setTenantId(source.getTenantId());
        target.setDeletionRequestId(source.getDeletionRequestId());
        target.setStepName(source.getStepName());
        target.setAttemptNo(source.getAttemptNo());
        target.setStatus(source.getStatus());
        target.setRowsAffected(source.getRowsAffected());
        target.setErrorDetail(source.getErrorDetail());
        target.setStartedAt(source.getStartedAt());
        target.setFinishedAt(source.getFinishedAt());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        return target;
    }

    /** domain entity → DO；{@code null} 输入返回 {@code null}。 */
    public static TenantDeletionStepDO toDO(TenantDeletionStep source) {
        if (source == null) {
            return null;
        }
        TenantDeletionStepDO target = new TenantDeletionStepDO();
        target.setStepId(source.getStepId());
        target.setTenantId(source.getTenantId());
        target.setDeletionRequestId(source.getDeletionRequestId());
        target.setStepName(source.getStepName());
        target.setAttemptNo(source.getAttemptNo());
        target.setStatus(source.getStatus());
        target.setRowsAffected(source.getRowsAffected());
        target.setErrorDetail(source.getErrorDetail());
        target.setStartedAt(source.getStartedAt());
        target.setFinishedAt(source.getFinishedAt());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        return target;
    }
}
