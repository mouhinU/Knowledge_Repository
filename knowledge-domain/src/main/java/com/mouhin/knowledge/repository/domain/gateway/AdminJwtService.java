package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.valueobject.AdminTokenPayload;
import java.util.Optional;

/**
 * 管理端令牌网关（domain 定义，infrastructure 以 JWT 实现）。
 *
 * <p>面向接口做签发 / 校验，领域与应用层不感知令牌格式（当前实现为标准 HS256 JWT，密钥与有效期由 基础设施配置注入）。校验只负责<b>令牌本身</b>（签名 +
 * 过期）的合法性；账号是否被禁用等 需读库的状态由应用层 / 过滤器结合 {@code UserGateway} 二次判定，二者职责分离。
 *
 * @author Knowledge-Repository
 * @date 2026-09-19
 */
public interface AdminJwtService {

    /**
     * 签发管理端访问令牌。
     *
     * @param userKey 用户唯一标识
     * @param username 用户名
     * @param admin 是否超级管理员
     * @return 已签名的令牌字符串（含过期时间）
     */
    String issue(String userKey, String username, boolean admin);

    /**
     * 校验令牌签名与有效期。
     *
     * @param token 待校验令牌
     * @return 校验通过返回载荷；签名不符 / 已过期 / 格式非法时返回 {@link Optional#empty()}
     */
    Optional<AdminTokenPayload> verify(String token);
}
