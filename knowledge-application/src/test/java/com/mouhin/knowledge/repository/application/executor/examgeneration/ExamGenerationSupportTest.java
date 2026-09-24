package com.mouhin.knowledge.repository.application.executor.examgeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.application.support.AuthorizedSearchSupport;
import com.mouhin.knowledge.repository.application.support.SystemConfigService;
import com.mouhin.knowledge.repository.domain.gateway.ExamAlertGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamDistributionGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import com.mouhin.knowledge.repository.domain.model.valueobject.SearchResult;
import com.mouhin.knowledge.repository.domain.service.BlackboardAgent;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import com.mouhin.knowledge.repository.domain.service.PermissionDomainService;
import com.mouhin.knowledge.repository.domain.service.ScoreRuleEngine;
import com.mouhin.knowledge.repository.domain.service.StreamingChatGateway;
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

/**
 * 出卷支撑组件单测（主干抽样，不穷举 7-Agent 流水线）：锁定同步出题 generateExam 的 LLM 直通与空结果兜底、 generateDistribution
 * 检索失败吞断（提示词降级 null）、balanceDistribution 的 null 方案防御、 validatePlanAsync 空方案「方案为空」短路，以及线程池拒绝（429
 * 前奏）推送并发上限错误事件。 收敛检测（extractFailingDimensions / hasEvaluationSuggestions）已由 {@code
 * ExamDistributionConvergenceTest} 覆盖。
 *
 * @author mouhinU
 * @date 2026-09-24 18:16:00
 */
@DisplayName("出卷支撑组件主干 (ExamGenerationSupport)")
class ExamGenerationSupportTest {

