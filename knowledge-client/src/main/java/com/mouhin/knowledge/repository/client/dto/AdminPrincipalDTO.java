package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 管理端当前登录者身份（由令牌校验还原，供 /me 与过滤器使用）。
 *
 * <p>仅承载身份标识，不含密码。userKey 为令牌内可信标识，账号是否仍激活由校验方读库二次确认。 P0-T2 起附带 {@code roles}（{@code
 * sys_role.role_key} 列表）由 {@code AdminPrincipalSupport.toPermission} 直读， 替代此前恒 null 的兜底。P1-T8 起附带
 * {@code tenantKey}，由 {@code AdminTokenAuthFilter} 校验成功后写入 request attr， 供 {@code
 * TenantResolverFilter} 在 {@code saas.enabled=true} 时消费。
 *
 * @author mouhinU
 * @date 2026-09-19
 */
@Getter
@Setter
public class AdminPrincipalDTO {

    private String userKey;
    private String username;
    private Boolean admin;

    /**
     * 所属部门，由令牌校验回库读取，供下游按部门作用域检索使用；不再信任请求体传入。
     */
    private String departmentId;

    /**
     * 用户角色 key 列表（P0-T2）；{@code null} 归一为空列表。
     */
    private List<String> roles;

    /**
     * 所属租户 key（P1-T8）；{@code null} 表示签发端未携带或未升级，由下游按 default 处理。
     */
    private String tenantKey;

    public AdminPrincipalDTO() {
    }

    public AdminPrincipalDTO(String userKey, String username, Boolean admin) {
        this.userKey = userKey;
        this.username = username;
        this.admin = admin;
        this.roles = List.of();
    }

    public AdminPrincipalDTO(String userKey, String username, Boolean admin, String departmentId) {
        this(userKey, username, admin);
        this.departmentId = departmentId;
    }

    public AdminPrincipalDTO(
            String userKey,
            String username,
            Boolean admin,
            String departmentId,
            List<String> roles) {
        this(userKey, username, admin, departmentId);
        this.roles = roles == null ? List.of() : List.copyOf(roles);
    }

    /**
     * P1-T8 起主用途：附带 tenantKey，供 AdminTokenAuthFilter 落到 request attr。
     */
    public AdminPrincipalDTO(
            String userKey,
            String username,
            Boolean admin,
            String departmentId,
            List<String> roles,
            String tenantKey) {
        this(userKey, username, admin, departmentId, roles);
        this.tenantKey = tenantKey;
    }
}
