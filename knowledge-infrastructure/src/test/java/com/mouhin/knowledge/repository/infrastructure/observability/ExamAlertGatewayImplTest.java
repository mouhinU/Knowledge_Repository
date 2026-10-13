package com.mouhin.knowledge.repository.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExamAlertType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ExamAlertGatewayImpl} 契约单测：锁定双通道中的指标埋点与注册表缺失降级。
 *
 * <p>以真实轻量 {@link SimpleMeterRegistry} 注入（非 Spring、非 Mockito {@code @Mock}），验证 {@code
 * knowledge.exam.alert} 计数器按 {@code type} 标签精确自增，并覆盖 {@code meterRegistry == null} 时的静默降级路径。
 *
 * @author mouhinU
 * @date 2026-09-24 18:21:00
 */
@DisplayName("考试告警网关契约单测 (ExamAlertGatewayImpl)")
class ExamAlertGatewayImplTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ExamAlertGatewayImpl gateway = new ExamAlertGatewayImpl(registry);

    private double countOf(String tag) {
        Counter counter = registry.find("knowledge.exam.alert").tag("type", tag).counter();
        return counter == null ? 0.0 : counter.count();
    }

    @Test
    @DisplayName("validationFailed → 计数 knowledge.exam.alert{type=validation_failed} 自增 1")
    void validationFailedIncrementsTypedCounter() {
        gateway.validationFailed("sk-1", 3);

        assertThat(countOf("validation_failed")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("gradingTimeout → 计数 type=grading_timeout 独立于 validation_failed")
    void gradingTimeoutUsesDistinctTag() {
        gateway.gradingTimeout(42L);
        gateway.validationFailed("sk-1", 1);

        assertThat(countOf("grading_timeout")).isEqualTo(1.0);
        assertThat(countOf("validation_failed")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("answerKeyMissing → 计数 type=answer_key_missing 自增")
    void answerKeyMissingIncrements() {
        gateway.answerKeyMissing(7L, 2);

        assertThat(countOf("answer_key_missing")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("alert 多次调用 → 同一标签计数累加")
    void repeatedAlertAccumulatesSameTag() {
        gateway.lowQualityScore("sk-1", 40, 60);
        gateway.lowQualityScore("sk-1", 30, 60);

        assertThat(countOf("low_quality")).isEqualTo(2.0);
    }

    @Test
    @DisplayName("meterRegistry 为 null → 降级为仅日志，不抛异常")
    void nullRegistryDegradesGracefully() {
        ExamAlertGatewayImpl degraded = new ExamAlertGatewayImpl(null);

        assertThatCode(() -> degraded.alert(ExamAlertType.GRADING_TIMEOUT, "obj", "detail"))
                .doesNotThrowAnyException();
    }
}
