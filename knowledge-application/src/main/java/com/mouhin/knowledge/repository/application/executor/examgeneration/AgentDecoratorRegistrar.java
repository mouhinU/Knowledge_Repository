package com.mouhin.knowledge.repository.application.executor.examgeneration;

import com.mouhin.knowledge.repository.domain.gateway.BlackboardAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.BlackboardMetrics;
import com.mouhin.knowledge.repository.domain.service.BlackboardAgent;
import org.springframework.stereotype.Component;

/**
 * Agent 装饰器工厂
 *
 * <p>编排层（app）通过此组件将任意 {@link BlackboardAgent} 包装为带审计 + 指标采集的装饰版本。 装饰器透明代理原 Agent 的 {@code execute}
 * 和 {@code getName}， 在执行前后自动记录审计日志与 Micrometer 指标。
 *
 * <p>用法示例：
 *
 * <pre>{@code
 * public ExamGenerationSupport(..., AgentDecoratorRegistrar registrar, BlackboardAgent agent) {
 *     this.agent = registrar.wrap(agent);
 * }
 * }</pre>
 *
 * @author mouhinU
 * @date 2026-09-28
 */
@Component
public class AgentDecoratorRegistrar {

    private final BlackboardAuditLog auditLog;
    private final BlackboardMetrics metrics;

    public AgentDecoratorRegistrar(BlackboardAuditLog auditLog, BlackboardMetrics metrics) {
        this.auditLog = auditLog;
        this.metrics = metrics;
    }

    /** 将 Agent 包装为带审计 + 指标的装饰版本。幂等：若已是装饰器则直接返回。 */
    public BlackboardAgent wrap(BlackboardAgent agent) {
        if (agent instanceof BlackboardAgentDecorator) {
            return agent;
        }
        return new BlackboardAgentDecorator(agent, auditLog, metrics);
    }
}
