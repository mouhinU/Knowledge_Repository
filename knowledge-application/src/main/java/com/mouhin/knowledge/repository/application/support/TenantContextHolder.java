package com.mouhin.knowledge.repository.application.support;

import com.mouhin.knowledge.repository.application.config.SaasProperties;
import com.mouhin.knowledge.repository.domain.context.TenantContext;
import org.springframework.stereotype.Component;

/**
 * 租户上下文持有器（P1-T2，app 层策略门）。
 *
 * <p>把"纯存储"的 {@link TenantContext}（domain）与"是否启用多租户"的 {@link SaasProperties} 收敛到一个可注入的门面， 供
 * adapter（{@code TenantResolverFilter}，P1-T3）与后续 Executor / Scheduler 统一读取，避免各处重复判开关。
 *
 * <p>语义：
 *
 * <ul>
 *   <li>{@code saas.enabled=false}（默认）：{@link #getTenantId()} 恒返回 {@link
 *       TenantContext#DEFAULT_TENANT_KEY}，即便线程上残留绑定也忽略——保证关闭态与单租户行为等价（回退闸门，提案 §9）。
 *   <li>{@code saas.enabled=true}：取 {@link TenantContext#getOrDefault()}，未绑定时回落 default。
 * </ul>
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@Component
public class TenantContextHolder {

    private final SaasProperties saasProperties;

    public TenantContextHolder(SaasProperties saasProperties) {
        this.saasProperties = saasProperties;
    }

    /** 多租户是否启用。 */
    public boolean isEnabled() {
        return saasProperties.isEnabled();
    }

    /** 当前生效的租户键：关闭多租户时恒为 default；开启时取上下文（未绑定回落 default）。 */
    public String getTenantId() {
        if (!saasProperties.isEnabled()) {
            return TenantContext.DEFAULT_TENANT_KEY;
        }
        return TenantContext.getOrDefault();
    }

    /** 绑定当前线程租户（仅多租户启用时有效）。 */
    public void bind(String tenantKey) {
        if (saasProperties.isEnabled()) {
            TenantContext.set(tenantKey);
        }
    }

    /** 清除当前线程租户绑定（须在 {@code finally} 调用）。 */
    public void clear() {
        TenantContext.clear();
    }
}
