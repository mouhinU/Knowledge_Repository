package com.mouhin.knowledge.repository.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mouhin.knowledge.repository.domain.gateway.AdminJwtService;
import com.mouhin.knowledge.repository.domain.model.valueobject.AdminTokenPayload;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 管理端令牌网关的 JWT 实现（infrastructure 层，标准 HS256）。
 *
 * <p>采用 JDK 原生 {@link javax.crypto.Mac}（HmacSHA256）+ Jackson 手工拼装 / 解析，<b>不引入任何第三方 JWT
 * 库</b>，以契合本项目离线镜像构建（DaoCloud 镜像源、无新增 Maven 依赖）的约束。令牌结构为标准 {@code
 * base64url(header).base64url(payload).base64url(signature)} 三段式：
 *
 * <ul>
 *   <li>header：{@code {"alg":"HS256","typ":"JWT"}}
 *   <li>payload：{@code {"sub":userKey,"username":..,"admin":..,"iat":..,"exp":..}}
 * </ul>
 *
 * <p>密钥经 {@code knowledge.admin.jwt.secret}（推荐由 {@code KNOWLEDGE_ADMIN_JWT_SECRET} 环境变量注入，
 * 缺省仅用于本地开发）配置；有效期 {@code knowledge.admin.jwt.expiration-hours}，默认 8 小时。 校验时以常量时间比较签名，并检查 {@code
 * exp}，任何篡改 / 过期 / 格式非法均返回 {@link Optional#empty()}。账号是否被禁用不在此判定（交由过滤器读库二次确认），保持职责分离。
 *
 * @author Knowledge-Repository
 * @date 2026-09-19
 */
@Component
public class AdminJwtGatewayImpl implements AdminJwtService {

    private static final Logger logger = LoggerFactory.getLogger(AdminJwtGatewayImpl.class);

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final byte[] HEADER_BYTES = HEADER_JSON.getBytes(StandardCharsets.UTF_8);
    private static final Base64.Encoder B64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();
    private static final String CLAIM_SUB = "sub";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ADMIN = "admin";
    private static final String CLAIM_IAT = "iat";
    private static final String CLAIM_EXP = "exp";
    private static final int TOKEN_SEGMENT_COUNT = 3;
    private static final long SECONDS_PER_HOUR = 3600L;

    private final byte[] secretBytes;
    private final long expirationSeconds;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminJwtGatewayImpl(
            @Value("${knowledge.admin.jwt.secret:knowledge-repo-dev-admin-jwt-secret-change-me}")
                    String secret,
            @Value("${knowledge.admin.jwt.expiration-hours:8}") long expirationHours) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("knowledge.admin.jwt.secret must not be blank");
        }
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = Math.max(1L, expirationHours) * SECONDS_PER_HOUR;
    }

    @Override
    public String issue(String userKey, String username, boolean admin) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expirationSeconds);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(CLAIM_SUB, userKey);
        payload.put(CLAIM_USERNAME, username);
        payload.put(CLAIM_ADMIN, admin);
        payload.put(CLAIM_IAT, now.getEpochSecond());
        payload.put(CLAIM_EXP, exp.getEpochSecond());

        String encodedHeader = B64_ENCODER.encodeToString(HEADER_BYTES);
        String encodedPayload;
        try {
            encodedPayload = B64_ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            logger.error("Failed to serialize JWT payload for userKey: {}", userKey, e);
            throw new IllegalStateException("Failed to issue admin token", e);
        }
        String signingInput = encodedHeader + "." + encodedPayload;
        String signature = B64_ENCODER.encodeToString(hmac(signingInput));
        return signingInput + "." + signature;
    }

    @Override
    public Optional<AdminTokenPayload> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] segments = token.split("\\.");
        if (segments.length != TOKEN_SEGMENT_COUNT) {
            return Optional.empty();
        }
        String signingInput = segments[0] + "." + segments[1];
        byte[] expectedSignature;
        try {
            expectedSignature = hmac(signingInput);
            byte[] actualSignature = B64_DECODER.decode(segments[2]);
            if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
                return Optional.empty();
            }
            byte[] payloadBytes = B64_DECODER.decode(segments[1]);
            ObjectNode payload = (ObjectNode) objectMapper.readTree(payloadBytes);
            long exp = payload.path(CLAIM_EXP).asLong(0L);
            if (exp <= Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            String userKey = payload.path(CLAIM_SUB).asText(null);
            if (userKey == null || userKey.isBlank()) {
                return Optional.empty();
            }
            String username = payload.path(CLAIM_USERNAME).asText(null);
            boolean admin = payload.path(CLAIM_ADMIN).asBoolean(false);
            return Optional.of(
                    new AdminTokenPayload(userKey, username, admin, Instant.ofEpochSecond(exp)));
        } catch (Exception e) {
            logger.debug("Admin token verification failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private byte[] hmac(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALGORITHM));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.error("HMAC computation failed", e);
            throw new IllegalStateException("Failed to sign admin token", e);
        }
    }
}
