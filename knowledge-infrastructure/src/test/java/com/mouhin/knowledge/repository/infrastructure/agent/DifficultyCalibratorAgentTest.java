package com.mouhin.knowledge.repository.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * DifficultyCalibratorAgent 单元测试
 *
 * <p>覆盖：正常执行、空试卷短路、LLM 空返回兜底、难度描述映射（EASY/MEDIUM/HARD/null）、进度事件。
 *
 * @author mouhinU
 * @date 2026-09-28
 */
@DisplayName("Bloom 认知层级校准 Agent (DifficultyCalibratorAgent)")
class DifficultyCalibratorAgentTest {

    private final BlackboardAgentStreamer agentStreamer = mock(BlackboardAgentStreamer.class);
    private final BlackboardProgressCallback callback = mock(BlackboardProgressCallback.class);

    private DifficultyCalibratorAgent agent;
    private BlackboardState blackboard;

    @BeforeEach
    void setUp() {
        agent = new DifficultyCalibratorAgent(agentStreamer);
        blackboard = new BlackboardState("test-session", "项目管理");
        blackboard.setExamPaper(
                "## 一、单选题\n1. 项目管理中，WBS 的全称是？\nA. Work Breakdown Structure  B. Work Build System");
        blackboard.setExamDifficulty("MEDIUM");
    }

    @Test
    @DisplayName("正常执行：LLM 返回有效分类 → 写入 blackboard + 推送完成事件")
    void execute_shouldSetClassificationAndEmitCompleted_whenLlmReturnsValidResult() {
        String fakeClassification =
                "## Bloom 分类明细\n| 1 | WBS全称 | 记忆 | 回忆术语 |\n\n## 层级分布\n| 记忆 | 1 | 100% | 25% | +75% |\n\n## 校准结论\n偏差较大\n\n## 调整建议\n增加应用题";
        when(agentStreamer.stream(eq("bloom-calibrator"), any(), any(), eq(callback)))
                .thenReturn(fakeClassification);

        agent.execute(blackboard, callback);

        assertThat(blackboard.getBloomClassification()).isEqualTo(fakeClassification);
        assertThat(blackboard.getPhase()).isEqualTo(BlackboardPhase.BLOOM_CALIBRATING);

        ArgumentCaptor<BlackboardProgressEvent> captor =
                ArgumentCaptor.forClass(BlackboardProgressEvent.class);
        verify(callback, org.mockito.Mockito.atLeast(2)).onProgress(captor.capture());
        List<BlackboardProgressEvent> events = captor.getAllValues();
        assertThat(events.get(0).getAgentName()).isEqualTo("bloom-calibrator");
    }

    @Test
    @DisplayName("空试卷 → 短路返回")
    void execute_shouldShortCircuit_whenExamPaperIsBlank() {
        blackboard.setExamPaper("");

        agent.execute(blackboard, callback);

        assertThat(blackboard.getBloomClassification()).isEqualTo("试卷为空，无法进行 Bloom 分类。");
        verify(agentStreamer, never()).stream(any(), any(), any(), any());
    }

    @Test
    @DisplayName("null 试卷 → 短路返回")
    void execute_shouldShortCircuit_whenExamPaperIsNull() {
        blackboard.setExamPaper(null);

        agent.execute(blackboard, callback);

        assertThat(blackboard.getBloomClassification()).isEqualTo("试卷为空，无法进行 Bloom 分类。");
    }

    @Test
    @DisplayName("LLM 返回 null → 使用兜底报告")
    void execute_shouldUseFallback_whenLlmReturnsNull() {
        when(agentStreamer.stream(eq("bloom-calibrator"), any(), any(), eq(callback)))
                .thenReturn(null);

        agent.execute(blackboard, callback);

        assertThat(blackboard.getBloomClassification()).contains("分类异常");
    }

