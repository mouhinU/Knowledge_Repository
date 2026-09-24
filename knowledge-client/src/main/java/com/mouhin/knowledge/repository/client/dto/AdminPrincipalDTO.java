package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 管理端当前登录者身份（由令牌校验还原，供 /me 与过滤器使用）。
 *
 * <p>仅承载身份标识，不含密码。userKey 为令牌内可信标识，账号是否仍激活由校验方读库二次确认。
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

    /** 所属部门，由令牌校验回库读取，供下游按部门作用域检索使用；不再信任请求体传入。 */
    private String departmentId;

    public AdminPrincipalDTO() {}

    public AdminPrincipalDTO(String userKey, String username, Boolean admin) {
        this.userKey = userKey;
        this.username = username;
        this.admin = admin;
    }

    public AdminPrincipalDTO(String userKey, String username, Boolean admin, String departmentId) {
        this(userKey, username, admin);
        this.departmentId = departmentId;
    }
}
