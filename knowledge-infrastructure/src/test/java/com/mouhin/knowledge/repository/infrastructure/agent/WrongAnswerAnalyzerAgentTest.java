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
 * WrongAnswerAnalyzerAgent 单元测试
 *
 * <p>覆盖：正常执行、空试卷短路、LLM 空返回兜底、answerKey 缺失时的处理、进度事件推送。
 *
 * @author mouhinU
 * @date 2026-09-28
 */
@DisplayName("错题模式分析 Agent (WrongAnswerAnalyzerAgent)")
class WrongAnswerAnalyzerAgentTest {

    private final BlackboardAgentStreamer agentStreamer = mock(BlackboardAgentStreamer.class);
    private final BlackboardProgressCallback callback = mock(BlackboardProgressCallback.class);

    private WrongAnswerAnalyzerAgent agent;
    private BlackboardState blackboard;

    @BeforeEach
    void setUp() {
        agent = new WrongAnswerAnalyzerAgent(agentStreamer);
        blackboard = new BlackboardState("test-session", "数学期末考试");
        blackboard.setExamPaper(
                "## 一、单选题\n1. 2+3=?\nA. 4  B. 5  C. 6  D. 7\n\n## 二、填空题\n2. 3×4=___");
        blackboard.setAnswerKey("1. B\n2. 12");
    }

    @Test
    @DisplayName("正常执行：LLM 返回有效分析 → 写入 blackboard + 推送完成事件")
    void execute_shouldSetAnalysisAndEmitCompleted_whenLlmReturnsValidResult() {
        String fakeAnalysis =
                "## 错题清单\n| 1 | 加法 | 粗心失误 | 选A | B |\n\n## 薄弱知识点\n1. 加法运算 — 错误 1 次\n\n## 错误模式分析\n集中在计算题\n\n## 个性化复习建议\n1. 重点复习加法";
        when(agentStreamer.stream(eq("wrong-answer-analyzer"), any(), any(), eq(callback)))
                .thenReturn(fakeAnalysis);

        agent.execute(blackboard, callback);

        assertThat(blackboard.getWrongAnswerAnalysis()).isEqualTo(fakeAnalysis);
        assertThat(blackboard.getPhase()).isEqualTo(BlackboardPhase.WRONG_ANSWER_ANALYZING);

        ArgumentCaptor<BlackboardProgressEvent> captor =
                ArgumentCaptor.forClass(BlackboardProgressEvent.class);
        verify(callback, org.mockito.Mockito.atLeast(2)).onProgress(captor.capture());
        List<BlackboardProgressEvent> events = captor.getAllValues();
        assertThat(events.get(0).getAgentName()).isEqualTo("wrong-answer-analyzer");
    }

    @Test
    @DisplayName("空试卷 → 短路返回")
    void execute_shouldShortCircuit_whenExamPaperIsBlank() {
        blackboard.setExamPaper("");

        agent.execute(blackboard, callback);

        assertThat(blackboard.getWrongAnswerAnalysis()).isEqualTo("试卷为空，无法进行错题分析。");
        verify(agentStreamer, never()).stream(any(), any(), any(), any());
    }

    @Test
    @DisplayName("null 试卷 → 短路返回")
    void execute_shouldShortCircuit_whenExamPaperIsNull() {
        blackboard.setExamPaper(null);

        agent.execute(blackboard, callback);

        assertThat(blackboard.getWrongAnswerAnalysis()).isEqualTo("试卷为空，无法进行错题分析。");
    }

    @Test
    @DisplayName("LLM 返回 null → 使用兜底报告")
    void execute_shouldUseFallback_whenLlmReturnsNull() {
        when(agentStreamer.stream(eq("wrong-answer-analyzer"), any(), any(), eq(callback)))
                .thenReturn(null);

        agent.execute(blackboard, callback);

        assertThat(blackboard.getWrongAnswerAnalysis()).contains("分析异常");
    }

    @Test
    @DisplayName("LLM 返回空白 → 使用兜底报告")
    void execute_shouldUseFallback_whenLlmReturnsBlank() {
        when(agentStreamer.stream(eq("wrong-answer-analyzer"), any(), any(), eq(callback)))
                .thenReturn("  ");

        agent.execute(blackboard, callback);

        assertThat(blackboard.getWrongAnswerAnalysis()).contains("分析失败");
    }

    @Test
    @DisplayName("answerKey 为 null → 用户提示包含'标准答案未提供'")
    void execute_shouldIndicateMissingAnswerKey_whenAnswerKeyIsNull() {
        blackboard.setAnswerKey(null);
        when(agentStreamer.stream(eq("wrong-answer-analyzer"), any(), any(), eq(callback)))
                .thenReturn("## 错题清单\nOK\n\n## 薄弱知识点\nOK\n\n## 错误模式分析\nOK\n\n## 个性化复习建议\nOK");

        agent.execute(blackboard, callback);

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentStreamer).stream(
                eq("wrong-answer-analyzer"), any(), userPromptCaptor.capture(), eq(callback));
        assertThat(userPromptCaptor.getValue()).contains("标准答案未提供");
    }

    @Test
    @DisplayName("answerKey 存在 → 用户提示包含标准答案内容")
    void execute_shouldIncludeAnswerKey_whenProvided() {
        when(agentStreamer.stream(eq("wrong-answer-analyzer"), any(), any(), eq(callback)))
                .thenReturn("## 错题清单\nOK\n\n## 薄弱知识点\nOK\n\n## 错误模式分析\nOK\n\n## 个性化复习建议\nOK");

        agent.execute(blackboard, callback);

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentStreamer).stream(
                eq("wrong-answer-analyzer"), any(), userPromptCaptor.capture(), eq(callback));
        assertThat(userPromptCaptor.getValue()).contains("1. B");
    }

    @Test
    @DisplayName("callback 为 null → 不抛异常")
    void execute_shouldNotThrow_whenCallbackIsNull() {
        when(agentStreamer.stream(eq("wrong-answer-analyzer"), any(), any(), eq(null)))
                .thenReturn("## 错题清单\nOK\n\n## 薄弱知识点\nOK\n\n## 错误模式分析\nOK\n\n## 个性化复习建议\nOK");

        agent.execute(blackboard, null);

        assertThat(blackboard.getWrongAnswerAnalysis()).isNotBlank();
    }

    @Test
    @DisplayName("getName 返回 WrongAnswerAnalyzer")
    void getName_shouldReturnExpectedName() {
        assertThat(agent.getName()).isEqualTo("WrongAnswerAnalyzer");
    }

    @Test
    @DisplayName("评分：四段结构完整 → 高分")
    void execute_shouldGiveHighScore_whenAnalysisHasAllSections() {
        String fullAnalysis =
                "## 错题清单\n"
                        + "| 1 | 知识点 | 粗心 | 选A | B |\n".repeat(20)
                        + "\n## 薄弱知识点\n知识点OK\n\n## 错误模式分析\n模式OK\n\n## 个性化复习建议\n建议OK";
        when(agentStreamer.stream(eq("wrong-answer-analyzer"), any(), any(), eq(callback)))
                .thenReturn(fullAnalysis);

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