    @Test
    @DisplayName("EASY 难度 → 用户提示包含'记忆 30%'")
    void execute_shouldIncludeEasyDistribution_whenDifficultyIsEasy() {
        blackboard.setExamDifficulty("EASY");
        when(agentStreamer.stream(eq("bloom-calibrator"), any(), any(), eq(callback)))
                .thenReturn("## Bloom 分类明细\nOK\n\n## 层级分布\nOK\n\n## 校准结论\nOK\n\n## 调整建议\n无");

        agent.execute(blackboard, callback);

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentStreamer).stream(
                eq("bloom-calibrator"), any(), userPromptCaptor.capture(), eq(callback));
        assertThat(userPromptCaptor.getValue()).contains("记忆 30%");
    }

    @Test
    @DisplayName("HARD 难度 → 用户提示包含'应用 25%'")
    void execute_shouldIncludeHardDistribution_whenDifficultyIsHard() {
        blackboard.setExamDifficulty("HARD");
        when(agentStreamer.stream(eq("bloom-calibrator"), any(), any(), eq(callback)))
                .thenReturn("## Bloom 分类明细\nOK\n\n## 层级分布\nOK\n\n## 校准结论\nOK\n\n## 调整建议\n无");

        agent.execute(blackboard, callback);

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentStreamer).stream(
                eq("bloom-calibrator"), any(), userPromptCaptor.capture(), eq(callback));
        assertThat(userPromptCaptor.getValue()).contains("应用 25%");
    }

    @Test
    @DisplayName("null 难度 → 默认 MEDIUM 描述")
    void execute_shouldDefaultToMedium_whenDifficultyIsNull() {
        blackboard.setExamDifficulty(null);
        when(agentStreamer.stream(eq("bloom-calibrator"), any(), any(), eq(callback)))
                .thenReturn("## Bloom 分类明细\nOK\n\n## 层级分布\nOK\n\n## 校准结论\nOK\n\n## 调整建议\n无");

        agent.execute(blackboard, callback);

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentStreamer).stream(
                eq("bloom-calibrator"), any(), userPromptCaptor.capture(), eq(callback));
        assertThat(userPromptCaptor.getValue()).contains("理解 25%");
    }

    @Test
    @DisplayName("callback 为 null → 不抛异常")
    void execute_shouldNotThrow_whenCallbackIsNull() {
        when(agentStreamer.stream(eq("bloom-calibrator"), any(), any(), eq(null)))
                .thenReturn("## Bloom 分类明细\nOK\n\n## 层级分布\nOK\n\n## 校准结论\nOK\n\n## 调整建议\n无");

        agent.execute(blackboard, null);

        assertThat(blackboard.getBloomClassification()).isNotBlank();
    }

    @Test
    @DisplayName("getName 返回 DifficultyCalibrator")
    void getName_shouldReturnExpectedName() {
        assertThat(agent.getName()).isEqualTo("DifficultyCalibrator");
    }

    @Test
    @DisplayName("评分：四段结构完整 → 高分")
    void execute_shouldGiveHighScore_whenClassificationHasAllSections() {
        String fullClassification =
                "## Bloom 分类明细\n"
                        + "| 1 | 题目 | 记忆 | 依据 |\n".repeat(20)
                        + "\n## 层级分布\n分布OK\n\n## 校准结论\n结论OK\n\n## 调整建议\n建议OK";
        when(agentStreamer.stream(eq("bloom-calibrator"), any(), any(), eq(callback)))
                .thenReturn(fullClassification);

        agent.execute(blackboard, callback);

        ArgumentCaptor<BlackboardProgressEvent> captor =
                ArgumentCaptor.forClass(BlackboardProgressEvent.class);
        verify(callback, org.mockito.Mockito.atLeast(1)).onProgress(captor.capture());
        BlackboardProgressEvent completedEvent =
                captor.getAllValues().stream()
                        .filter(e -> e.getAgentScore() != null)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        assertThat(completedEvent.getAgentScore()).isGreaterThanOrEqualTo(80.0);
    }
}
