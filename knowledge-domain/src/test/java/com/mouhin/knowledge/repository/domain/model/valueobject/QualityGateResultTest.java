package com.mouhin.knowledge.repository.domain.model.valueobject;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * QualityGateResult 值对象测试
 *
 * @author mouhinU
 * @date 2026-09-28
 */
class QualityGateResultTest {

    @Test
    void shouldCreatePassResult() {
        QualityGateResult result = QualityGateResult.pass("test-gate", "all good");
        assertTrue(result.isPassed());
        assertEquals("test-gate", result.getGateName());
        assertEquals("all good", result.getReason());
        assertEquals(QualityGateResult.Action.CONTINUE, result.getSuggestedAction());
        assertNull(result.getConfidence());
    }

    @Test
    void shouldCreatePassResultWithConfidence() {
        AgentConfidence confidence = AgentConfidence.high("great");
        QualityGateResult result = QualityGateResult.pass("test-gate", "all good", confidence);
        assertTrue(result.isPassed());
        assertEquals(confidence, result.getConfidence());
    }

    @Test
    void shouldCreateFailResultWithRetryAction() {
        QualityGateResult result =
                QualityGateResult.fail(
                        "test-gate", "not enough data", QualityGateResult.Action.RETRY_CURRENT);
        assertFalse(result.isPassed());
        assertEquals(QualityGateResult.Action.RETRY_CURRENT, result.getSuggestedAction());
    }

    @Test
    void shouldCreateFailResultWithSkipAction() {
        QualityGateResult result =
                QualityGateResult.fail("test-gate", "non-critical", QualityGateResult.Action.SKIP);
        assertFalse(result.isPassed());
        assertEquals(QualityGateResult.Action.SKIP, result.getSuggestedAction());
    }

    @Test
    void shouldCreateFailResultWithTerminateAction() {
        QualityGateResult result =
                QualityGateResult.fail(
                        "test-gate", "critical failure", QualityGateResult.Action.TERMINATE);
        assertFalse(result.isPassed());
        assertEquals(QualityGateResult.Action.TERMINATE, result.getSuggestedAction());
    }

    @Test
    void shouldCreateFailResultWithConfidence() {
        AgentConfidence confidence = AgentConfidence.low("bad");
        QualityGateResult result =
                QualityGateResult.fail(
                        "test-gate", "failed", QualityGateResult.Action.TERMINATE, confidence);
        assertFalse(result.isPassed());
        assertEquals(confidence, result.getConfidence());
    }

    @Test
    void shouldHandleNullReason() {
        QualityGateResult result = QualityGateResult.pass("gate", null);
        assertEquals("", result.getReason());
    }

    @Test
    void shouldToStringIncludeAllFields() {
        QualityGateResult result = QualityGateResult.pass("my-gate", "ok");
        String str = result.toString();
        assertTrue(str.contains("my-gate"));
        assertTrue(str.contains("true"));
        assertTrue(str.contains("CONTINUE"));
    }
}
