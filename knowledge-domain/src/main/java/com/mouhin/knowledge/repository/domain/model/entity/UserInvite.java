package com.mouhin.knowledge.repository.domain.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户邀请实体（P3-T3，一次性设密邀请）。
 *
 * <p>承载"平台运维为新租户首位管理员签发一次性设密链接"的完整生命周期：PENDING → USED / EXPIRED / REVOKED。 与 {@code
 * sys_user_invite} 表一一对应；<b>不</b>持有原始 code——仅持有其 SHA-256 hash， 原始 code 由 {@code UserInviteService}
 * 生成后仅在 provision 响应体一次性回传，绝不落库、绝不入日志。
 *
 * <p><b>安全契约</b>：
 *
 * <ul>
 *   <li>{@link #isUsable(LocalDateTime)} 判定消费前置：状态 PENDING 且未过期。
 *   <li>{@link #markUsed(LocalDateTime)} 幂等：已 USED 再调用为无副作用。
 *   <li>{@link #markRevoked(LocalDateTime)} 用于"平台运维主动吊销"或"同一用户重复签发时清理旧邀请"。
 *   <li>过期不主动置 EXPIRED——由 {@link #isUsable} 惰性判定；批量清理留给运维脚本或后台任务。
 * </ul>
 *
 * <p><b>身份等价</b>：以 {@link #inviteId} 为准；未落库时（{@code inviteId==null}）走引用相等。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Getter
@Setter
public class UserInvite {

    /** 状态：待使用。 */
    public static final String STATUS_PENDING = "PENDING";

    /** 状态：已被设密消费。 */
    public static final String STATUS_USED = "USED";

    /** 状态：已过期（惰性判定，不主动写库）。 */
    public static final String STATUS_EXPIRED = "EXPIRED";

    /** 状态：已吊销（重复签发 / 运维主动收回）。 */
    public static final String STATUS_REVOKED = "REVOKED";

    private Long inviteId;

    /** 被邀请加入的租户 id（业务列，与 sys_tenant_provision_step 同性质）。 */
    private Long tenantId;

    /** 目标 sys_user.id。 */
    private Long userId;

    /** SHA-256(raw code) 的 hex 字符串（64 字符）；raw code 不入库。 */
    private String inviteCodeHash;

    /** 生命周期状态，取值见 {@code STATUS_*} 常量。 */
    private String status;

    /** 过期时间（默认签发 +72h）。 */
    private LocalDateTime expiresAt;

    /** 消费时间（{@link #markUsed} 落值）。 */
    private LocalDateTime usedAt;

    /** 吊销时间（{@link #markRevoked} 落值）。 */
    private LocalDateTime revokedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== 业务方法 ====================

    /**
     * 是否可被消费：状态 PENDING 且当前时间早于 {@link #expiresAt}。
     *
     * @param now 判定基准时间（不直接取系统时钟，方便单测注入）
     * @return 可用返回 true
     */
    public boolean isUsable(LocalDateTime now) {
        if (!STATUS_PENDING.equals(status)) {
            return false;
        }
        if (expiresAt == null) {
            return false;
        }
        return now.isBefore(expiresAt);
    }

    /** 是否已过期（仅按时间判定，不看状态）。 */
    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    /**
     * 标记为已使用（幂等）。已 USED/REVOKED/EXPIRED 的邀请再调用为无副作用。
     *
     * @param now 消费时间
     */
    public void markUsed(LocalDateTime now) {
        if (!STATUS_PENDING.equals(status)) {
            return;
        }
        this.status = STATUS_USED;
        this.usedAt = now;
    }

    /**
     * 标记为已吊销（幂等）。已 USED 的邀请不允许再吊销（业务上已消费完毕，无需再撤）。
     *
     * @param now 吊销时间
     */
    public void markRevoked(LocalDateTime now) {
        if (STATUS_USED.equals(status) || STATUS_REVOKED.equals(status)) {
            return;
        }
        this.status = STATUS_REVOKED;
        this.revokedAt = now;
    }

    // ==================== equals / hashCode / toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserInvite that)) {
            return false;
        }
        return Objects.equals(inviteId, that.inviteId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(inviteId);
    }

    /** 不含 inviteCodeHash，避免误落日志。 */
    @Override
    public String toString() {
        return "UserInvite{inviteId="
                + inviteId
                + ", tenantId="
                + tenantId
                + ", userId="
                + userId
                + ", status='"
                + status
                + '\''
                + ", expiresAt="
                + expiresAt
                + '}';
    }
}
