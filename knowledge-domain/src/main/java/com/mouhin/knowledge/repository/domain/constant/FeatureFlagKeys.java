package com.mouhin.knowledge.repository.domain.constant;

/**
 * 运行时特性门控键常量。
 *
 * <p>SaaS / 平台 / Redis 等特性闸门的 Spring 属性键，统一在此维护，供 {@code @ConditionalOnProperty(name = ...)} 引用
 * （编译期常量，可用于注解），禁止在各模块内联硬编码（AGENTS.md 红线 #2）。此前 {@code "knowledge.platform.enabled"} 等在
 * infra/web/app 的 13+ 文件重复约 23 处，一处拼错即静默解除门控。
 *
 * <p>所有闸门默认关闭（{@code matchIfMissing = false}），仅 {@link #REDIS_ENABLED} 默认开启。
 */
public final class FeatureFlagKeys {

    private FeatureFlagKeys() {}

    /** SaaS 多租户总开关。 */
    public static final String SAAS_ENABLED = "knowledge.saas.enabled";

    /** 配额与计费开关（Phase 6）。 */
    public static final String SAAS_QUOTA_ENABLED = "knowledge.saas.quota.enabled";

    /** 平台管理端开关。 */
    public static final String PLATFORM_ENABLED = "knowledge.platform.enabled";

    /** 平台端企业 IdP OIDC 接入开关（P3-T7）。 */
    public static final String PLATFORM_OIDC_ENABLED = "knowledge.platform.oidc.enabled";

    /** Redis 开关（分布式缓存失效 / 上传会话 / 分布式锁），默认开启。 */
    public static final String REDIS_ENABLED = "knowledge.redis.enabled";
}
