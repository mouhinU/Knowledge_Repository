package com.mouhin.knowledge.repository.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mouhin.knowledge.repository.domain.model.valueobject.AdminTokenPayload;
import com.mouhin.knowledge.repository.infrastructure.security.AdminJwtGatewayImpl;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 管理端 JWT 网关实现单元测试（HS256 手工签发 / 校验）。
 *
 * @author mouhinU
 * @date 2026-09-19
 */
@DisplayName("AdminJwtGatewayImpl HS256 签发与校验")
class AdminJwtGatewayImplTest {

    private static final String SECRET = "unit-test-secret-key-0123456789";

    private AdminJwtGatewayImpl gateway(long hours) {
        return new AdminJwtGatewayImpl(SECRET, hours);
    }

    @Test
    @DisplayName("签发后校验应还原身份且带未来过期时间")
    void issueThenVerify_roundTrip() {
        AdminJwtGatewayImpl gw = gateway(8);
        String token = gw.issue("key-123", "admin", true);

        assertTrue(token.chars().filter(c -> c == '.').count() == 2, "标准 JWT 应为三段式");

        Optional<AdminTokenPayload> verified = gw.verify(token);
        assertTrue(verified.isPresent());
        AdminTokenPayload payload = verified.get();
        assertEquals("key-123", payload.userKey());
        assertEquals("admin", payload.username());
        assertTrue(payload.admin());
        assertTrue(payload.expiresAt().isAfter(Instant.now()), "过期时间应在未来");
    }

    @Test
    @DisplayName("篡改载荷后校验应失败")
    void verify_rejectsTamperedPayload() {
        AdminJwtGatewayImpl gw = gateway(8);
        String token = gw.issue("key-123", "admin", false);
        String[] parts = token.split("\\.");
        // 把载荷换成 admin=true 的伪造段（未重签），签名应不再匹配
        String forgedPayload = "eyJzdWIiOiJrZXktMTIzIiwidXNlcm5hbWUiOiJhZG1pbiIsImFkbWluIjp0cnVlfQ";
        String tampered = parts[0] + "." + forgedPayload + "." + parts[2];

        assertFalse(gw.verify(tampered).isPresent(), "篡改令牌必须校验失败");
    }

    @Test
    @DisplayName("用不同密钥的令牌应校验失败")
    void verify_rejectsForeignSecret() {
        AdminJwtGatewayImpl issuer = new AdminJwtGatewayImpl("another-secret-key-zzzzzzzz", 8);
        String foreign = issuer.issue("key-123", "admin", true);

        assertFalse(gateway(8).verify(foreign).isPresent(), "异密钥令牌必须校验失败");
    }

    @Test
    @DisplayName("过期令牌应校验失败")
    void verify_rejectsExpired() throws Exception {
        AdminJwtGatewayImpl gw = gateway(8);
        // 反射将有效期改为负值，签发一个即刻过期的令牌
        Field f = AdminJwtGatewayImpl.class.getDeclaredField("expirationSeconds");
        f.setAccessible(true);
        f.setLong(gw, -60L);
        String expired = gw.issue("key-123", "admin", true);

        assertFalse(gw.verify(expired).isPresent(), "过期令牌必须校验失败");
    }

    @Test
    @DisplayName("格式非法 / 空令牌应安全返回 empty")
    void verify_rejectsMalformed() {
        AdminJwtGatewayImpl gw = gateway(8);
        assertFalse(gw.verify(null).isPresent());
        assertFalse(gw.verify("").isPresent());
        assertFalse(gw.verify("not-a-jwt").isPresent());
        assertFalse(gw.verify("a.b").isPresent());
    }
}
