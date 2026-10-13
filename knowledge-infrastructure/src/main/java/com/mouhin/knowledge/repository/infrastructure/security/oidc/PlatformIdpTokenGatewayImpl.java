package com.mouhin.knowledge.repository.infrastructure.security.oidc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mouhin.knowledge.repository.domain.gateway.PlatformIdpTokenService;
import com.mouhin.knowledge.repository.domain.model.valueobject.PlatformTokenPayload;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 企业 IdP OIDC 令牌校验网关的 RS256/JWKS 实现（infrastructure 层，P3-T7 / C-3=方案 B）。
 *
 * <p>与本地 HS256 的 {@code PlatformJwtGatewayImpl} 并列但<b>算法不同</b>：本实现校验企业 IdP 用<b>非对称 RS256</b> 私钥签发的
 * JWT——按 header {@code kid} 经 {@link JwkSetProvider} 取 RSA 公钥，用 JDK 原生 {@link
 * java.security.Signature}（{@code SHA256withRSA}）验签，再校验 {@code iss} 精确匹配、{@code aud} 命中配置受众、{@code
 * exp}/{@code nbf} 在允许的时钟偏移内、{@code sub} 非空。全部通过才归一为 {@link PlatformTokenPayload}（{@code sub}→操作者
 * ID，{@code preferred_username}/{@code email}/{@code name} 依次取展示名）。任何一步不满足一律返回 {@code
 * empty}（安全侧默认拒绝，不区分失败原因）。
 *
 * <p><b>零新增依赖</b>（红线 #11）：与 {@code PlatformJwtGatewayImpl} 手工拼装 JWT 的既有约束一致，本类仅用 JDK 密码学 + Jackson，
 * <b>不</b>引入 {@code spring-security-oauth2-jose}/{@code nimbus-jose-jwt} 等，避免框架依赖变更需另提案。
 *
 * <p><b>门控</b>：仅当 {@code knowledge.platform.oidc.enabled=true} 才装配（{@link ConditionalOnProperty}）；
 * 关闭态下不构造，平台端仅走本地 JWT。开启时 {@code issuer} 与 {@code audience}（或 {@code
 * client-id}）任一缺失即启动失败（fail-fast， 绝不静默降级为"只验签不校验受众"）。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Component
@ConditionalOnProperty(name = "knowledge.platform.oidc.enabled", havingValue = "true")
@Slf4j
public class PlatformIdpTokenGatewayImpl implements PlatformIdpTokenService {

    private static final String ALG_RS256 = "RS256";
    private static final String SIGN_ALGORITHM = "SHA256withRSA";
    private static final String KEY_ALGORITHM = "RSA";
    private static final int TOKEN_SEGMENT_COUNT = 3;

    private static final String CLAIM_ALG = "alg";
    private static final String CLAIM_KID = "kid";
    private static final String CLAIM_ISS = "iss";
    private static final String CLAIM_AUD = "aud";
    private static final String CLAIM_EXP = "exp";
    private static final String CLAIM_NBF = "nbf";
    private static final String CLAIM_SUB = "sub";
    private static final String CLAIM_PREFERRED_USERNAME = "preferred_username";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_NAME = "name";

    private final String issuer;
    private final String audience;
    private final long clockSkewSeconds;
    private final JwkSetProvider jwkSetProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Base64.Decoder b64Decoder = Base64.getUrlDecoder();

