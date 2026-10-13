package com.mouhin.knowledge.repository.infrastructure.security.oidc;

/**
 * RSA 类型 JWK 的最小投影（P3-T7 / C-3=方案 B）。
 *
 * <p>只保留 {@code RS256} 验签所需的字段：{@code kid}（密钥匹配用）与模数/公钥指数的 <b>base64url（无填充）</b> 编码。 {@code n}
 * 为模数（modulus）、{@code e} 为公钥指数（exponent），均为大端无符号整数的 base64url 串（对齐 RFC 7518 {@code RSA/EC } {@code
 * KeyType}）。解析由 {@link HttpJwkSetProvider#parseJwks(String)} 从远程 JWKS 文档的 {@code keys[]} 中提取，构建
 * {@code java.security.PublicKey} 的动作收敛在校验网关内。
 *
 * @param keyId {@code kid} 成员，可能为 {@code null}（部分 IdP 不返回；此时按单密钥回退选取）
 * @param modulusBase64Url {@code n} 成员（base64url 无填充）
 * @param exponentBase64Url {@code e} 成员（base64url 无填充）
 * @author mouhinU
 * @date 2026-10-09
 */
public record RsaJwk(String keyId, String modulusBase64Url, String exponentBase64Url) {}
