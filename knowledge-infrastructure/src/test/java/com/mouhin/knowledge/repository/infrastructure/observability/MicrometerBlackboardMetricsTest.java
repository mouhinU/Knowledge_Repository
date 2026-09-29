package com.mouhin.knowledge.repository.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MicrometerBlackboardMetrics 单元测试
 *
 * <p>验证各指标正确注册到 Micrometer registry。
 *
 * @author mouhinU
 * @date 2026-09-28
 */
class MicrometerBlackboardMetricsTest {

    private MeterRegistry registry;
    private MicrometerBlackboardMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MicrometerBlackboardMetrics(registry);
    }

    @Test
    void recordAgentDuration_shouldRecordTimer() {
        metrics.recordAgentDuration("researcher", BlackboardPhase.RESEARCH, Duration.ofMillis(500));

        var timer =
                registry.find("knowledge.blackboard.agent.duration")
                        .tag("agent", "researcher")
                        .tag("phase", "RESEARCH")
                        .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(500.0);
    }

    @Test
    void incrementAgentCount_shouldIncrementCounter() {
        metrics.incrementAgentCount("writer", BlackboardPhase.WRITING, "COMPLETE");
        metrics.incrementAgentCount("writer", BlackboardPhase.WRITING, "COMPLETE");

        var counter =
                registry.find("knowledge.blackboard.agent.total")
                        .tag("agent", "writer")
                        .tag("phase", "WRITING")
                        .tag("status", "COMPLETE")
                        .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    void incrementQualityGateCount_shouldTrackPassedAndFailed() {
        metrics.incrementQualityGateCount("research-min-chunks", BlackboardPhase.RESEARCH, true);
        metrics.incrementQualityGateCount("research-min-chunks", BlackboardPhase.RESEARCH, false);

        var passedCounter =
                registry.find("knowledge.blackboard.quality_gate.total")
                        .tag("gate", "research-min-chunks")
                        .tag("phase", "RESEARCH")
                        .tag("result", "passed")
                        .counter();
        assertThat(passedCounter).isNotNull();
        assertThat(passedCounter.count()).isEqualTo(1.0);

        var failedCounter =
                registry.find("knowledge.blackboard.quality_gate.total")
                        .tag("gate", "research-min-chunks")
                        .tag("phase", "RESEARCH")
                        .tag("result", "failed")
                        .counter();
        assertThat(failedCounter).isNotNull();
        assertThat(failedCounter.count()).isEqualTo(1.0);
    }

    @Test
    void recordPipelineDuration_shouldRecordTimer() {
        metrics.recordPipelineDuration(Duration.ofSeconds(30));

        var timer = registry.find("knowledge.blackboard.pipeline.duration").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void recordAgentTokenCount_shouldIncrementCounter() {
        metrics.recordAgentTokenCount("researcher", 150);
        metrics.recordAgentTokenCount("researcher", 50);

        var counter =
                registry.find("knowledge.blackboard.agent.tokens")
                        .tag("agent", "researcher")
                        .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(200.0);
    }

    @Test
    void recordFinalQualityScore_shouldUpdateGauge() {
        metrics.recordFinalQualityScore(85.5);

        var gauge = registry.find("knowledge.blackboard.pipeline.quality_score").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(85.5);
    }

    @Test
    void recordFinalQualityScore_shouldOverwritePreviousValue() {
        metrics.recordFinalQualityScore(70.0);
        metrics.recordFinalQualityScore(90.0);

        var gauge = registry.find("knowledge.blackboard.pipeline.quality_score").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(90.0);
    }

    @Test
    void nullAgentName_shouldDefaultToNone() {
        metrics.recordAgentDuration(null, BlackboardPhase.RESEARCH, Duration.ofMillis(100));

        var timer =
                registry.find("knowledge.blackboard.agent.duration")
                        .tag("agent", "none")
                        .tag("phase", "RESEARCH")
                        .timer();
        assertThat(timer).isNotNull();
    }

    @Test
    void blankAgentName_shouldDefaultToNone() {
        metrics.incrementAgentCount("  ", BlackboardPhase.WRITING, "COMPLETE");

        var counter =
                registry.find("knowledge.blackboard.agent.total").tag("agent", "none").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
}
