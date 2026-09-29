package com.mouhin.knowledge.repository.application.executor.examgeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.BlackboardAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.BlackboardMetrics;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.service.BlackboardAgent;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * BlackboardAgentDecorator 单元测试
 *
 * <p>验证装饰器透明代理 delegate 的 execute/getName，并在执行前后正确记录审计日志和指标。
 *
 * @author mouhinU
 * @date 2026-09-28
 */
class BlackboardAgentDecoratorTest {

    private final BlackboardAgent delegate = mock(BlackboardAgent.class);
    private final BlackboardAuditLog auditLog = mock(BlackboardAuditLog.class);
    private final BlackboardMetrics metrics = mock(BlackboardMetrics.class);

    private BlackboardAgentDecorator decorator;
    private BlackboardState blackboard;

    @BeforeEach
    void setUp() {
        decorator = new BlackboardAgentDecorator(delegate, auditLog, metrics);
        blackboard = new BlackboardState("test-session", "测试问题");
        blackboard.advanceTo(BlackboardPhase.RESEARCH);
        when(delegate.getName()).thenReturn("test-agent");
    }

    @Test
    void execute_shouldDelegateToWrappedAgent() {
        decorator.execute(blackboard, null);

        verify(delegate).execute(eq(blackboard), any());
    }

    @Test
    void execute_shouldLogStartAndComplete_onSuccess() {
        decorator.execute(blackboard, null);

        verify(auditLog)
                .logAgentStart(
                        eq("test-session"), eq("test-agent"), eq(BlackboardPhase.RESEARCH), any());
        verify(auditLog)
                .logAgentComplete(
                        eq("test-session"),
                        eq("test-agent"),
                        any(),
                        any(Duration.class),
                        eq(null),
                        eq(null),
                        any());
    }

    @Test
    void execute_shouldRecordMetrics_onSuccess() {
        decorator.execute(blackboard, null);

        verify(metrics)
                .incrementAgentCount(eq("test-agent"), eq(BlackboardPhase.RESEARCH), eq("STARTED"));
        verify(metrics).recordAgentDuration(eq("test-agent"), any(), any(Duration.class));
        verify(metrics).incrementAgentCount(eq("test-agent"), any(), eq("COMPLETE"));
    }

    @Test
    void execute_shouldLogFailed_onException() {
        RuntimeException ex = new RuntimeException("LLM 超时");
        doThrow(ex).when(delegate).execute(any(), any());

        assertThatThrownBy(() -> decorator.execute(blackboard, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("LLM 超时");

        verify(auditLog)
                .logAgentFailed(
                        eq("test-session"),
                        eq("test-agent"),
                        eq(BlackboardPhase.RESEARCH),
                        any(Duration.class),
                        eq("LLM 超时"));
        verify(metrics).incrementAgentCount("test-agent", BlackboardPhase.RESEARCH, "FAILED");
    }

    @Test
    void execute_shouldRethrowException() {
        doThrow(new IllegalStateException("bad state")).when(delegate).execute(any(), any());

        assertThatThrownBy(() -> decorator.execute(blackboard, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("bad state");
    }

    @Test
    void getName_shouldDelegateToWrappedAgent() {
        assertThat(decorator.getName()).isEqualTo("test-agent");
        verify(delegate).getName();
    }

    @Test
    void getDelegate_shouldReturnOriginalAgent() {
        assertThat(decorator.getDelegate()).isSameAs(delegate);
    }

    @Test
    void execute_shouldPassCallbackToDelegate() {
        BlackboardProgressCallback callback = mock(BlackboardProgressCallback.class);

        decorator.execute(blackboard, callback);

        verify(delegate).execute(eq(blackboard), eq(callback));
    }
}
