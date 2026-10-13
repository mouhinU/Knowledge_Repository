package com.mouhin.knowledge.repository.infrastructure.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mouhin.knowledge.repository.domain.model.valueobject.PlatformTokenPayload;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PlatformIdpTokenGatewayImpl} RS256/JWKS 验签单元测试（P3-T7 / C-3=方案 B）。
 *
 * <p>在测试内自签一对 RSA 密钥，用真实 {@code SHA256withRSA} 造 JWT，配一个内存 {@link JwkSetProvider} 假象提供公钥，覆盖： 合法 →
 * 归一载荷；签名篡改 / alg 非 RS256 / 未知 kid → 拒；issuer / audience 不符 → 拒；exp 过期 → 拒；aud 为数组命中 → 通过。
 * 全程不触网，验证逻辑与生产一致。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@DisplayName("PlatformIdpTokenGatewayImpl RS256/JWKS 验签 (P3-T7)")
class PlatformIdpTokenGatewayImplTest {

    private static final String ISSUER = "https://idp.example.com/realms/master";
    private static final String AUDIENCE = "knowledge-platform";
    private static final String KID = "test-key-1";
    private static final long SKEW = 60L;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();

    private PrivateKey privateKey;
    private String modulusB64Url;
    private String exponentB64Url;

    private static String unsignedBase64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        privateKey = keyPair.getPrivate();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        modulusB64Url = unsignedBase64Url(publicKey.getModulus());
        exponentB64Url = unsignedBase64Url(publicKey.getPublicExponent());
    }

    private PlatformIdpTokenGatewayImpl gateway(JwkSetProvider provider) {
        return new PlatformIdpTokenGatewayImpl(ISSUER, AUDIENCE, null, SKEW, provider);
    }

    // ==================== 正向 ====================

    /** 提供已知 KID 的公钥；其它 kid 返回空。 */
    private JwkSetProvider providerWithTestKey() {
        return kid ->
                KID.equals(kid)
                        ? Optional.of(new RsaJwk(KID, modulusB64Url, exponentB64Url))
                        : Optional.empty();
    }

    @Test
    @DisplayName("合法 RS256 令牌 → 通过并归一 sub/preferred_username/exp")
    void validTokenPassesAndNormalizes() throws Exception {
        long exp = Instant.now().getEpochSecond() + 3600;
        ObjectNode header = header();
        ObjectNode payload = mapper.createObjectNode();
        payload.put("iss", ISSUER);
        payload.put("aud", AUDIENCE);
        payload.put("sub", "operator-42");
        payload.put("preferred_username", "ops.zhang");
        payload.put("exp", exp);
        String token = sign(header, payload);

        Optional<PlatformTokenPayload> result = gateway(providerWithTestKey()).verify(token);

        assertThat(result).isPresent();
        assertThat(result.get().operatorId()).isEqualTo("operator-42");
        assertThat(result.get().operatorName()).isEqualTo("ops.zhang");
        assertThat(result.get().expiresAt()).isEqualTo(Instant.ofEpochSecond(exp));
    }

    // ==================== 反向 ====================

    @Test
    @DisplayName("aud 为数组且命中配置受众 → 通过")
    void audienceAsArrayContainingConfiguredPasses() throws Exception {
        ObjectNode header = header();
        ObjectNode payload = basePayload();
        payload.putArray("aud").add("other-aud").add(AUDIENCE);
        String token = sign(header, payload);

        assertThat(gateway(providerWithTestKey()).verify(token)).isPresent();
    }

    @Test
    @DisplayName("签名被篡改 → 拒")
    void tamperedSignatureRejected() throws Exception {
        String token = sign(header(), basePayload());
        // 翻转签名段中间字符（末尾字符可能位于 base64 填充位，翻转不影响解码字节）
        int dot2 = token.lastIndexOf('.');
        int sigLen = token.length() - dot2 - 1;
        int mid = dot2 + 1 + sigLen / 2;
        char orig = token.charAt(mid);
        char flipped = orig == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, mid) + flipped + token.substring(mid + 1);

        assertThat(gateway(providerWithTestKey()).verify(tampered)).isEmpty();
    }

    @Test
    @DisplayName("alg 非 RS256（HS256）→ 拒")
    void nonRs256AlgRejected() throws Exception {
        ObjectNode header = header();
        header.put("alg", "HS256");
        String token = sign(header, basePayload());

        assertThat(gateway(providerWithTestKey()).verify(token)).isEmpty();
    }

    @Test
    @DisplayName("未知 kid（JWKS 无匹配密钥）→ 拒")
    void unknownKidRejected() throws Exception {
        ObjectNode header = header();
        header.put("kid", "unknown-kid");
        String token = sign(header, basePayload());

        assertThat(gateway(providerWithTestKey()).verify(token)).isEmpty();
    }

    @Test
    @DisplayName("issuer 不匹配 → 拒")
    void wrongIssuerRejected() throws Exception {
        ObjectNode payload = basePayload();
        payload.put("iss", "https://evil.example.com");
        String token = sign(header(), payload);

        assertThat(gateway(providerWithTestKey()).verify(token)).isEmpty();
    }

    @Test
    @DisplayName("audience 不匹配 → 拒")
    void wrongAudienceRejected() throws Exception {
        ObjectNode payload = basePayload();
        payload.put("aud", "some-other-client");
        String token = sign(header(), payload);

        assertThat(gateway(providerWithTestKey()).verify(token)).isEmpty();
    }

    @Test
    @DisplayName("exp 已过期（超出时钟偏移）→ 拒")
    void expiredTokenRejected() throws Exception {
        ObjectNode payload = basePayload();
        payload.put("exp", Instant.now().getEpochSecond() - 3600);
        String token = sign(header(), payload);

        assertThat(gateway(providerWithTestKey()).verify(token)).isEmpty();
    }

    @Test
    @DisplayName("缺 sub → 拒")
    void missingSubjectRejected() throws Exception {
        ObjectNode payload = basePayload();
        payload.remove("sub");
        String token = sign(header(), payload);

        assertThat(gateway(providerWithTestKey()).verify(token)).isEmpty();
    }

    // ==================== 测试辅助 ====================

    @Test
    @DisplayName("非三段 / 空令牌 → 拒（不抛）")
    void malformedTokenRejected() {
        PlatformIdpTokenGatewayImpl gateway = gateway(providerWithTestKey());
        assertThat(gateway.verify(null)).isEmpty();
        assertThat(gateway.verify("")).isEmpty();
        assertThat(gateway.verify("a.b")).isEmpty();
    }

    private ObjectNode header() {
        ObjectNode header = mapper.createObjectNode();
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        header.put("kid", KID);
        return header;
    }

    /** 一份默认合法载荷（iss/aud/sub/exp 均满足），供各反向用例逐字段改坏。 */
    private ObjectNode basePayload() {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("iss", ISSUER);
        payload.put("aud", AUDIENCE);
        payload.put("sub", "operator-1");
        payload.put("preferred_username", "ops");
        payload.put("exp", Instant.now().getEpochSecond() + 3600);
        return payload;
    }

    /** 用测试私钥对 {@code header.payload} 做 SHA256withRSA 签名，拼出三段 JWT。 */
    private String sign(ObjectNode header, ObjectNode payload) throws Exception {
        String signingInput =
                b64.encodeToString(mapper.writeValueAsBytes(header))
                        + "."
                        + b64.encodeToString(mapper.writeValueAsBytes(payload));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + b64.encodeToString(signature.sign());
    }
}
