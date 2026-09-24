package com.mouhin.knowledge.repository.application.executor.articlegeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.application.support.AuthorizedSearchSupport;
import com.mouhin.knowledge.repository.domain.gateway.WritingHistoryGateway;
import com.mouhin.knowledge.repository.domain.model.entity.WritingHistory;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import com.mouhin.knowledge.repository.domain.model.valueobject.SearchResult;
import com.mouhin.knowledge.repository.domain.service.BlackboardAgent;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import com.mouhin.knowledge.repository.domain.service.PermissionDomainService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 文章生成支撑（黑板编排）单测：锁定「INIT 事件同步先行 → 检索 → 无召回回退大模型补充 → 研究 / 写手 / 审核三 Agent 顺序执行 → 历史落库 COMPLETED /
 * FAILED → 终态事件」的编排骨架与线程池拒绝（429 前奏）语义。
 *
 * @author mouhinU
 * @date 2026-09-24 18:16:00
 */
@DisplayName("文章生成黑板编排支撑 (ArticleGenerationSupport)")
class ArticleGenerationSupportTest {

    private final BlackboardAgent researcherAgent = mock(BlackboardAgent.class);
    private final BlackboardAgent writerAgent = mock(BlackboardAgent.class);
    private final BlackboardAgent reviewerAgent = mock(BlackboardAgent.class);
    private final AuthorizedSearchSupport authorizedSearch = mock(AuthorizedSearchSupport.class);
    private final PermissionDomainService permissionDomainService =
            mock(PermissionDomainService.class);
    private final WritingHistoryGateway writingHistoryGateway = mock(WritingHistoryGateway.class);
    private final ChatModel chatModel = mock(ChatModel.class);

    private ArticleGenerationSupport support;
    private final Permission permission = new Permission("u-1", "d-1", null, false);

    @BeforeEach
    void setUp() {
        support =
                new ArticleGenerationSupport(
                        researcherAgent,
                        writerAgent,
                        reviewerAgent,
                        authorizedSearch,
                        permissionDomainService,
                        writingHistoryGateway,
                        chatModel);
        when(permissionDomainService.buildFilterExpression(any())).thenReturn("owner_id == 'u-1'");
    }

    @AfterEach
    void tearDown() {
        support.shutdown();
    }

    /** 记录全部进度事件并在终态（COMPLETED / ERROR）放行 latch 的测试回调。 */
    private static final class Recorder implements BlackboardProgressCallback {
        final List<BlackboardProgressEvent> events =
                Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch terminal = new CountDownLatch(1);

        @Override
        public void onProgress(BlackboardProgressEvent event) {
            events.add(event);
            if ("COMPLETED".equals(event.getType()) || "ERROR".equals(event.getType())) {
                terminal.countDown();
            }
        }

        boolean awaitTerminal() throws InterruptedException {
            return terminal.await(10, TimeUnit.SECONDS);
        }

        List<BlackboardProgressEvent> ofType(String type) {
            synchronized (events) {
                return events.stream().filter(e -> type.equals(e.getType())).toList();
            }
        }
    }

    @Test
    @DisplayName("启动即刻同步推送 INIT 阶段事件（异步流水线尚未展开）")
    void initEventEmittedSynchronously() {
        when(authorizedSearch.searchAuthorized(
                        anyString(), anyInt(), anyDouble(), any(), any(), any()))
                .thenReturn(List.of());
        Recorder recorder = new Recorder();

        support.generateArticleAsync("主题", permission, recorder, "s-1", "学科");

        BlackboardProgressEvent first = recorder.events.get(0);
        assertEquals("PHASE", first.getType());
        assertEquals(BlackboardPhase.INIT, first.getPhase());
        assertTrue(first.getMessage().contains("初始化"));
    }

    @Test
    @DisplayName("主干：检索命中 → 三 Agent 顺序执行 → 历史落库 COMPLETED + completed 事件")
    void happyPathPersistsCompletedHistory() throws Exception {
        when(authorizedSearch.searchAuthorized(
                        anyString(), anyInt(), anyDouble(), any(), any(), any()))
                .thenReturn(List.of(new SearchResult("知识片段", "d-1", "教材", 1, 0, 0.9)));
        Recorder recorder = new Recorder();

        support.generateArticleAsync("春天", permission, recorder, "s-2", "语文");

        assertTrue(recorder.awaitTerminal(), "应在时限内收到终态事件");
        assertEquals(1, recorder.ofType("COMPLETED").size());
        verify(researcherAgent).execute(any(), any());
        verify(writerAgent).execute(any(), any());
        verify(reviewerAgent).execute(any(), any());
        // 有召回时不应触发大模型补充
        verify(chatModel, times(0)).chat(any(ChatRequest.class));

        ArgumentCaptor<WritingHistory> cap = ArgumentCaptor.forClass(WritingHistory.class);
        verify(writingHistoryGateway).save(cap.capture());
        WritingHistory saved = cap.getValue();
        assertEquals("s-2", saved.getSessionId());
        assertEquals("春天", saved.getQuestion());
        assertEquals("COMPLETED", saved.getStatus());
        assertEquals(1, saved.getRetrievedChunks());
        assertEquals("u-1", saved.getUserId());
        assertNotNull(saved.getCreateTime());
    }

