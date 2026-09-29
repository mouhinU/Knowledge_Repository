package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.valueobject.AdminTokenPayload;
import java.util.List;
import java.util.Optional;

/**
 * 管理端令牌网关（domain 定义，infrastructure 以 JWT 实现）。
 *
 * <p>面向接口做签发 / 校验，领域与应用层不感知令牌格式（当前实现为标准 HS256 JWT，密钥与有效期由 基础设施配置注入）。校验只负责<b>令牌本身</b>（签名 +
 * 过期）的合法性；账号是否被禁用等 需读库的状态由应用层 / 过滤器结合 {@code UserGateway} 二次判定，二者职责分离。
 *
 * <p>P0-T2 起 {@link #issue(String, String, boolean, List)} 支持在 claim 内携带 {@code roles}（{@code
 * sys_role.role_key} 列表），下游 {@code AdminPrincipalSupport} 从 claim 直接读取，无需回库； 旧签名 {@link
 * #issue(String, String, boolean)} 保留为兼容入口，缺省写入空列表。
 *
 * @author mouhinU
 * @date 2026-09-19
 */
public interface AdminJwtService {

    /**
     * 签发管理端访问令牌（不带角色 claim 的旧签名，兼容 1 个版本）。
     *
     * @param userKey 用户唯一标识
     * @param username 用户名
     * @param admin 是否超级管理员
     * @return 已签名的令牌字符串（含过期时间）
     */
    String issue(String userKey, String username, boolean admin);

    /**
     * 签发管理端访问令牌，携带 {@code roles} claim（P0-T2 主用途）。
     *
     * @param userKey 用户唯一标识
     * @param username 用户名
     * @param admin 是否超级管理员
     * @param roles 用户角色 key 列表；{@code null} 视为空
     * @return 已签名的令牌字符串
     */
    default String issue(String userKey, String username, boolean admin, List<String> roles) {
        // 默认降级为旧签名；具体实现在 infrastructure 层重写以真正写入 roles。
        return issue(userKey, username, admin);
    }

    /**
     * 校验令牌签名与有效期。
     *
     * @param token 待校验令牌
     * @return 校验通过返回载荷；签名不符 / 已过期 / 格式非法时返回 {@link Optional#empty()}
     */
    Optional<AdminTokenPayload> verify(String token);
}
