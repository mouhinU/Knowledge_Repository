package com.mouhin.knowledge.repository.domain.model.valueobject;

import java.time.Instant;
import java.util.List;

/**
 * 管理端令牌载荷（domain 值对象）。
 *
 * <p>由 {@code AdminJwtService} 签发时写入、校验成功后还原，承载一次登录的管理者身份： 用户唯一标识、用户名、是否超管、角色 key 列表、以及过期时刻。P0-T2
 * 起附带 {@code roles}（{@code sys_user_role → sys_role.role_key}）， 供 {@code
 * AdminPrincipalSupport.toPermission} 回读真实角色，替代此前的恒 null 兜底。纯 JDK 类型，不依赖任何基础设施。
 *
 * @param userKey 用户唯一标识（UUID）
 * @param username 用户名
 * @param admin 是否超级管理员
 * @param roles 用户角色 key 列表；{@code null} 归一为空列表
 * @param expiresAt 令牌过期时刻
 * @author mouhinU
 * @date 2026-09-19
 */
public record AdminTokenPayload(
        String userKey, String username, boolean admin, List<String> roles, Instant expiresAt) {

    public AdminTokenPayload {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    /** 兼容旧签名：不带 roles 的载荷按空列表处理。 */
    public AdminTokenPayload(String userKey, String username, boolean admin, Instant expiresAt) {
        this(userKey, username, admin, List.of(), expiresAt);
    }
}
