package com.mouhin.knowledge.repository.domain.model.valueobject;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * AgentConfidence 值对象测试
 *
 * @author mouhinU
 * @date 2026-09-28
 */
class AgentConfidenceTest {

    @Test
    void shouldCreateWithValidScore() {
        AgentConfidence confidence = new AgentConfidence(0.8, "good result");
        assertEquals(0.8, confidence.getScore());
        assertEquals("good result", confidence.getReason());
        assertTrue(confidence.isAcceptable());
    }

    @Test
    void shouldRejectScoreBelowZero() {
        assertThrows(IllegalArgumentException.class, () -> new AgentConfidence(-0.1, "invalid"));
    }

    @Test
    void shouldRejectScoreAboveOne() {
        assertThrows(IllegalArgumentException.class, () -> new AgentConfidence(1.1, "invalid"));
    }

    @Test
    void shouldDefaultThresholdBeZeroPointThree() {
        AgentConfidence low = new AgentConfidence(0.2, "low");
        assertFalse(low.isAcceptable());

        AgentConfidence mid = new AgentConfidence(0.3, "threshold");
        assertTrue(mid.isAcceptable());
    }

    @Test
    void shouldSupportCustomThreshold() {
        AgentConfidence confidence = new AgentConfidence(0.5, "medium", 0.6);
        assertFalse(confidence.isAcceptable());
    }

    @Test
    void shouldCreateHighConfidence() {
        AgentConfidence high = AgentConfidence.high("excellent");
        assertEquals(0.9, high.getScore());
        assertTrue(high.isAcceptable());
    }

    @Test
    void shouldCreateMediumConfidence() {
        AgentConfidence medium = AgentConfidence.medium("okay");
        assertEquals(0.5, medium.getScore());
        assertTrue(medium.isAcceptable());
    }

    @Test
    void shouldCreateLowConfidence() {
        AgentConfidence low = AgentConfidence.low("poor");
        assertEquals(0.2, low.getScore());
        assertFalse(low.isAcceptable());
    }

    @Test
    void shouldCreateUnknownConfidence() {
        AgentConfidence unknown = AgentConfidence.unknown();
        assertEquals(0.0, unknown.getScore());
        assertEquals("未评估", unknown.getReason());
        assertFalse(unknown.isAcceptable());
    }

    @Test
    void shouldHandleNullReason() {
        AgentConfidence confidence = new AgentConfidence(0.5, null);
        assertEquals("", confidence.getReason());
    }

    @Test
    void shouldToStringIncludeAllFields() {
        AgentConfidence confidence = new AgentConfidence(0.75, "test reason");
        String str = confidence.toString();
        assertTrue(str.contains("0.75"));
        assertTrue(str.contains("test reason"));
        assertTrue(str.contains("true"));
    }
}
