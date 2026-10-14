package com.mouhin.knowledge.repository.domain.constant;

/**
 * JWT 头部与声明（Claim）名称常量。
 *
 * <p>三个 JWT 网关（{@code AdminJwtGatewayImpl} / {@code PlatformJwtGatewayImpl} / {@code
 * PlatformIdpTokenGatewayImpl}）共享同一组 RFC 7519 标准 claim 名与企业 OIDC 常见扩展 claim 名。依 AGENTS.md 红线 #2 +
 * DRY 原则集中维护，避免各处 {@code private static final String CLAIM_SUB = "sub"} 三份重复。
 */
public final class JwtClaims {

    private JwtClaims() {}

    // ── JWS Header 字段 ──────────────────────────────────

    /** 算法字段（JWS header {@code alg}）。 */
    public static final String HEADER_ALG = "alg";

    /** 密钥 ID 字段（JWS header {@code kid}）。 */
    public static final String HEADER_KID = "kid";

    /** 类型字段（JWS header {@code typ}）。 */
    public static final String HEADER_TYP = "typ";

    /** HS256 签名算法值（自签发管理端/平台令牌）。 */
    public static final String ALG_HS256 = "HS256";

    /** RS256 签名算法值（企业 IdP 令牌常见）。 */
    public static final String ALG_RS256 = "RS256";

    /** JWT 类型标识值。 */
    public static final String TYP_JWT = "JWT";

    // ── Registered Claims (RFC 7519 §4.1) ────────────────

    /** Subject claim：{@code sub}。 */
    public static final String SUB = "sub";

    /** Issuer claim：{@code iss}。 */
    public static final String ISS = "iss";

    /** Audience claim：{@code aud}。 */
    public static final String AUD = "aud";

    /** Expiration time claim：{@code exp}。 */
    public static final String EXP = "exp";

    /** Not before claim：{@code nbf}。 */
    public static final String NBF = "nbf";

    /** Issued at claim：{@code iat}。 */
    public static final String IAT = "iat";

    /** JWT ID claim：{@code jti}。 */
    public static final String JTI = "jti";

    // ── 项目自定义 Claims ────────────────────────────────

    /** 用户名 claim（本项目管理端/平台令牌自定义字段 {@code username}）。 */
    public static final String USERNAME = "username";

    /** 授权范围 claim（{@code scope}，平台令牌使用）。 */
    public static final String SCOPE = "scope";

    /** 管理员标记 claim（{@code admin}，管理端令牌使用）。 */
    public static final String ADMIN = "admin";

    /** 角色数组 claim（{@code roles}，管理端令牌使用）。 */
    public static final String ROLES = "roles";

    /** 租户键 claim（{@code tenantKey}，SaaS 场景）。 */
    public static final String TENANT_KEY = "tenantKey";

    // ── OIDC 标准扩展 Claims (OpenID Connect Core §5.1) ─

    /** OIDC 首选用户名 claim（{@code preferred_username}）。 */
    public static final String PREFERRED_USERNAME = "preferred_username";

    /** OIDC 邮箱 claim（{@code email}）。 */
    public static final String EMAIL = "email";

    /** OIDC 显示名 claim（{@code name}）。 */
    public static final String NAME = "name";
}