    public PlatformIdpTokenGatewayImpl(
            @Value("${knowledge.platform.oidc.issuer:}") String issuer,
            @Value("${knowledge.platform.oidc.audience:}") String audience,
            @Value("${knowledge.platform.oidc.client-id:}") String clientId,
            @Value("${knowledge.platform.oidc.clock-skew-seconds:60}") long clockSkewSeconds,
            JwkSetProvider jwkSetProvider) {
        String effectiveAudience =
                !isBlank(audience) ? audience.trim() : (isBlank(clientId) ? null : clientId.trim());
        if (isBlank(issuer) || effectiveAudience == null) {
            throw new IllegalStateException(
                    "knowledge.platform.oidc.issuer and (audience | client-id) must be configured when oidc.enabled=true");
        }
        this.issuer = issuer.trim();
        this.audience = effectiveAudience;
        this.clockSkewSeconds = Math.max(0L, clockSkewSeconds);
        this.jwkSetProvider = jwkSetProvider;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (!isBlank(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public Optional<PlatformTokenPayload> verify(String token) {
        if (isBlank(token)) {
            return Optional.empty();
        }
        String[] segments = token.split("\\.");
        if (segments.length != TOKEN_SEGMENT_COUNT) {
            return Optional.empty();
        }
        try {
            JsonNode header = readJson(segments[0]);
            if (!ALG_RS256.equalsIgnoreCase(header.path(CLAIM_ALG).asText(""))) {
                return Optional.empty();
            }
            String kid = header.path(CLAIM_KID).asText(null);
            Optional<RsaJwk> jwk = jwkSetProvider.findKey(kid);
            if (jwk.isEmpty()) {
                log.debug("No matching JWKS key for kid '{}', reject IdP token", kid);
                return Optional.empty();
            }
            PublicKey publicKey = buildPublicKey(jwk.get());
            if (!verifySignature(publicKey, segments[0] + "." + segments[1], segments[2])) {
                return Optional.empty();
            }

            JsonNode payload = readJson(segments[1]);
            return validateClaims(payload);
        } catch (Exception ex) {
            log.debug("Platform IdP token verification failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /** 校验签发者 / 受众 / 时效 / 主体；全部通过返回归一载荷，否则 empty。 */
    private Optional<PlatformTokenPayload> validateClaims(JsonNode payload) {
        String iss = payload.path(CLAIM_ISS).asText(null);
        if (!issuer.equals(iss)) {
            return Optional.empty();
        }
        if (!audienceMatches(payload.path(CLAIM_AUD))) {
            return Optional.empty();
        }
        long now = Instant.now().getEpochSecond();
        long exp = payload.path(CLAIM_EXP).asLong(0L);
        if (exp <= 0L || exp + clockSkewSeconds < now) {
            return Optional.empty();
        }
        if (payload.hasNonNull(CLAIM_NBF)) {
            long nbf = payload.path(CLAIM_NBF).asLong(0L);
            if (nbf - clockSkewSeconds > now) {
                return Optional.empty();
            }
        }
        String operatorId = payload.path(CLAIM_SUB).asText(null);
        if (isBlank(operatorId)) {
            return Optional.empty();
        }
        String operatorName =
                firstNonBlank(
                        payload.path(CLAIM_PREFERRED_USERNAME).asText(null),
                        payload.path(CLAIM_EMAIL).asText(null),
                        payload.path(CLAIM_NAME).asText(null),
                        operatorId);
        return Optional.of(
                new PlatformTokenPayload(operatorId, operatorName, Instant.ofEpochSecond(exp)));
    }

    /** {@code aud} 可为字符串或字符串数组；命中配置受众即为真。 */
    private boolean audienceMatches(JsonNode aud) {
        if (aud.isMissingNode() || aud.isNull()) {
            return false;
        }
        if (aud.isArray()) {
            for (JsonNode element : aud) {
                if (audience.equals(element.asText(null))) {
                    return true;
                }
            }
            return false;
        }
        return audience.equals(aud.asText(null));
    }

    private boolean verifySignature(
            PublicKey publicKey, String signingInput, String signatureSegment) throws Exception {
        Signature signature = Signature.getInstance(SIGN_ALGORITHM);
        signature.initVerify(publicKey);
        signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
        byte[] rawSignature = b64Decoder.decode(signatureSegment);
        return signature.verify(rawSignature);
    }

    private PublicKey buildPublicKey(RsaJwk jwk) throws Exception {
        BigInteger modulus = new BigInteger(1, b64Decoder.decode(jwk.modulusBase64Url()));
        BigInteger exponent = new BigInteger(1, b64Decoder.decode(jwk.exponentBase64Url()));
        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
        return KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(spec);
    }

    private JsonNode readJson(String base64UrlSegment) throws Exception {
        return objectMapper.readTree(b64Decoder.decode(base64UrlSegment));
    }
}
