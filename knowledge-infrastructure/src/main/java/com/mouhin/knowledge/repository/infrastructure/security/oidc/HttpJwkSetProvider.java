package com.mouhin.knowledge.repository.infrastructure.security.oidc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mouhin.knowledge.repository.domain.constant.FeatureFlagKeys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 基于 HTTP 的远程 JWKS 公钥来源（P3-T7 / C-3=方案 B，infrastructure 实现）。
 *
 * <p>经 JDK 原生 {@link java.net.http.HttpClient}（本仓库既有的出站 HTTP 通道，<b>不引入任何新依赖</b>，契合离线镜像约束） 拉取企业 IdP
 * 的 JWKS 文档，解析出 RSA 公钥集合并按 {@code kid} 供校验网关选取。带 TTL 的进程内缓存，避免每次验签都打远程；
 * 刷新失败时若有陈旧快照则继续用陈旧快照并告警，无快照则返回空导致后续 401。
 *
 * <p><b>门控</b>：仅当 {@code knowledge.platform.oidc.enabled=true} 才装配（{@link ConditionalOnProperty}）；
 * 关闭态下本 bean 缺席，{@link PlatformIdpTokenGatewayImpl} 同样不装配，平台端仅走本地 JWT 路径（回退闸门）。开启时 {@code
 * knowledge.platform.oidc.jwks-uri} 缺失即启动失败（fail-fast，绝不静默降级到"接受一切"）。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Component
@ConditionalOnProperty(name = FeatureFlagKeys.PLATFORM_OIDC_ENABLED, havingValue = "true")
@Slf4j
public class HttpJwkSetProvider implements JwkSetProvider {

    /** JWKS 文档中被解析的 RSA 密钥类型标记。 */
    private static final String KEY_TYPE_RSA = "RSA";

    /** HTTP 连接 / 读取超时（秒）。 */
    private static final int HTTP_TIMEOUT_SECONDS = 5;

    private final URI jwksUri;
    private final long cacheTtlSeconds;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 最近一次成功解析出的 {@code kid -> RsaJwk} 快照（不可变视图）；从未成功过时为空 Map。 */
    private volatile Map<String, RsaJwk> snapshot = Collections.emptyMap();

    /** 最近一次成功拉取/解析的时刻；用于 TTL 判定。 */
    private volatile Instant lastLoadedAt = Instant.EPOCH;

    public HttpJwkSetProvider(
            @Value("${knowledge.platform.oidc.jwks-uri:}") String jwksUri,
            @Value("${knowledge.platform.oidc.jwks-cache-ttl-seconds:300}") long cacheTtlSeconds) {
        if (jwksUri == null || jwksUri.isBlank()) {
            throw new IllegalStateException("knowledge.platform.oidc.jwks-uri must not be blank");
        }
        this.jwksUri = URI.create(jwksUri.trim());
        this.cacheTtlSeconds = cacheTtlSeconds > 0 ? cacheTtlSeconds : 300L;
        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                        .build();
    }

    /**
     * 解析 JWKS JSON 文档，抽取 {@code kty=RSA} 的密钥为 {@code kid -> RsaJwk} 不可变映射。
     *
     * <p>对缺失 {@code kid} 的密钥以空串占位键存储（与 {@link #findKey(String)} 的"单密钥回退"协同）；对既无 {@code n} 也无 {@code
     * e} 的畸形条目直接跳过，不影响其余密钥。抽为静态方法便于脱离网络的单元测试。
     *
     * @param jwksJson JWKS 文档原文（{@code {"keys":[{"kty":"RSA","kid":...,"n":...,"e":...}]}}）
     * @return 不可变 {@code kid -> RsaJwk} 映射；无 RSA 密钥时为空 Map
     */
    static Map<String, RsaJwk> parseJwks(String jwksJson) {
        Map<String, RsaJwk> result = new LinkedHashMap<>();
        if (jwksJson == null || jwksJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jwksJson);
            JsonNode keys = root.path("keys");
            if (!keys.isArray()) {
                return Collections.emptyMap();
            }
            for (JsonNode key : keys) {
                if (!KEY_TYPE_RSA.equalsIgnoreCase(key.path("kty").asText(""))) {
                    continue;
                }
                String n = key.path("n").asText(null);
                String e = key.path("e").asText(null);
                if (n == null || n.isBlank() || e == null || e.isBlank()) {
                    continue;
                }
                String kid = key.path("kid").asText("");
                result.put(kid, new RsaJwk(kid.isEmpty() ? null : kid, n, e));
            }
        } catch (Exception ex) {
            log.warn("Failed to parse JWKS document: {}", ex.getMessage());
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public Optional<RsaJwk> findKey(String kid) {
        Map<String, RsaJwk> keys = currentKeys();
        if (keys.isEmpty()) {
            return Optional.empty();
        }
        if (kid != null && !kid.isBlank()) {
            return Optional.ofNullable(keys.get(kid));
        }
        // 无 kid：仅当快照恰有一把 RSA 密钥时才回退选取，避免"猜错密钥"。
        return keys.size() == 1 ? Optional.of(keys.values().iterator().next()) : Optional.empty();
    }

    /** 取当前可用密钥快照：TTL 过期则尝试刷新；刷新失败保留陈旧快照。 */
    private Map<String, RsaJwk> currentKeys() {
        if (isStale()) {
            refresh();
        }
        return snapshot;
    }

    private boolean isStale() {
        return Instant.now().isAfter(lastLoadedAt.plusSeconds(cacheTtlSeconds));
    }

    private synchronized void refresh() {
        // 双重检查：并发进入时若另一线程刚刷过，且仍未过期，则直接复用。
        if (!isStale()) {
            return;
        }
        try {
            HttpRequest request =
                    HttpRequest.newBuilder(jwksUri)
                            .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                            .GET()
                            .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                        "JWKS endpoint returned status " + response.statusCode());
            }
            Map<String, RsaJwk> parsed = parseJwks(response.body());
            this.snapshot = parsed;
            this.lastLoadedAt = Instant.now();
            log.info("Refreshed platform IdP JWKS from {} ({} RSA key(s))", jwksUri, parsed.size());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn(
                    "JWKS refresh interrupted, serving stale snapshot if any: {}", ie.getMessage());
        } catch (Exception ex) {
            // 不吞异常导致启动即不可用：有陈旧快照则继续用，否则 findKey 返回空 → 401（安全侧默认拒绝）。
            log.warn(
                    "JWKS refresh failed ({}), serving stale snapshot size={}",
                    ex.getMessage(),
                    snapshot.size());
        }
    }
}
