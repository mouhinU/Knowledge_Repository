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
import com.mouhin.knowledge.repository.domain.model.valueobject.SearchResult;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * KnowledgeGapAnalyzerAgent 单元测试
 *
 * <p>覆盖：正常执行、空试卷短路、LLM 空返回兜底、进度事件推送、知识片段上下文拼装。
 *
 * @author mouhinU
 * @date 2026-09-28
 */
@DisplayName("知识缺口分析 Agent (KnowledgeGapAnalyzerAgent)")
class KnowledgeGapAnalyzerAgentTest {

    private final BlackboardAgentStreamer agentStreamer = mock(BlackboardAgentStreamer.class);
    private final BlackboardProgressCallback callback = mock(BlackboardProgressCallback.class);

    private KnowledgeGapAnalyzerAgent agent;
    private BlackboardState blackboard;

    @BeforeEach
    void setUp() {
        agent = new KnowledgeGapAnalyzerAgent(agentStreamer);
        blackboard = new BlackboardState("test-session", "Java 基础");
        blackboard.setExamPaper(
                "## 一、单选题\n1. 以下哪个是 Java 的基本数据类型？\nA. String  B. int  C. Object  D. Array");
        blackboard.setKnowledgeChunks(sampleChunks());
    }

    @Test
    @DisplayName("正常执行：LLM 返回有效报告 → 写入 blackboard + 推送完成事件")
    void execute_shouldSetReportAndEmitCompleted_whenLlmReturnsValidReport() {
        String fakeReport =
                "## 逐题核查\n| 题号 | 考查知识点 | 依据状态 | 对应片段 |\n|------|-----------|---------|--------|\n| 1 | 基本数据类型 | ✅ | #1 |\n\n## 覆盖率统计\n有依据：1 / 1 题（100%）\n\n## 风险提示\n知识覆盖率达标\n\n## 补充建议\n无需补充";
        when(agentStreamer.stream(eq("knowledge-gap-analyzer"), any(), any(), eq(callback)))
                .thenReturn(fakeReport);

        agent.execute(blackboard, callback);

        assertThat(blackboard.getKnowledgeGapReport()).isEqualTo(fakeReport);
        assertThat(blackboard.getPhase()).isEqualTo(BlackboardPhase.KNOWLEDGE_GAP_ANALYZING);

        ArgumentCaptor<BlackboardProgressEvent> captor =
                ArgumentCaptor.forClass(BlackboardProgressEvent.class);
        verify(callback, org.mockito.Mockito.atLeast(2)).onProgress(captor.capture());
        List<BlackboardProgressEvent> events = captor.getAllValues();
        assertThat(events.get(0).getAgentName()).isEqualTo("knowledge-gap-analyzer");
        BlackboardProgressEvent last = events.get(events.size() - 1);
        assertThat(last.getAgentScore()).isNotNull();
        assertThat(last.getAgentScore()).isGreaterThan(0);
    }

    @Test
    @DisplayName("空试卷 → 短路返回 + 写入占位报告")
    void execute_shouldShortCircuit_whenExamPaperIsBlank() {
        blackboard.setExamPaper("");

        agent.execute(blackboard, callback);

        assertThat(blackboard.getKnowledgeGapReport()).isEqualTo("试卷为空，无法进行知识缺口分析。");
        verify(agentStreamer, never()).stream(any(), any(), any(), any());
    }

    @Test
    @DisplayName("null 试卷 → 短路返回")
    void execute_shouldShortCircuit_whenExamPaperIsNull() {
        blackboard.setExamPaper(null);

        agent.execute(blackboard, callback);

        assertThat(blackboard.getKnowledgeGapReport()).isEqualTo("试卷为空，无法进行知识缺口分析。");
        verify(agentStreamer, never()).stream(any(), any(), any(), any());
    }

