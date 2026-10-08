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
 * <p><b>状态口径</b>与 {@code sys_tenant.status} 列取值一致：{@link #STATUS_ACTIVE}（正常）、{@link
 * #STATUS_SUSPENDED}（软停，租户请求返 403，见 P3-T4）、{@link #STATUS_ARCHIVED}（归档，保留期内可恢复）、{@link
 * #STATUS_DELETED}（终态）。P3-T1 交付的解耦子集只做「创建即 ACTIVE + 软停 / 恢复」的直接流转；PENDING → PROVISIONING →
 * ACTIVE/FAILED 的开租户编排状态机由 P3-T2 在本实体之上叠加。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
public class Tenant {

    /** 状态：正常。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 状态：已软停（租户侧请求返 403 TENANT_SUSPENDED，平台侧仍可管理）。 */
    public static final String STATUS_SUSPENDED = "SUSPENDED";

    /** 状态：已归档（保留期内可恢复）。 */
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 状态：已删除（终态）。 */
    public static final String STATUS_DELETED = "DELETED";

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

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== 业务方法 ====================

    /** 是否为正常态。 */
    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    /** 是否为软停态。 */
    public boolean isSuspended() {
        return STATUS_SUSPENDED.equals(status);
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
