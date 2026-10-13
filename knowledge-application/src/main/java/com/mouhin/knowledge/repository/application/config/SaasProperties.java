package com.mouhin.knowledge.repository.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SaaS 多租户总开关配置（{@code knowledge.saas.*}，P1-T2 / X-T2）。
 *
 * <p>{@code enabled} 是整个 Phase 1+ 的回退闸门（提案 §9）：默认 {@code false} 时， {@link
 * com.mouhin.knowledge.repository.application.support.TenantContextHolder} 恒返回默认租户，且 {@code
 * TenantResolverFilter}（P1-T3）与 {@code TenantLineInnerInterceptor}（P1-T7）应据此短路 / 不注册，运行行为与单租户版本等价。
 *
 * <p>关闭态即"零风险回退"：无需回滚迁移或代码，只需把该属性置回 {@code false} 重启。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "knowledge.saas")
public class SaasProperties {

    /**
     * 多租户总开关，默认关闭（单租户语义）。
     */
    private boolean enabled = false;

    /**
     * 子域名解析用的 DNS apex（如 {@code example.com}）。
     *
     * <p>{@code TenantResolverFilter}（P1-T3）据此从 {@code Host} 头反解 {@code {tenantKey}.apexDomain}
     * 形态的租户键； 留空表示关闭子域名解析（仅走 header / JWT claim）。
     */
    private String apexDomain = "";
}
