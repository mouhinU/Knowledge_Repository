package com.mouhin.knowledge.repository.infrastructure.llm;

import com.mouhin.knowledge.repository.domain.constant.FeatureFlagKeys;
import com.mouhin.knowledge.repository.domain.gateway.LlmProfileGateway;
import com.mouhin.knowledge.repository.domain.gateway.SecretCipher;
import com.mouhin.knowledge.repository.domain.model.valueobject.LlmProfile;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 租户 LLM 配置解析器（P4-T4 · T-1 定案）。
 *
 * <p>缓存架构：
 *
 * <ul>
 *   <li><b>L1 本地热缓存</b>：{@link ConcurrentHashMap} 存解密后的 {@link LlmProfile}，TTL 30s；
 *   <li><b>DB 回源</b>：L1 miss 时走 {@link LlmProfileGateway#findByTenantId}，解密后回填 L1。
 * </ul>
 *
 * <p>跨实例失效：Phase 4 内 Redis pub/sub channel {@code kr:llm-profile:invalidate} 预留， 由 {@code
 * KeyRotationCmdExe} 在密钥轮换时广播。
 *
 * <p>解密失败处理（S-4 = 方案 C）：运行期解密失败 → 抛 {@link LlmProfileDecryptionException}， 上层拒绝调用 + 触发租户管理员告警 + 保留非
 * LLM 功能，不回退平台默认。
 *
 * <p>未配置 profile 时（S-5 = 方案 A）：{@link #resolve(long)} 返回 {@link Optional#empty()}， 上层走全局平台默认配置。
 *
 * @author mouhinU
 * @date 2026-10-11
 */
@Component
@Slf4j
@ConditionalOnProperty(name = FeatureFlagKeys.SAAS_ENABLED, havingValue = "true")
public class TenantLlmProfileResolver {

    private static final Duration L1_TTL = Duration.ofSeconds(30);

    private final LlmProfileGateway llmProfileGateway;
    private final SecretCipher secretCipher;

    /** L1 本地热缓存：tenantId → 解密后的 LlmProfile。 */
    private final ConcurrentHashMap<Long, CachedProfile> l1Cache = new ConcurrentHashMap<>();

    public TenantLlmProfileResolver(
            LlmProfileGateway llmProfileGateway, SecretCipher secretCipher) {
        this.llmProfileGateway = llmProfileGateway;
        this.secretCipher = secretCipher;
    }

    /**
     * 解析租户 LLM 配置。
     *
     * <p>L1 命中且未过期 → 直接返回；L1 miss / 过期 → DB 回源 + 解密 + 回填。 未配置 profile 返回 {@link
     * Optional#empty()}（上层走平台默认）。
     *
     * @param tenantId 租户数字 ID
     * @return 解密后的 LLM 配置；未配置返回 empty
     * @throws LlmProfileDecryptionException 解密失败时（S-4 方案 C）
     */
    public Optional<LlmProfile> resolve(long tenantId) {
        // L1 热缓存检查
        CachedProfile cached = l1Cache.get(tenantId);
        if (cached != null && !cached.isExpired()) {
            return Optional.ofNullable(cached.profile());
        }

        // DB 回源
        Optional<LlmProfile> fromDb = llmProfileGateway.findByTenantId(tenantId);
        if (fromDb.isEmpty()) {
            // 未配置 profile → S-5 方案 A：走平台默认
            l1Cache.put(tenantId, new CachedProfile(null, Instant.now()));
            return Optional.empty();
        }

        // DB 返回的 profile 中 apiKey 已经是解密后的明文（由 LlmProfileGatewayImpl 解密）
        LlmProfile profile = fromDb.get();
        l1Cache.put(tenantId, new CachedProfile(profile, Instant.now()));
        return Optional.of(profile);
    }

    /** 清除指定租户的 L1 缓存（供密钥轮换 / 配置变更时调用）。 */
    public void evict(long tenantId) {
        l1Cache.remove(tenantId);
        log.debug("[LlmProfile] L1 缓存已清除: tenantId={}", tenantId);
    }

    /** 清除全部 L1 缓存。 */
    public void evictAll() {
        l1Cache.clear();
        log.info("[LlmProfile] L1 缓存已全部清除");
    }

    /** L1 缓存条目。 */
    private record CachedProfile(LlmProfile profile, Instant loadedAt) {
        boolean isExpired() {
            return Duration.between(loadedAt, Instant.now()).compareTo(L1_TTL) > 0;
        }
    }

    /** LLM 配置解密失败异常（S-4 方案 C：拒绝调用 + 告警）。 */
    public static class LlmProfileDecryptionException extends RuntimeException {
        public LlmProfileDecryptionException(long tenantId, String message) {
            super("LLM profile decryption failed for tenant " + tenantId + ": " + message);
        }
    }
}
