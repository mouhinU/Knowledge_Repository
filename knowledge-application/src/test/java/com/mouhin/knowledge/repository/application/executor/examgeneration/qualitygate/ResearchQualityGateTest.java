package com.mouhin.knowledge.repository.application.executor.examgeneration.qualitygate;

import static org.assertj.core.api.Assertions.assertThat;

import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.model.valueobject.QualityGateResult;
import com.mouhin.knowledge.repository.domain.model.valueobject.SearchResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * ResearchQualityGate 单元测试
 *
 * @author mouhinU
 * @date 2026-09-28
 */
class ResearchQualityGateTest {

    private ResearchQualityGate gate;
    private BlackboardState blackboard;

    @BeforeEach
    void setUp() {
        gate = new ResearchQualityGate();
        ReflectionTestUtils.setField(gate, "minChunks", 1);
        ReflectionTestUtils.setField(gate, "lowConfidenceThreshold", 3);
        blackboard = new BlackboardState("test-session", "测试问题");
        blackboard.advanceTo(BlackboardPhase.RESEARCH);
    }

    @Test
    void evaluate_shouldPass_whenPhaseIsNotResearch() {
        blackboard.advanceTo(BlackboardPhase.WRITING);

        QualityGateResult result = gate.evaluate(BlackboardPhase.WRITING, blackboard);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getGateName()).isEqualTo("research-min-chunks");
    }

    @Test
    void evaluate_shouldPassWithHighConfidence_whenChunksAboveLowConfidenceThreshold() {
        List<SearchResult> chunks = createChunks(5);
        blackboard.setKnowledgeChunks(chunks);

        QualityGateResult result = gate.evaluate(BlackboardPhase.RESEARCH, blackboard);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getConfidence()).isNotNull();
        assertThat(result.getConfidence().getScore()).isEqualTo(0.9);
        assertThat(result.getReason()).contains("5");
    }

    @Test
    void evaluate_shouldPassWithMediumConfidence_whenChunksBetweenMinAndLowThreshold() {
        List<SearchResult> chunks = createChunks(2);
        blackboard.setKnowledgeChunks(chunks);

        QualityGateResult result = gate.evaluate(BlackboardPhase.RESEARCH, blackboard);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getConfidence()).isNotNull();
        assertThat(result.getConfidence().getScore()).isEqualTo(0.5);
        assertThat(result.getReason()).contains("2");
    }

    @Test
    void evaluate_shouldFailWithSkip_whenZeroChunks() {
        blackboard.setKnowledgeChunks(new ArrayList<>());

        QualityGateResult result = gate.evaluate(BlackboardPhase.RESEARCH, blackboard);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getSuggestedAction()).isEqualTo(QualityGateResult.Action.SKIP);
        assertThat(result.getConfidence()).isNotNull();
        assertThat(result.getConfidence().getScore()).isEqualTo(0.2);
        assertThat(result.getReason()).contains("0 条");
    }

    @Test
    void evaluate_shouldFailWithRetry_whenChunksBelowMinChunks() {
        // minChunks=1, 0 chunks → SKIP (not RETRY)
        // 调高 minChunks 使 chunkCount > 0 但 < minChunks
        ReflectionTestUtils.setField(gate, "minChunks", 3);
        blackboard.setKnowledgeChunks(createChunks(1));

        QualityGateResult result = gate.evaluate(BlackboardPhase.RESEARCH, blackboard);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getSuggestedAction()).isEqualTo(QualityGateResult.Action.RETRY_CURRENT);
        assertThat(result.getConfidence()).isNotNull();
        assertThat(result.getConfidence().getScore()).isEqualTo(0.2);
    }

    @Test
    void evaluate_shouldHandleNullKnowledgeChunks() {
        blackboard.setKnowledgeChunks(null);

        QualityGateResult result = gate.evaluate(BlackboardPhase.RESEARCH, blackboard);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getSuggestedAction()).isEqualTo(QualityGateResult.Action.SKIP);
    }

    @Test
    void getName_shouldReturnGateName() {
        assertThat(gate.getName()).isEqualTo("research-min-chunks");
    }

    @Test
    void evaluate_shouldPassExactlyAtMinChunks() {
        ReflectionTestUtils.setField(gate, "minChunks", 2);
        blackboard.setKnowledgeChunks(createChunks(2));

        QualityGateResult result = gate.evaluate(BlackboardPhase.RESEARCH, blackboard);

        assertThat(result.isPassed()).isTrue();
    }

    @Test
    void evaluate_shouldPassExactlyAtLowConfidenceThreshold() {
        blackboard.setKnowledgeChunks(createChunks(3));

        QualityGateResult result = gate.evaluate(BlackboardPhase.RESEARCH, blackboard);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getConfidence().getScore()).isEqualTo(0.9);
    }

    private List<SearchResult> createChunks(int count) {
        List<SearchResult> chunks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            chunks.add(new SearchResult("text-" + i, "doc-1", "文档1", 1, i, 0.8));
        }
        return chunks;
    }
}
