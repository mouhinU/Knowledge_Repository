package com.mouhin.knowledge.repository.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mouhin.knowledge.repository.domain.model.valueobject.PlatformTokenPayload;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PlatformJwtGatewayImpl} 平台端 JWT 网关单测（P3-T1 / T-4 方案 B）。
 *
 * <p>锁定：签发→校验往返、scope=platform 强制、篡改 / 异密钥 / 过期 / 格式非法拒绝、空密钥构造 fail-fast。 与 {@code
 * AdminJwtGatewayImplTest} 同构，但额外验证「无 scope=platform claim 的令牌被拒」——这是平台令牌与管理员令牌 互不可用的关键边界。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@DisplayName("PlatformJwtGatewayImpl HS256 签发与校验 (P3-T1)")
class PlatformJwtGatewayImplTest {

    private static final String SECRET = "platform-unit-test-secret-0123456789";

    private PlatformJwtGatewayImpl gateway(long hours) {
        return new PlatformJwtGatewayImpl(SECRET, hours);
    }

    @Test
    @DisplayName("构造时密钥空白 → fail-fast 抛 IllegalStateException")
    void blankSecretFailsFast() {
        assertThatThrownBy(() -> new PlatformJwtGatewayImpl("  ", 8))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("knowledge.platform.jwt.secret");
    }

    @Test
    @DisplayName("签发后校验还原操作者身份且带未来过期时间")
    void issueThenVerifyRoundTrip() {
        PlatformJwtGatewayImpl gw = gateway(8);
        String token = gw.issue("op-123", "platform-admin");

        assertThat(token.chars().filter(c -> c == '.').count()).isEqualTo(2);

        Optional<PlatformTokenPayload> verified = gw.verify(token);
        assertThat(verified).isPresent();
        assertThat(verified.get().operatorId()).isEqualTo("op-123");
        assertThat(verified.get().operatorName()).isEqualTo("platform-admin");
        assertThat(verified.get().expiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("篡改载荷后校验失败")
    void rejectsTamperedPayload() {
        PlatformJwtGatewayImpl gw = gateway(8);
        String token = gw.issue("op-123", "platform-admin");
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "x" + "." + parts[2];
        assertThat(gw.verify(tampered)).isEmpty();
    }

    @Test
    @DisplayName("异密钥签发的令牌校验失败（平台/管理员密钥隔离）")
    void rejectsForeignSecret() {
        PlatformJwtGatewayImpl issuer =
                new PlatformJwtGatewayImpl("another-platform-secret-zzz", 8);
        String foreign = issuer.issue("op-123", "platform-admin");
        assertThat(gateway(8).verify(foreign)).isEmpty();
    }

    @Test
    @DisplayName("过期令牌校验失败")
    void rejectsExpired() throws Exception {
        PlatformJwtGatewayImpl gw = gateway(8);
        Field f = PlatformJwtGatewayImpl.class.getDeclaredField("expirationSeconds");
        f.setAccessible(true);
        f.setLong(gw, -60L);
        String expired = gw.issue("op-123", "platform-admin");
        assertThat(gw.verify(expired)).isEmpty();
    }

    @Test
    @DisplayName("格式非法 / 空令牌安全返回 empty")
    void rejectsMalformed() {
        PlatformJwtGatewayImpl gw = gateway(8);
        assertThat(gw.verify(null)).isEmpty();
        assertThat(gw.verify("")).isEmpty();
        assertThat(gw.verify("not-a-jwt")).isEmpty();
        assertThat(gw.verify("a.b")).isEmpty();
    }

    @Test
    @DisplayName("缺 scope=platform claim 的令牌被拒（防管理员令牌跨体系复用）")
    void rejectsTokenWithoutPlatformScope() {
        PlatformJwtGatewayImpl gw = gateway(8);
        String token = gw.issue("op-123", "platform-admin");
        // 解出 payload，去掉 scope 后重编（不重签，签名仍匹配原 payload 字节——故须重新构造一个 scope 缺失但签名有效的场景）。
        // 简化：直接构造一个用相同密钥、但 payload 不含 scope 的令牌。
        String noScope = issueWithoutScope(gw, "op-123", "platform-admin");
        assertThat(gw.verify(noScope)).isEmpty();
        // 原始合法令牌仍应通过（对照）
        assertThat(gw.verify(token)).isPresent();
    }

    /** 用反射借用 gateway 的 hmac 逻辑构造一个缺 scope claim 的令牌，验证 scope 强制校验。 */
    private String issueWithoutScope(PlatformJwtGatewayImpl gw, String opId, String opName) {
        try {
            java.util.Base64.Encoder enc = java.util.Base64.getUrlEncoder().withoutPadding();
            String header =
                    enc.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes("UTF-8"));
            long now = Instant.now().getEpochSecond();
            String payloadJson =
                    "{\"sub\":\""
                            + opId
                            + "\",\"username\":\""
                            + opName
                            + "\",\"iat\":"
                            + now
                            + ",\"exp\":"
                            + (now + 3600)
                            + "}";
            String payload = enc.encodeToString(payloadJson.getBytes("UTF-8"));
            String signingInput = header + "." + payload;
            java.lang.reflect.Method hmac =
                    PlatformJwtGatewayImpl.class.getDeclaredMethod("hmac", String.class);
            hmac.setAccessible(true);
            byte[] sig = (byte[]) hmac.invoke(gw, signingInput);
            return signingInput + "." + enc.encodeToString(sig);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
