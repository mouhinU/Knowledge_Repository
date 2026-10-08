package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.valueobject.PlatformTokenPayload;
import java.util.Optional;

/**
 * 平台端令牌网关（domain 定义，infrastructure 以独立 JWT 实现，P3-T1 / T-4 方案 B）。
 *
 * <p>与 {@code AdminJwtService}（租户管理员令牌）<b>密钥与签发链完全隔离</b>：平台令牌用 {@code
 * knowledge.platform.jwt.secret} 签名，仅 {@code PlatformTokenAuthFilter} 校验、仅 {@code /api/platform/**}
 * 接受；租户管理员令牌用 {@code knowledge.admin.jwt.secret}，二者互不通用。这是 T-4 方案 B 的核心安全边界——
 * 即便租户管理员令牌泄漏，也无法触达平台生命周期端点。
 *
 * <p>校验只负责<b>令牌本身</b>（签名 + 过期）的合法性；平台管理员账号体系（{@code sys_platform_admin}）本期不建， 令牌签发面向运维带外流程 /
 * 引导脚本（P3-T3 引导 token 复用本接口）。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
public interface PlatformJwtService {

    /**
     * 签发平台端访问令牌。
     *
     * @param operatorId 平台操作者唯一标识（写入 {@code sub} claim）
     * @param operatorName 平台操作者展示名（写入 {@code username} claim）
     * @return 已签名的令牌字符串（含过期时间）
     */
    String issue(String operatorId, String operatorName);

    /**
     * 校验令牌签名与有效期。
     *
     * @param token 待校验令牌
     * @return 校验通过返回载荷；签名不符 / 已过期 / 格式非法时返回 {@link Optional#empty()}
     */
    Optional<PlatformTokenPayload> verify(String token);
}
