package com.mouhin.knowledge.repository.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 平台端管理总开关配置（{@code knowledge.platform.*}，P3-T1 / T-4 方案 B）。
 *
 * <p>{@code enabled} 是平台生命周期端点的第二道闸门（与 {@code knowledge.saas.enabled} 双门控）：默认 {@code false} 时，
 * {@code PlatformTokenAuthFilter} 与 {@code PlatformTenantController}
 * 均不注册（{@code @ConditionalOnProperty}）， {@code /api/platform/**} 整体不可达，运行行为与未引入平台端等价——满足「零风险回退」。
 *
 * <p>开启态（{@code true}）下平台端点由独立 {@code knowledge.platform.jwt.secret} 签发的令牌保护， 与租户管理员令牌体系完全隔离（T-4 方案
 * B）。密钥缺失时 {@code PlatformJwtGatewayImpl} 构造即 fail-fast。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "knowledge.platform")
public class PlatformProperties {

    /** 平台端管理总开关，默认关闭（端点不注册）。 */
    private boolean enabled = false;
}
