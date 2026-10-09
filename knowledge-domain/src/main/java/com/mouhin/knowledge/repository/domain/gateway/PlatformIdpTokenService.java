package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.valueobject.PlatformTokenPayload;
import java.util.Optional;

/**
 * 平台端企业 IdP 令牌校验网关（领域层端口，P3-T7 / C-3=方案 B）。
 *
 * <p>与本地平台令牌端口 {@link PlatformJwtService} 平行：本端口校验<b>企业 IdP 签发的 OIDC ID/Access Token</b>（{@code
 * RS256} + JWKS 公钥验签，附 {@code iss}/{@code aud}/{@code exp} 校验），使平台生命周期端点 {@code /api/platform/**}
 * 既认本地平台 JWT，也认企业 IdP 令牌。 校验成功后同样归一为 {@link PlatformTokenPayload}（{@code sub}→operatorId、{@code
 * preferred_username} 等→operatorName、{@code exp}→expiresAt），从而下游控制器与审计无需感知令牌来源。
 *
 * <p><b>依赖方向</b>（红线 #7）：接口定义于 domain，实现（RS256/JWKS 验签 + 远程取密钥）落 infrastructure，仅在 {@code
 * knowledge.platform.oidc.enabled=true} 时装配；默认关闭态下本端口无实现 bean，平台端仅走本地 JWT 路径（回退闸门）。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
public interface PlatformIdpTokenService {

    /**
     * 校验企业 IdP 令牌。合法且 claim 满足约束时返回归一化的操作者载荷，否则返回 {@link Optional#empty()}（由调用侧统一 401 拒绝）。
     *
     * @param token 原始 JWT 字符串（{@code header.payload.signature}，三段 base64url）
     * @return 校验通过的操作者载荷；签名不符 / 过期 / issuer/audience 不匹配 / 算法不符等一律 {@code empty}
     */
    Optional<PlatformTokenPayload> verify(String token);
}