    @Test
    @DisplayName("知识库零召回 → 大模型补充资料进入黑板（来源标注「大模型补充」）")
    void emptyRecallFallsBackToLlmSupplement() throws Exception {
        when(authorizedSearch.searchAuthorized(
                        anyString(), anyInt(), anyDouble(), any(), any(), any()))
                .thenReturn(List.of());
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("补充知识正文")).build());
        Recorder recorder = new Recorder();

        support.generateArticleAsync("主题", permission, recorder, "s-3", null);

        assertTrue(recorder.awaitTerminal(), "应在时限内收到终态事件");
        ArgumentCaptor<BlackboardState> cap = ArgumentCaptor.forClass(BlackboardState.class);
        verify(researcherAgent).execute(cap.capture(), any());
        List<SearchResult> chunks = cap.getValue().getKnowledgeChunks();
        assertEquals(1, chunks.size());
        assertEquals("大模型补充", chunks.get(0).getDocumentName());
        assertEquals("补充知识正文", chunks.get(0).getText());
    }

    @Test
    @DisplayName("Agent 中途失败 → 历史落库 FAILED（含错误消息）并推送 ERROR 事件")
    void agentFailurePersistsFailedHistory() throws Exception {
        when(authorizedSearch.searchAuthorized(
                        anyString(), anyInt(), anyDouble(), any(), any(), any()))
                .thenReturn(List.of(new SearchResult("片段", "d-1", "教材", 1, 0, 0.8)));
        doThrow(new RuntimeException("写手崩溃"))
                .when(writerAgent)
                .execute(any(BlackboardState.class), any());
        Recorder recorder = new Recorder();

        support.generateArticleAsync("主题", permission, recorder, "s-4", null);

        assertTrue(recorder.awaitTerminal());
        assertEquals(1, recorder.ofType("ERROR").size());
        assertTrue(recorder.ofType("ERROR").get(0).getErrorMessage().contains("写手崩溃"));

        ArgumentCaptor<WritingHistory> cap = ArgumentCaptor.forClass(WritingHistory.class);
        verify(writingHistoryGateway).save(cap.capture());
        assertEquals("FAILED", cap.getValue().getStatus());
        assertEquals("写手崩溃", cap.getValue().getErrorMessage());
        assertEquals(0, cap.getValue().getRetrievedChunks(), "失败记录不携带召回数");
    }

    @Test
    @DisplayName("线程池已关闭（并发上限场景）→ 推 ERROR 事件并抛 RejectedExecutionException 供上层转 429")
    void rejectedWhenPoolShutdown() {
        support.shutdown();
        Recorder recorder = new Recorder();

        assertThrows(
                RejectedExecutionException.class,
                () -> support.generateArticleAsync("主题", permission, recorder, "s-5", null));

        List<BlackboardProgressEvent> errors = recorder.ofType("ERROR");
        assertEquals(1, errors.size());
        assertTrue(
                errors.get(0).getErrorMessage().contains("并发上限"),
                "实际：" + errors.get(0).getErrorMessage());
    }

    @Test
    @DisplayName("回调为 null（前端未订阅 SSE）→ 全流程静默不抛 NPE")
    void nullCallbackIsTolerated() throws Exception {
        when(authorizedSearch.searchAuthorized(
                        anyString(), anyInt(), anyDouble(), any(), any(), any()))
                .thenReturn(List.of(new SearchResult("片段", "d-1", "教材", 1, 0, 0.8)));

        support.generateArticleAsync("主题", permission, null, "s-6", null);

        ArgumentCaptor<WritingHistory> cap = ArgumentCaptor.forClass(WritingHistory.class);
        verify(writingHistoryGateway, org.mockito.Mockito.after(3_000)).save(cap.capture());
        assertEquals("COMPLETED", cap.getValue().getStatus());
    }

    // 其他分支待补：@Value 注入 searchMaxResults / minScore 的自定义回退（单测恒为 0 → 走默认常量，已由主干用例锁定）。
}
