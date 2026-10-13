package com.mouhin.knowledge.repository.infrastructure.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link HttpJwkSetProvider#parseJwks(String)} 单元测试（P3-T7 / C-3=方案 B）。
 *
 * <p>脱离网络直接验证 JWKS 文档解析：抽取 {@code kty=RSA} 密钥为 {@code kid -> RsaJwk}；跳过非 RSA / 缺 {@code n}/{@code
 * e} 的畸形条目；缺 {@code kid} 以空串占位键且 {@code keyId} 归 null；空 / 非法 JSON → 空 Map（不抛）。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@DisplayName("HttpJwkSetProvider JWKS 解析 (P3-T7)")
class HttpJwkSetProviderParseJwksTest {

    @Test
    @DisplayName("合法 JWKS：抽 RSA 密钥，保留 kid/n/e")
    void parsesRsaKeys() {
        String json =
                "{\"keys\":[{"
                        + "\"kty\":\"RSA\",\"kid\":\"k1\",\"use\":\"sig\",\"alg\":\"RS256\","
                        + "\"n\":\"abcN\",\"e\":\"AQAB\""
                        + "}]}";

        Map<String, RsaJwk> result = HttpJwkSetProvider.parseJwks(json);

        assertThat(result).containsOnlyKeys("k1");
        assertThat(result.get("k1").keyId()).isEqualTo("k1");
        assertThat(result.get("k1").modulusBase64Url()).isEqualTo("abcN");
        assertThat(result.get("k1").exponentBase64Url()).isEqualTo("AQAB");
    }

    @Test
    @DisplayName("跳过非 RSA 与缺 n/e 的畸形条目")
    void skipsNonRsaAndMalformed() {
        String json =
                "{\"keys\":["
                        + "{\"kty\":\"EC\",\"kid\":\"ec1\",\"n\":\"x\",\"e\":\"y\"},"
                        + "{\"kty\":\"RSA\",\"kid\":\"noN\",\"e\":\"AQAB\"},"
                        + "{\"kty\":\"RSA\",\"kid\":\"ok\",\"n\":\"N\",\"e\":\"E\"}"
                        + "]}";

        Map<String, RsaJwk> result = HttpJwkSetProvider.parseJwks(json);

        assertThat(result).containsOnlyKeys("ok");
    }

    @Test
    @DisplayName("缺 kid 以空串键存储且 keyId 归 null")
    void missingKidStoredUnderBlankKey() {
        String json = "{\"keys\":[{\"kty\":\"RSA\",\"n\":\"N\",\"e\":\"E\"}]}";

        Map<String, RsaJwk> result = HttpJwkSetProvider.parseJwks(json);

        assertThat(result).containsKey("");
        assertThat(result.get("").keyId()).isNull();
    }

    @Test
    @DisplayName("空 / 非法 / 非 keys 数组 JSON → 空 Map，不抛")
    void blankOrMalformedReturnsEmpty() {
        assertThat(HttpJwkSetProvider.parseJwks(null)).isEmpty();
        assertThat(HttpJwkSetProvider.parseJwks("  ")).isEmpty();
        assertThat(HttpJwkSetProvider.parseJwks("not json")).isEmpty();
        assertThat(HttpJwkSetProvider.parseJwks("{\"keys\":\"nope\"}")).isEmpty();
    }
}
