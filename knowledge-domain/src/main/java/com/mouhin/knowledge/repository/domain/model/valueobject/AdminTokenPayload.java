package com.mouhin.knowledge.repository.domain.model.valueobject;

import java.time.Instant;

/**
 * 管理端令牌载荷（domain 值对象）。
 * <p>
 * 由 {@code AdminJwtService} 签发时写入、校验成功后还原，承载一次登录的管理者身份：
 * 用户唯一标识、用户名、是否超管、以及过期时刻。纯 JDK 类型，不依赖任何基础设施。
 * </p>
 *
 * @param userKey  用户唯一标识（UUID）
 * @param username 用户名
 * @param admin    是否超级管理员
 * @param expiresAt 令牌过期时刻
 * @author Knowledge-Repository
 * @date 2026-09-19
 */
public record AdminTokenPayload(String userKey, String username, boolean admin, Instant expiresAt) {
}
