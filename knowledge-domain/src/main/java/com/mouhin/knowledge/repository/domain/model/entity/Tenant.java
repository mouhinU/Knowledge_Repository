package com.mouhin.knowledge.repository.domain.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * 租户领域实体（P3-T1，SaaS 平台端生命周期管理的聚合根）。
 *
 * <p>映射平台级主表 {@code sys_tenant}，承载一个租户的业务身份（{@code tenantKey}）、套餐（{@code plan}）、 数据驻留区（{@code
 * dataRegion}）与生命周期状态（{@code status}）。本实体位于 domain 层，零框架依赖，状态流转的合法性判定内聚于此， 供 app 层 Executor
 * 调用；持久化映射（{@code Entity ⇄ DO}）由 infrastructure 层的 {@code TenantConverter} 承担。
 *
 * <p><b>状态口径</b>与 {@code sys_tenant.status} 列取值一致：{@link #STATUS_PENDING}（已创建待开通）、{@link
 * #STATUS_PROVISIONING}（开通中）、{@link #STATUS_ACTIVE}（正常）、{@link #STATUS_SUSPENDED}（软停，租户请求返 403，见
 * P3-T4）、{@link #STATUS_ARCHIVED}（归档，保留期内可恢复）、{@link #STATUS_DELETED}（终态）、{@link
 * #STATUS_FAILED}（开通失败，可查询 {@code sys_tenant_provision_step.error_detail} 后重放）。
 *
 * <p><b>生命周期</b>（P3-T2 状态机）：{@code create → PENDING → beginProvisioning → PROVISIONING →
 * completeProvisioning → ACTIVE}；任一步失败则 {@code failProvisioning → FAILED}，可携新 {@code
 * provisionRequestId} 或原 id + attempt_no+1 重放。软停 / 恢复走 {@code ACTIVE ⇄ SUSPENDED}。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Getter
@Setter
public class Tenant {

    /** 状态：已创建但尚未开通（P3-T2 起点）。 */
    public static final String STATUS_PENDING = "PENDING";

    /** 状态：开通中（步骤逐条落 {@code sys_tenant_provision_step}）。 */
    public static final String STATUS_PROVISIONING = "PROVISIONING";

    /** 状态：正常。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 状态：已软停（租户侧请求返 403 TENANT_SUSPENDED，平台侧仍可管理）。 */
    public static final String STATUS_SUSPENDED = "SUSPENDED";

    /** 状态：已归档（保留期内可恢复；S-4 保留期起点见 {@link #archivedAt}）。 */
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 状态：删除中（ARCHIVED 过保留期 + 双人复核通过后由 reconciler 认领，硬删进行时的中间态）。 */
    public static final String STATUS_DELETING = "DELETING";

    /** 状态：已删除（终态）。 */
    public static final String STATUS_DELETED = "DELETED";

    /** 状态：开通失败（可通过 {@code provisionRequestId} + attempt_no+1 重放）。 */
    public static final String STATUS_FAILED = "FAILED";

    private Long tenantId;

    /** 租户业务键（全局唯一，小写，用于子域名 / 集合命名 / 令牌 claim）。 */
    private String tenantKey;

    /** 租户展示名。 */
    private String name;

    /** 套餐标识（如 FREE / STANDARD / ENTERPRISE）。 */
    private String plan;

    /** 生命周期状态，取值见本类 {@code STATUS_*} 常量。 */
    private String status;

    /** 数据驻留区（如 cn-hangzhou），用于 SILO placement 决策（P3-T6）。 */
    private String dataRegion;

    /**
     * 当前 provision 请求的 UUID v4（S-5=方案 A 幂等键）。 首次 create 由 {@code TenantCreateCmdExe} 生成并落库；重放时
     * {@code TenantProvisionCmdExe} 若接收调用方传入的 id 则复用之，否则续用库中 id。
     */
    private String provisionRequestId;

    /**
     * 当前删除请求的 UUID v4（删除幂等键）。 由 {@code ConfirmTenantDeletionCmdExe} 在双人复核通过、迁入 DELETING 时生成并落库；
     * reconciler 续跑同一请求时复用之，与 {@code sys_tenant_deletion_step} 的四元组唯一键配合实现步骤级幂等。
     */
    private String deletionRequestId;

    /** 归档时间（S-4=方案 A 的 30 天保留期起点）；非归档态为 {@code null}。 */
    private LocalDateTime archivedAt;

    /** 硬删完成时间（DELETED 终态戳）；未完成为 {@code null}。 */
    private LocalDateTime deletedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== 业务方法 ====================

    /** 是否为待开通态。 */
    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    /** 是否为开通中态。 */
    public boolean isProvisioning() {
        return STATUS_PROVISIONING.equals(status);
    }

    /** 是否为正常态。 */
    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    /** 是否为软停态。 */
    public boolean isSuspended() {
        return STATUS_SUSPENDED.equals(status);
    }

    /** 是否为开通失败态。 */
    public boolean isFailed() {
        return STATUS_FAILED.equals(status);
    }

    /**
     * 进入开通中：允许 {@code PENDING → PROVISIONING} 或 {@code FAILED → PROVISIONING}（重放）； 也允许 {@code
     * PROVISIONING → PROVISIONING}（同请求进程内恢复，视为幂等）。
     *
     * @param requestId 本次开通请求 UUID v4；不可为 {@code null} 或空白
     * @throws IllegalStateException 当前状态是 ACTIVE / SUSPENDED / ARCHIVED / DELETED
     * @throws IllegalArgumentException {@code requestId} 为空
     */
    public void beginProvisioning(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("provisionRequestId must not be blank");
        }
        boolean allowed =
                STATUS_PENDING.equals(status)
                        || STATUS_PROVISIONING.equals(status)
                        || STATUS_FAILED.equals(status);
        if (!allowed) {
            throw new IllegalStateException(
                    "Cannot begin provisioning for tenant '"
                            + tenantKey
                            + "' from status="
                            + status);
        }
        this.status = STATUS_PROVISIONING;
        this.provisionRequestId = requestId;
    }

    /**
     * 开通成功：{@code PROVISIONING → ACTIVE}。 只有 {@link #beginProvisioning(String)} 之后才允许调用。
     *
     * @throws IllegalStateException 当前状态不是 PROVISIONING
     */
    public void completeProvisioning() {
        if (!STATUS_PROVISIONING.equals(status)) {
            throw new IllegalStateException(
                    "Cannot complete provisioning for tenant '"
                            + tenantKey
                            + "' from status="
                            + status);
        }
        this.status = STATUS_ACTIVE;
    }

    /**
     * 开通失败：{@code PROVISIONING → FAILED}；保留 {@link #provisionRequestId} 供重放。
     *
     * @throws IllegalStateException 当前状态不是 PROVISIONING
     */
    public void failProvisioning() {
        if (!STATUS_PROVISIONING.equals(status)) {
            throw new IllegalStateException(
                    "Cannot fail provisioning for tenant '"
                            + tenantKey
                            + "' from status="
                            + status);
        }
        this.status = STATUS_FAILED;
    }

    /**
     * 软停：仅允许从 ACTIVE 迁入 SUSPENDED。
     *
     * @throws IllegalStateException 当前状态不是 ACTIVE（含已归档 / 已删除 / 已停）
     */
    public void suspend() {
        if (!STATUS_ACTIVE.equals(status)) {
            throw new IllegalStateException(
                    "Cannot suspend tenant '" + tenantKey + "' from status=" + status);
        }
        this.status = STATUS_SUSPENDED;
    }

    /**
     * 恢复：仅允许从 SUSPENDED 迁回 ACTIVE。
     *
     * @throws IllegalStateException 当前状态不是 SUSPENDED
     */
    public void resume() {
        if (!STATUS_SUSPENDED.equals(status)) {
            throw new IllegalStateException(
                    "Cannot resume tenant '" + tenantKey + "' from status=" + status);
        }
        this.status = STATUS_ACTIVE;
    }

    /** 是否为归档态。 */
    public boolean isArchived() {
        return STATUS_ARCHIVED.equals(status);
    }

    /** 是否为删除中态。 */
    public boolean isDeleting() {
        return STATUS_DELETING.equals(status);
    }

    /** 是否为已删除终态。 */
    public boolean isDeleted() {
        return STATUS_DELETED.equals(status);
    }

    /**
     * 归档：仅允许从 ACTIVE 或 SUSPENDED 迁入 ARCHIVED，并打 {@link #archivedAt} 保留期起点戳（S-4=方案 A）。
     *
     * <p>归档是软删——保留期内数据仍在，可经 {@link #restore()} 恢复；过保留期 + 双人复核后才由 reconciler 硬删。
     *
     * @param archivedAt 归档时间戳；不可为 {@code null}
     * @throws IllegalStateException 当前状态不是 ACTIVE / SUSPENDED
     * @throws IllegalArgumentException {@code archivedAt} 为 {@code null}
     */
    public void archive(LocalDateTime archivedAt) {
        if (archivedAt == null) {
            throw new IllegalArgumentException("archivedAt must not be null");
        }
        boolean allowed = STATUS_ACTIVE.equals(status) || STATUS_SUSPENDED.equals(status);
        if (!allowed) {
            throw new IllegalStateException(
                    "Cannot archive tenant '" + tenantKey + "' from status=" + status);
        }
        this.status = STATUS_ARCHIVED;
        this.archivedAt = archivedAt;
    }

    /**
     * 归档恢复：仅允许从 ARCHIVED 迁回 ACTIVE（保留期内撤销归档），并清空 {@link #archivedAt}。
     *
     * @throws IllegalStateException 当前状态不是 ARCHIVED
     */
    public void restore() {
        if (!STATUS_ARCHIVED.equals(status)) {
            throw new IllegalStateException(
                    "Cannot restore tenant '" + tenantKey + "' from status=" + status);
        }
        this.status = STATUS_ACTIVE;
        this.archivedAt = null;
    }

    /**
     * 认领删除：仅允许从 ARCHIVED 迁入 DELETING，并绑定删除请求幂等键 {@link #deletionRequestId}。
     *
     * <p>调用方（{@code ConfirmTenantDeletionCmdExe}）须先校验保留期已过 + 双人复核令牌有效；本方法只负责状态机合法性。
     *
     * @param requestId 本次删除请求 UUID v4；不可为 {@code null} 或空白
     * @throws IllegalStateException 当前状态不是 ARCHIVED
     * @throws IllegalArgumentException {@code requestId} 为空
     */
    public void markDeleting(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("deletionRequestId must not be blank");
        }
        if (!STATUS_ARCHIVED.equals(status)) {
            throw new IllegalStateException(
                    "Cannot begin deletion for tenant '" + tenantKey + "' from status=" + status);
        }
        this.status = STATUS_DELETING;
        this.deletionRequestId = requestId;
    }

    /**
     * 删除完成：{@code DELETING → DELETED}（终态），并打 {@link #deletedAt} 戳。
     *
     * @param deletedAt 硬删完成时间戳；不可为 {@code null}
     * @throws IllegalStateException 当前状态不是 DELETING
     * @throws IllegalArgumentException {@code deletedAt} 为 {@code null}
     */
    public void markDeleted(LocalDateTime deletedAt) {
        if (deletedAt == null) {
            throw new IllegalArgumentException("deletedAt must not be null");
        }
        if (!STATUS_DELETING.equals(status)) {
            throw new IllegalStateException(
                    "Cannot complete deletion for tenant '"
                            + tenantKey
                            + "' from status="
                            + status);
        }
        this.status = STATUS_DELETED;
        this.deletedAt = deletedAt;
    }

    // ==================== equals / hashCode / toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Tenant that = (Tenant) o;
        return Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(tenantId);
    }

    @Override
    public String toString() {
        return "Tenant{"
                + "tenantId="
                + tenantId
                + ", tenantKey='"
                + tenantKey
                + '\''
                + ", status='"
                + status
                + '\''
                + ", plan='"
                + plan
                + '\''
                + '}';
    }
}
