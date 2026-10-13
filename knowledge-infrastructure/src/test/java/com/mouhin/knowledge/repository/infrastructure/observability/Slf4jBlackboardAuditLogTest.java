package com.mouhin.knowledge.repository.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.mouhin.knowledge.repository.domain.model.valueobject.AgentConfidence;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Slf4jBlackboardAuditLog 单元测试
 *
 * <p>验证审计日志方法不抛异常、sanitize 逻辑正确。
 *
 * @author mouhinU
 * @date 2026-09-28
 */
class Slf4jBlackboardAuditLogTest {

    private final Slf4jBlackboardAuditLog auditLog = new Slf4jBlackboardAuditLog();

    @Test
    void logAgentStart_shouldNotThrow() {
        auditLog.logAgentStart("session-1", "researcher", BlackboardPhase.RESEARCH, "检索知识库");

        // 不抛异常即通过
    }

    @Test
    void logAgentStart_shouldHandleNullInputSummary() {
        auditLog.logAgentStart("session-1", "researcher", BlackboardPhase.RESEARCH, null);
    }

    @Test
    void logAgentComplete_shouldNotThrow_withAllFields() {
        AgentConfidence confidence = AgentConfidence.high("检索充分");

        auditLog.logAgentComplete(
                "session-1",
                "researcher",
                BlackboardPhase.RESEARCH,
                Duration.ofMillis(1500),
                200,
                confidence,
                "找到5条结果");
    }

    @Test
    void logAgentComplete_shouldNotThrow_withNullOptionalFields() {
        auditLog.logAgentComplete(
                "session-1",
                "writer",
                BlackboardPhase.WRITING,
                Duration.ofSeconds(3),
                null,
                null,
                "生成完毕");
    }

    @Test
    void logAgentFailed_shouldNotThrow() {
        auditLog.logAgentFailed(
                "session-1",
                "reviewer",
                BlackboardPhase.REVIEWING,
                Duration.ofMillis(800),
                "LLM 超时");
    }

    @Test
    void logAgentFailed_shouldHandleNullError() {
        auditLog.logAgentFailed(
                "session-1", "reviewer", BlackboardPhase.REVIEWING, Duration.ofMillis(800), null);
    }

    @Test
    void logQualityGate_shouldNotThrow() {
        auditLog.logQualityGate(
                "session-1", "research-min-chunks", BlackboardPhase.RESEARCH, true, "通过");
    }

    @Test
    void logPipelineComplete_shouldNotThrow_withAllFields() {
        auditLog.logPipelineComplete("session-1", Duration.ofSeconds(30), 1500, 85.50, 7);
    }

    @Test
    void logPipelineComplete_shouldNotThrow_withNullTokenCount() {
        auditLog.logPipelineComplete("session-1", Duration.ofSeconds(30), null, 85.50, 7);
    }

    @Test
    void sanitize_shouldEscapeNewlines() throws Exception {
        // 通过反射测试 sanitize 方法
        java.lang.reflect.Method method =
                Slf4jBlackboardAuditLog.class.getDeclaredMethod("sanitize", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(auditLog, "line1\nline2\rline3");
        assertThat(result).isEqualTo("line1\\nline2\\rline3");
    }

    @Test
    void sanitize_shouldEscapeQuotes() throws Exception {
        java.lang.reflect.Method method =
                Slf4jBlackboardAuditLog.class.getDeclaredMethod("sanitize", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(auditLog, "say \"hello\"");
        assertThat(result).isEqualTo("say \\\"hello\\\"");
    }

    @Test
    void sanitize_shouldTruncateLongText() throws Exception {
        java.lang.reflect.Method method =
                Slf4jBlackboardAuditLog.class.getDeclaredMethod("sanitize", String.class);
        method.setAccessible(true);

        String longText = "A".repeat(300);
        String result = (String) method.invoke(auditLog, longText);
        assertThat(result).hasSize(200);
    }

    @Test
    void sanitize_shouldReturnEmptyForNull() throws Exception {
        java.lang.reflect.Method method =
                Slf4jBlackboardAuditLog.class.getDeclaredMethod("sanitize", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(auditLog, (String) null);
        assertThat(result).isEmpty();
    }
}