    private final BlackboardAgent researcher = mock(BlackboardAgent.class);
    private final BlackboardAgent scoring = mock(BlackboardAgent.class);
    private final BlackboardAgent writer = mock(BlackboardAgent.class);
    private final BlackboardAgent answerKey = mock(BlackboardAgent.class);
    private final BlackboardAgent reviewer = mock(BlackboardAgent.class);
    private final BlackboardAgent calibrator = mock(BlackboardAgent.class);
    private final BlackboardAgent deduplicator = mock(BlackboardAgent.class);
    private final BlackboardAgent knowledgeDedup = mock(BlackboardAgent.class);
    private final ExamDistributionGateway distributionAgent = mock(ExamDistributionGateway.class);
    private final AuthorizedSearchSupport authorizedSearch = mock(AuthorizedSearchSupport.class);
    private final PermissionDomainService permissionDomainService =
            mock(PermissionDomainService.class);
    private final ChatModel chatModel = mock(ChatModel.class);
    private final StreamingChatGateway streamingChatGateway = mock(StreamingChatGateway.class);
    private final ExamHistoryGateway examHistoryGateway = mock(ExamHistoryGateway.class);
    private final ExamQuestionSplitSupport splitSupport = mock(ExamQuestionSplitSupport.class);
    private final ExamAlertGateway alertGateway = mock(ExamAlertGateway.class);
    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);

    private ExamGenerationSupport support;
    private final Permission permission = new Permission("u-1", "d-1", null, false);

    /** 记录进度事件并在终态（COMPLETED / ERROR）放行的测试回调。 */
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

        List<BlackboardProgressEvent> ofType(String type) {
            synchronized (events) {
                return events.stream().filter(e -> type.equals(e.getType())).toList();
            }
        }
    }

    @BeforeEach
    void setUp() {
        support =
                new ExamGenerationSupport(
                        researcher,
                        scoring,
                        writer,
                        answerKey,
                        reviewer,
                        calibrator,
                        deduplicator,
                        knowledgeDedup,
                        distributionAgent,
                        authorizedSearch,
                        permissionDomainService,
                        chatModel,
                        streamingChatGateway,
                        examHistoryGateway,
                        splitSupport,
                        alertGateway,
                        systemConfigService);
        when(permissionDomainService.buildFilterExpression(any())).thenReturn("dept == 'd-1'");
    }

    @AfterEach
    void tearDown() {
        support.shutdown();
    }

    @Test
    @DisplayName("同步出题：检索 → 组装提示词 → 返回 LLM 卷面原文")
    void generateExamReturnsLlmOutput() {
        when(authorizedSearch.searchAuthorized(
                        anyString(), anyInt(), anyDouble(), any(), any(), any()))
                .thenReturn(List.of(new SearchResult("知识点：古诗", "d-1", "教材", 2, 0, 0.9)));
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(
                        ChatResponse.builder().aiMessage(AiMessage.from("# 模拟卷\n一、单选题")).build());

        String paper =
                support.generateExam("古诗", "MEDIUM", "PRIMARY", 2, 1, 1, 1, 1, 0, permission, "语文");

        assertTrue(paper.contains("模拟卷"));
        verify(chatModel).chat(any(ChatRequest.class));
    }

    @Test
    @DisplayName("同步出题：LLM 返回空白 → 兜底失败文案而非抛断（可重试语义）")
    void generateExamBlankLlmFallsBack() {
        when(authorizedSearch.searchAuthorized(
                        anyString(), anyInt(), anyDouble(), any(), any(), any()))
                .thenReturn(List.of());
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("   ")).build());

        String paper = support.generateExam("主题", "EASY", null, 1, 0, 0, 0, 0, 0, permission, null);

        assertTrue(paper.contains("试卷生成失败"), "实际返回：" + paper);
    }

    @Test
    @DisplayName("分布方案生成：检索异常吞断降级，knowledgeHint 以 null 继续委托分布 Agent")
    void generateDistributionSwallowsSearchFailure() {
        when(authorizedSearch.searchAuthorized(
                        anyString(), anyInt(), anyDouble(), any(), any(), any()))
                .thenThrow(new RuntimeException("milvus down"));
        ExamPlan plan = new ExamPlan();
        when(distributionAgent.generate(eq("数学"), eq("MEDIUM"), eq("PRIMARY"), isNull()))
                .thenReturn(plan);

        ExamPlan result = support.generateDistribution("数学", "MEDIUM", "PRIMARY", "数学", permission);

        assertEquals(plan, result);
        verify(distributionAgent).generate("数学", "MEDIUM", "PRIMARY", null);
    }

    @Test
    @DisplayName("balanceDistribution：null 方案 → 原样回传 +「无可平衡」说明，不抛 NPE")
    void balanceDistributionNullGuard() {
        ScoreRuleEngine.BalanceResult result = support.balanceDistribution(null);

        assertNull(result.plan());
        assertTrue(result.trace().contains("无可平衡"), "实际 trace：" + result.trace());
    }

    @Test
    @DisplayName("validatePlanAsync：方案为空 → agentFailed +「方案为空，请先生成方案」ERROR 事件短路")
    void validatePlanAsyncRejectsEmptyPlan() throws Exception {
        Recorder recorder = new Recorder();

        support.validatePlanAsync("s-1", null, recorder);

        assertTrue(recorder.terminal.await(10, TimeUnit.SECONDS), "应收到 ERROR 终态事件");
        List<BlackboardProgressEvent> errors = recorder.ofType("ERROR");
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getErrorMessage().contains("方案为空"));
        // 空方案不触模型解读
        verify(streamingChatGateway, org.mockito.Mockito.never())
                .streamCompletion(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("线程池已关闭（并发上限场景）→ 推 ERROR 事件并抛 RejectedExecutionException 供控制器转 429")
    void rejectedAfterShutdownPushesBusyError() {
        support.shutdown();
        Recorder recorder = new Recorder();

        assertThrows(
                RejectedExecutionException.class,
                () ->
                        support.generateExamAsync(
                                "主题",
                                "MEDIUM",
                                "2,1,1",
                                permission,
                                recorder,
                                "s-2",
                                null,
                                "PRIMARY",
                                null,
                                false));

        // 初始 INIT 事件先于入池推送，拒绝时补发并发上限 ERROR
        assertTrue(recorder.events.stream().anyMatch(e -> BlackboardPhase.INIT == e.getPhase()));
        List<BlackboardProgressEvent> errors = recorder.ofType("ERROR");
        assertEquals(1, errors.size());
        assertTrue(
                errors.get(0).getErrorMessage().contains("并发上限"),
                "实际：" + errors.get(0).getErrorMessage());
    }

    // 其他分支待补：executeExamPipeline 七步并行流水线的逐 Agent 编排（依赖大量打桩矩阵，主干已由上列用例与收敛测试锚定）。
}