    @Test
    @DisplayName("LLM 返回 null → 使用兜底报告")
    void execute_shouldUseFallbackReport_whenLlmReturnsNull() {
        when(agentStreamer.stream(eq("knowledge-gap-analyzer"), any(), any(), eq(callback)))
                .thenReturn(null);

        agent.execute(blackboard, callback);

        assertThat(blackboard.getKnowledgeGapReport()).contains("逐题核查");
        assertThat(blackboard.getKnowledgeGapReport()).contains("分析过程异常");
    }

    @Test
    @DisplayName("LLM 返回空白 → 使用兜底报告")
    void execute_shouldUseFallbackReport_whenLlmReturnsBlank() {
        when(agentStreamer.stream(eq("knowledge-gap-analyzer"), any(), any(), eq(callback)))
                .thenReturn("   ");

        agent.execute(blackboard, callback);

        assertThat(blackboard.getKnowledgeGapReport()).contains("分析失败");
    }

    @Test
    @DisplayName("无知识片段 → 上下文显示'未检索到'")
    void execute_shouldHandleEmptyKnowledgeChunks() {
        blackboard.setKnowledgeChunks(Collections.emptyList());
        when(agentStreamer.stream(eq("knowledge-gap-analyzer"), any(), any(), eq(callback)))
                .thenReturn("## 逐题核查\n无依据\n\n## 覆盖率统计\n0/1\n\n## 风险提示\n覆盖率不足\n\n## 补充建议\n需补充");

        agent.execute(blackboard, callback);

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentStreamer).stream(
                eq("knowledge-gap-analyzer"), any(), userPromptCaptor.capture(), eq(callback));
        assertThat(userPromptCaptor.getValue()).contains("未检索到");
    }

    @Test
    @DisplayName("callback 为 null → 不抛异常")
    void execute_shouldNotThrow_whenCallbackIsNull() {
        when(agentStreamer.stream(eq("knowledge-gap-analyzer"), any(), any(), eq(null)))
                .thenReturn("## 逐题核查\nOK\n\n## 覆盖率统计\n1/1\n\n## 风险提示\n无\n\n## 补充建议\n无");

        agent.execute(blackboard, null);

        assertThat(blackboard.getKnowledgeGapReport()).isNotBlank();
    }

    @Test
    @DisplayName("getName 返回 KnowledgeGapAnalyzer")
    void getName_shouldReturnExpectedName() {
        assertThat(agent.getName()).isEqualTo("KnowledgeGapAnalyzer");
    }

    @Test
    @DisplayName("评分：结构完整四段 → 高分")
    void execute_shouldGiveHighScore_whenReportHasAllSections() {
        String fullReport =
                "## 逐题核查\n"
                        + "| 1 | 类型 | ✅ | #1 |\n".repeat(20)
                        + "\n## 覆盖率统计\n有依据：20/20\n\n## 风险提示\n无\n\n## 补充建议\n无";
        when(agentStreamer.stream(eq("knowledge-gap-analyzer"), any(), any(), eq(callback)))
                .thenReturn(fullReport);

        agent.execute(blackboard, callback);

        ArgumentCaptor<BlackboardProgressEvent> captor =
                ArgumentCaptor.forClass(BlackboardProgressEvent.class);
        verify(callback, org.mockito.Mockito.atLeast(1)).onProgress(captor.capture());
        BlackboardProgressEvent completedEvent =
                captor.getAllValues().stream()
                        .filter(
                                e ->
                                        "knowledge-gap-analyzer".equals(e.getAgentName())
                                                && e.getAgentScore() != null)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        assertThat(completedEvent.getAgentScore()).isGreaterThanOrEqualTo(80.0);
    }

    private List<SearchResult> sampleChunks() {
        List<SearchResult> chunks = new ArrayList<>();
        chunks.add(new SearchResult("int 是 Java 的基本数据类型", "Java基础.doc", null, null, null, 0.9));
        chunks.add(new SearchResult("String 是引用类型", "Java基础.doc", null, null, null, 0.8));
        return chunks;
    }
}
