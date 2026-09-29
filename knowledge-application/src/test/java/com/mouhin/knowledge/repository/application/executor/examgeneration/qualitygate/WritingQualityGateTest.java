package com.mouhin.knowledge.repository.application.executor.examgeneration.qualitygate;

import static org.assertj.core.api.Assertions.assertThat;

import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.model.valueobject.QualityGateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * WritingQualityGate 单元测试
 *
 * @author mouhinU
 * @date 2026-09-28
 */
class WritingQualityGateTest {

    private WritingQualityGate gate;
    private BlackboardState blackboard;

    @BeforeEach
    void setUp() {
        gate = new WritingQualityGate();
        ReflectionTestUtils.setField(gate, "minLength", 50);
        blackboard = new BlackboardState("test-session", "测试问题");
        blackboard.advanceTo(BlackboardPhase.WRITING);
    }

    @Test
    void evaluate_shouldPass_whenPhaseIsNotWriting() {
        blackboard.advanceTo(BlackboardPhase.REVIEWING);

        QualityGateResult result = gate.evaluate(BlackboardPhase.REVIEWING, blackboard);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getGateName()).isEqualTo("writing-min-length");
    }

    @Test
    void evaluate_shouldFailWithRetry_whenExamPaperIsNull() {
        blackboard.setExamPaper(null);
        blackboard.setDraftArticle(null);

        QualityGateResult result = gate.evaluate(BlackboardPhase.WRITING, blackboard);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getSuggestedAction()).isEqualTo(QualityGateResult.Action.RETRY_CURRENT);
        assertThat(result.getReason()).contains("空");
    }

    @Test
    void evaluate_shouldFailWithRetry_whenExamPaperIsBlank() {
        blackboard.setExamPaper("   ");

        QualityGateResult result = gate.evaluate(BlackboardPhase.WRITING, blackboard);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getSuggestedAction()).isEqualTo(QualityGateResult.Action.RETRY_CURRENT);
    }

    @Test
    void evaluate_shouldFailWithRetry_whenOutputTooShort() {
        blackboard.setExamPaper("这是一段很短的内容");

        QualityGateResult result = gate.evaluate(BlackboardPhase.WRITING, blackboard);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getSuggestedAction()).isEqualTo(QualityGateResult.Action.RETRY_CURRENT);
        assertThat(result.getConfidence().getScore()).isEqualTo(0.2);
    }

    @Test
    void evaluate_shouldPassWithMediumConfidence_whenOutputMeetsMinLength() {
        // 60 chars >= 50 (minLength) but < 150 (3×minLength)
        String content = "A".repeat(60);
        blackboard.setExamPaper(content);

        QualityGateResult result = gate.evaluate(BlackboardPhase.WRITING, blackboard);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getConfidence().getScore()).isEqualTo(0.5);
        assertThat(result.getReason()).contains("60");
    }

    @Test
    void evaluate_shouldPassWithHighConfidence_whenOutputExceeds3xMinLength() {
        // 200 chars >= 150 (3×minLength)
        String content = "B".repeat(200);
        blackboard.setExamPaper(content);

        QualityGateResult result = gate.evaluate(BlackboardPhase.WRITING, blackboard);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getConfidence().getScore()).isEqualTo(0.9);
        assertThat(result.getReason()).contains("200");
    }

    @Test
    void evaluate_shouldFallbackToDraftArticle_whenExamPaperIsNull() {
        blackboard.setExamPaper(null);
        String draftContent = "C".repeat(100);
        blackboard.setDraftArticle(draftContent);

        QualityGateResult result = gate.evaluate(BlackboardPhase.WRITING, blackboard);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getReason()).contains("100");
    }

    @Test
    void evaluate_shouldPreferExamPaper_overDraftArticle() {
        blackboard.setExamPaper("D".repeat(80));
        blackboard.setDraftArticle("E".repeat(200));

        QualityGateResult result = gate.evaluate(BlackboardPhase.WRITING, blackboard);

        assertThat(result.isPassed()).isTrue();
        // 应使用 examPaper 长度 (80) 而非 draftArticle (200)
        assertThat(result.getReason()).contains("80");
    }

    @Test
    void evaluate_shouldPassExactlyAtMinLength() {
        String content = "F".repeat(50);
        blackboard.setExamPaper(content);

        QualityGateResult result = gate.evaluate(BlackboardPhase.WRITING, blackboard);

        assertThat(result.isPassed()).isTrue();
    }

    @Test
    void getName_shouldReturnGateName() {
        assertThat(gate.getName()).isEqualTo("writing-min-length");
    }
}
