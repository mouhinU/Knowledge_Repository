package com.mouhin.knowledge.repository.infrastructure.security.oidc;

import java.util.Optional;

/**
 * JWKS 公钥来源接缝（P3-T7 / C-3=方案 B）。
 *
 * <p>把"从哪里、怎么取企业 IdP 的验签公钥"从校验网关里解耦出来：生产态由 {@link HttpJwkSetProvider} 经 {@code
 * java.net.http.HttpClient} 拉取并缓存远程 JWKS；测试态注入内存实现即可构造各类验签用例（有效 / 篡改 / 未知 kid / 过期），无需真实 HTTP
 * 服务。校验网关只依赖本接缝，满足红线 #8「长耗时外部 IO 收敛到基础设施边界」。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
public interface JwkSetProvider {

    /**
     * 按 {@code kid} 选取匹配的 RSA 公钥。
     *
     * @param kid 令牌 header 的 {@code kid}；{@code null} / 空白时实现方按"单一可用 RSA 密钥"回退选取
     * @return 命中的 RSA JWK；无匹配返回 {@link Optional#empty()}
     */
    Optional<RsaJwk> findKey(String kid);
}
