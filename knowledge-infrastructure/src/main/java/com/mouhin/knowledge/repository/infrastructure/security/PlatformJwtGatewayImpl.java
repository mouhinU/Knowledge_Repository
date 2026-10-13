package com.mouhin.knowledge.repository.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mouhin.knowledge.repository.domain.gateway.PlatformJwtService;
import com.mouhin.knowledge.repository.domain.model.valueobject.PlatformTokenPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * 平台端令牌网关的 JWT 实现（infrastructure 层，标准 HS256，P3-T1 / T-4 方案 B）。
 *
 * <p>与 {@code AdminJwtGatewayImpl} 同构（JDK 原生 {@link javax.crypto.Mac} + Jackson 手工拼装，<b>不引入第三方 JWT
 * 库</b>，契合离线镜像构建约束），但<b>密钥独立</b>：签名密钥来自 {@code knowledge.platform.jwt.secret}， 与租户管理员令牌的 {@code
 * knowledge.admin.jwt.secret} 分离，两套令牌互不可用。
 *
 * <p>令牌结构 {@code base64url(header).base64url(payload).base64url(signature)}：
 *
 * <ul>
 *   <li>header：{@code {"alg":"HS256","typ":"JWT"}}
 *   <li>payload：{@code
 *       {"sub":operatorId,"username":operatorName,"scope":"platform","iat":..,"exp":..}}
 * </ul>
 *
 * <p>{@code scope=platform} 是防跨体系复用的显式标记：即便有人误把平台密钥配成与管理员密钥相同， 校验端仍要求 {@code scope} 恒为 {@code
 * platform}，管理员令牌（无此 claim）不会被平台过滤器接受。
 *
 * <p><b>门控</b>：仅当 {@code knowledge.platform.enabled=true} 才注册本 bean（{@link ConditionalOnProperty}）。
 * 默认关闭态下不构造，故 {@code knowledge.platform.jwt.secret} 留空也不会触发启动失败——满足「双门控零风险回退」。 开启时密钥必须由 {@code
 * KNOWLEDGE_PLATFORM_JWT_SECRET} 注入，缺失即启动失败（fail-fast，绝不静默降级）。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Component
@ConditionalOnProperty(name = "knowledge.platform.enabled", havingValue = "true")
@Slf4j
public class PlatformJwtGatewayImpl implements PlatformJwtService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final byte[] HEADER_BYTES = HEADER_JSON.getBytes(StandardCharsets.UTF_8);
    private static final Base64.Encoder B64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();
    private static final String CLAIM_SUB = "sub";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_SCOPE = "scope";
    private static final String CLAIM_IAT = "iat";
    private static final String CLAIM_EXP = "exp";
    private static final String SCOPE_PLATFORM = "platform";
    private static final int TOKEN_SEGMENT_COUNT = 3;
    private static final long SECONDS_PER_HOUR = 3600L;

    private final byte[] secretBytes;
    private final long expirationSeconds;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PlatformJwtGatewayImpl(
            @Value("${knowledge.platform.jwt.secret}") String secret,
            @Value("${knowledge.platform.jwt.expiration-hours:8}") long expirationHours) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("knowledge.platform.jwt.secret must not be blank");
        }
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = Math.max(1L, expirationHours) * SECONDS_PER_HOUR;
    }

    @Override
    public String issue(String operatorId, String operatorName) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expirationSeconds);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(CLAIM_SUB, operatorId);
        payload.put(CLAIM_USERNAME, operatorName);
        payload.put(CLAIM_SCOPE, SCOPE_PLATFORM);
        payload.put(CLAIM_IAT, now.getEpochSecond());
        payload.put(CLAIM_EXP, exp.getEpochSecond());

        String encodedHeader = B64_ENCODER.encodeToString(HEADER_BYTES);
        String encodedPayload;
        try {
            encodedPayload = B64_ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            log.error("Failed to serialize platform JWT payload for operatorId: {}", operatorId, e);
            throw new IllegalStateException("Failed to issue platform token", e);
        }
        String signingInput = encodedHeader + "." + encodedPayload;
        String signature = B64_ENCODER.encodeToString(hmac(signingInput));
        return signingInput + "." + signature;
    }

    @Override
    public Optional<PlatformTokenPayload> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] segments = token.split("\\.");
        if (segments.length != TOKEN_SEGMENT_COUNT) {
            return Optional.empty();
        }
        String signingInput = segments[0] + "." + segments[1];
        try {
            byte[] expectedSignature = hmac(signingInput);
            byte[] actualSignature = B64_DECODER.decode(segments[2]);
            if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
                return Optional.empty();
            }
            byte[] payloadBytes = B64_DECODER.decode(segments[1]);
            ObjectNode payload = (ObjectNode) objectMapper.readTree(payloadBytes);
            if (!SCOPE_PLATFORM.equals(payload.path(CLAIM_SCOPE).asText(null))) {
                return Optional.empty();
            }
            long exp = payload.path(CLAIM_EXP).asLong(0L);
            if (exp <= Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            String operatorId = payload.path(CLAIM_SUB).asText(null);
            if (operatorId == null || operatorId.isBlank()) {
                return Optional.empty();
            }
            String operatorName = payload.path(CLAIM_USERNAME).asText(null);
            return Optional.of(
                    new PlatformTokenPayload(operatorId, operatorName, Instant.ofEpochSecond(exp)));
        } catch (Exception e) {
            log.debug("Platform token verification failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private byte[] hmac(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALGORITHM));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Platform HMAC computation failed", e);
            throw new IllegalStateException("Failed to sign platform token", e);
        }
    }
}
