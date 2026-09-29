package com.mouhin.knowledge.repository.application.executor.examgeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.BlackboardAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.BlackboardMetrics;
import com.mouhin.knowledge.repository.domain.service.BlackboardAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * AgentDecoratorRegistrar 单元测试
 *
 * @author mouhinU
 * @date 2026-09-28
 */
class AgentDecoratorRegistrarTest {

    private final BlackboardAuditLog auditLog = mock(BlackboardAuditLog.class);
    private final BlackboardMetrics metrics = mock(BlackboardMetrics.class);
    private final AgentDecoratorRegistrar registrar =
            new AgentDecoratorRegistrar(auditLog, metrics);

    private final BlackboardAgent agent = mock(BlackboardAgent.class);

    @BeforeEach
    void setUp() {
        when(agent.getName()).thenReturn("test-agent");
    }

    @Test
    void wrap_shouldReturnDecorator() {
        BlackboardAgent wrapped = registrar.wrap(agent);

        assertThat(wrapped).isInstanceOf(BlackboardAgentDecorator.class);
        assertThat(wrapped.getName()).isEqualTo("test-agent");
    }

    @Test
    void wrap_shouldBeIdempotent_whenAlreadyDecorated() {
        BlackboardAgent first = registrar.wrap(agent);
        BlackboardAgent second = registrar.wrap(first);

        assertThat(second).isSameAs(first);
    }

    @Test
    void wrap_shouldPreserveAgentName() {
        when(agent.getName()).thenReturn("examResearcherAgent");

        BlackboardAgent wrapped = registrar.wrap(agent);

        assertThat(wrapped.getName()).isEqualTo("examResearcherAgent");
    }
}
