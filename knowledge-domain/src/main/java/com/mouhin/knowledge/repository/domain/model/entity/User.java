package com.mouhin.knowledge.repository.domain.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户实体
 *
 * @author mouhinU
 * @date 2026-09-02
 */
@Getter
@Setter
public class User {

    /** 有效账号状态 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 禁用账号状态 */
    public static final String STATUS_DISABLED = "DISABLED";

    private Long id;

    /** 用户唯一标识（UUID） */
    private String userKey;

    /** 用户名 */
    private String username;

    /** 所属部门 ID */
    private String departmentId;

    /**
     * 所属租户 key（P1-T8 SaaS 租户脊柱）。对应 {@code UserDO.tenant_id} (BIGINT) 转换后的字符串形式， 由 infrastructure 层
     * {@code UserGatewayImpl} 借 {@code TenantIdResolver.lookupKeyOrDefault} 还原； 若 DB 行 tenant_id
     * 缺失/漂移，回退为 {@code default}。app 层登录用例在签发 JWT 时直接读取本字段， 避免每次登录回库查 sys_tenant。
     */
    private String tenantKey;

    /** 是否为超级管理员 */
    private Boolean admin;

    /** 登录密码 BCrypt 哈希（$2a）。为 null 表示该账号尚未设置密码、不可通过密码登录。 */
    private String passwordHash;

    /**
     * 首次登录是否必须改密（P3-T3 邀请式引导）。
     *
     * <p>由 {@code CreateFirstAdminStep} 建首位管理员时置 {@code true}，{@code SetPasswordByInviteCmdExe}
     * 完成设密后置 {@code false}。UI / 登录链路可据此强制跳转改密页。默认 {@code false}， 与 V28 迁移 {@code
     * must_change_password TINYINT(1) NOT NULL DEFAULT 0} 对齐。
     */
    private Boolean mustChangePassword;

    /** 账号状态：ACTIVE / DISABLED。禁用账号登录与被拦截的令牌校验均实时读此字段。 */
    private String status;

    // ==================== 状态常量 ====================
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    /**
     * 账号是否处于激活状态（大小写不敏感，null 视为非激活）。
     *
     * @return status 等于 ACTIVE 时返回 true
     */
    public boolean isActive() {
        return STATUS_ACTIVE.equalsIgnoreCase(this.status);
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
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "User{"
                + "id="
                + id
                + ", userKey='"
                + userKey
                + '\''
                + ", username='"
                + username
                + '\''
                + ", departmentId='"
                + departmentId
                + '\''
                + ", admin="
                + admin
                + '}';
    }
}
