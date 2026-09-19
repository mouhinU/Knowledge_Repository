package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 管理端登录结果视图对象
 *
 * <p>登录成功后返回：签发的访问令牌及令牌对应的管理者身份概览。不含密码等敏感字段。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-19
 */
@Getter
@Setter
public class AdminLoginResultDTO {

    /** 签发的访问令牌（前端存 sessionStorage，后续请求以 X-Admin-Token 头携带）。 */
    private String token;

    /** 用户唯一标识（UUID）。 */
    private String userKey;

    /** 用户名。 */
    private String username;

    /** 是否超级管理员。 */
    private Boolean admin;

    /** 令牌过期时刻（ISO-8601 字符串），供前端提示会话有效期。 */
    private String expiresAt;
}
