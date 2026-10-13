package com.mouhin.knowledge.repository.application.support;

import com.mouhin.knowledge.repository.domain.exception.QuotaExceededException;
import com.mouhin.knowledge.repository.domain.exception.ThrottleRequiredException;
import com.mouhin.knowledge.repository.domain.gateway.TenantPlanLimitGateway;
import com.mouhin.knowledge.repository.domain.gateway.TenantUsageRecorder;
import com.mouhin.knowledge.repository.domain.model.valueobject.QuotaAction;
import com.mouhin.knowledge.repository.domain.model.valueobject.QuotaCheckResult;
import com.mouhin.knowledge.repository.domain.model.valueobject.TenantPlanLimit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 配额前置拦截（P6-T3）。
 *
 * <p>在写入类 CmdExe {@code execute()} 最前面调用 {@link #check(String)}，事务外检查当月用量是否超限。 多 metric
 * 同时超额取最严策略（BLOCK &gt; THROTTLE &gt; ALERT）。
 *
 * <p>仅当 {@code knowledge.saas.quota.enabled=true} 时装配；关闭时 CmdExe 通过 {@code @Autowired(required =
 * false)} 注入为 null，不执行任何检查。
 */
@Component
@ConditionalOnProperty(name = "knowledge.saas.quota.enabled", havingValue = "true")
@Slf4j
public class QuotaGuard {

    private final TenantUsageRecorder usageRecorder;
    private final TenantPlanLimitGateway limitGateway;

    public QuotaGuard(TenantUsageRecorder usageRecorder, TenantPlanLimitGateway limitGateway) {
        this.usageRecorder = usageRecorder;
        this.limitGateway = limitGateway;
    }

    /**
     * 前置检查所有已配置 metric 的配额。
     *
     * @param tenantKey 归一化租户键
     * @throws QuotaExceededException    当最严策略为 BLOCK 时
     * @throws ThrottleRequiredException 当最严策略为 THROTTLE 时
     */
    public void check(String tenantKey) {
        if (tenantKey == null || tenantKey.isBlank()) {
            throw new IllegalArgumentException("tenantKey required for quota check");
        }
        LocalDate period = LocalDate.now().withDayOfMonth(1);
        List<TenantPlanLimit> limits = limitGateway.findByTenantKey(tenantKey);
        if (limits.isEmpty()) {
            return; // 无限额配置 = 无约束
        }

        QuotaCheckResult worst = null;
        for (TenantPlanLimit limit : limits) {
            if (limit.isUnlimited()) {
                continue;
            }
            long current = usageRecorder.getCurrentUsage(tenantKey, limit.metric(), period);
            QuotaCheckResult result = evaluate(tenantKey, limit, current);
            worst = QuotaCheckResult.merge(worst, result);
        }
        if (worst != null && worst.action() != QuotaAction.PASS) {
            handleViolation(worst);
        }
    }

    private QuotaCheckResult evaluate(String tenantKey, TenantPlanLimit limit, long current) {
        if (current >= limit.quota()) {
            return new QuotaCheckResult(
                    QuotaCheckResult.actionFromPolicy(limit.policy()),
                    tenantKey,
                    limit.metric(),
                    limit.quota(),
                    current);
        }
        return new QuotaCheckResult(
                QuotaAction.PASS, tenantKey, limit.metric(), limit.quota(), current);
    }

    private void handleViolation(QuotaCheckResult result) {
        switch (result.action()) {
            case BLOCK -> throw new QuotaExceededException(result);
            case THROTTLE -> throw new ThrottleRequiredException(result);
            case ALERT -> log.warn(
                    "[quota] ALERT tenant={} metric={} quota={} current={}",
                    result.tenantKey(),
                    result.metric(),
                    result.quota(),
                    result.currentUsage());
            default -> {
                // PASS, no-op
            }
        }
    }
}
