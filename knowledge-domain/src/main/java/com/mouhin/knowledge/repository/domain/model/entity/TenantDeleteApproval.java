package com.mouhin.knowledge.repository.domain.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 租户删除审批令牌（P3-T5，映射 {@code sys_tenant_delete_approval}，S-4=方案 A 双人复核载体）。
 *
 * <p>镜像 {@code UserInvite} 的一次性码模式：项目负责人（签发者）为某租户签发一条 PENDING 审批，落库只存 {@code SHA-256(rawToken)} 的
 * {@code tokenHash}，raw token 仅在签发响应体一次性返回、不入库、不入日志。 第二操作者持 raw token 确认删除时按 {@code tokenHash}
 * 精确查，校验 {@link #isUsable(LocalDateTime)}（PENDING 且未过期）， 消费后 {@link #markUsed(LocalDateTime,
 * String)} 置 USED——一人签发、另一人确认，达成双人复核。
 *
 * <p>本实体位于 domain 层，纯字段承载 + 状态判定；令牌生成 / 哈希在 app 层 {@code DeleteApprovalService}，持久化在 infra 层。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
public class TenantDeleteApproval {

    /** 状态：已签发待确认。 */
    public static final String STATUS_PENDING = "PENDING";

    /** 状态：已消费（确认删除时置位）。 */
    public static final String STATUS_USED = "USED";

    /** 状态：已过期（超 {@code expiresAt} 未消费）。 */
    public static final String STATUS_EXPIRED = "EXPIRED";

    /** 状态：已撤销（签发者主动作废或重签时吊销旧令牌）。 */
    public static final String STATUS_REVOKED = "REVOKED";

    private Long approvalId;

    private Long tenantId;

    /** 签发者（项目负责人）operator id。 */
    private String approverOperatorId;

    /** SHA-256(raw token) hex 64 字符；raw token 不入库。 */
    private String tokenHash;

    /** 状态，见本类 {@code STATUS_*} 常量。 */
    private String status;

    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;

    /** 确认者（第二操作者）operator id；消费时回填。 */
    private String usedByOperatorId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 是否可用（可被第二操作者消费确认删除）：状态为 PENDING 且未过期。
     *
     * @param now 判定基准时间
     * @return 可用返回 {@code true}
     */
    public boolean isUsable(LocalDateTime now) {
        return STATUS_PENDING.equals(status)
                && expiresAt != null
                && (now == null || expiresAt.isAfter(now));
    }

    /** 是否已过期（PENDING 但 {@code expiresAt} 已过）。 */
    public boolean isExpired(LocalDateTime now) {
        return STATUS_PENDING.equals(status) && expiresAt != null && !expiresAt.isAfter(now);
    }

    /**
     * 消费令牌：置 USED + 回填 {@code usedAt} / {@code usedByOperatorId}。
     *
     * @param usedAt 消费时间
     * @param usedByOperatorId 确认者 operator id
     */
    public void markUsed(LocalDateTime usedAt, String usedByOperatorId) {
        this.status = STATUS_USED;
        this.usedAt = usedAt;
        this.usedByOperatorId = usedByOperatorId;
    }

    /** 撤销令牌：置 REVOKED（重签前吊销旧令牌 / 签发者主动作废）。 */
    public void markRevoked(LocalDateTime revokedAt) {
        this.status = STATUS_REVOKED;
        this.updateTime = revokedAt;
    }

    /** 标记过期：置 EXPIRED（懒过期，被查询命中且已超期时回写）。 */
    public void markExpired() {
        this.status = STATUS_EXPIRED;
    }

    public Long getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(Long approvalId) {
        this.approvalId = approvalId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getApproverOperatorId() {
        return approverOperatorId;
    }

    public void setApproverOperatorId(String approverOperatorId) {
        this.approverOperatorId = approverOperatorId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }

    public String getUsedByOperatorId() {
        return usedByOperatorId;
    }

    public void setUsedByOperatorId(String usedByOperatorId) {
        this.usedByOperatorId = usedByOperatorId;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TenantDeleteApproval that = (TenantDeleteApproval) o;
        return Objects.equals(approvalId, that.approvalId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(approvalId);
    }

    @Override
    public String toString() {
        return "TenantDeleteApproval{"
                + "approvalId="
                + approvalId
                + ", tenantId="
                + tenantId
                + ", status='"
                + status
                + '\''
                + ", approverOperatorId='"
                + approverOperatorId
                + '\''
                + ", expiresAt="
                + expiresAt
                + '}';
    }
}
