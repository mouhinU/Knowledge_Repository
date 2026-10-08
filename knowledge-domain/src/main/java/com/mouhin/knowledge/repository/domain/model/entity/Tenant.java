package com.mouhin.knowledge.repository.domain.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;

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
public class Tenant {

    /** 状态：已创建但尚未开通（P3-T2 起点）。 */
    public static final String STATUS_PENDING = "PENDING";

    /** 状态：开通中（步骤逐条落 {@code sys_tenant_provision_step}）。 */
    public static final String STATUS_PROVISIONING = "PROVISIONING";

    /** 状态：正常。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 状态：已软停（租户侧请求返 403 TENANT_SUSPENDED，平台侧仍可管理）。 */
    public static final String STATUS_SUSPENDED = "SUSPENDED";

    /** 状态：已归档（保留期内可恢复）。 */
    public static final String STATUS_ARCHIVED = "ARCHIVED";

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

    // ==================== Getters & Setters ====================

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantKey() {
        return tenantKey;
    }

    public void setTenantKey(String tenantKey) {
        this.tenantKey = tenantKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDataRegion() {
        return dataRegion;
    }

    public void setDataRegion(String dataRegion) {
        this.dataRegion = dataRegion;
    }

    public String getProvisionRequestId() {
        return provisionRequestId;
    }

    public void setProvisionRequestId(String provisionRequestId) {
        this.provisionRequestId = provisionRequestId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
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
