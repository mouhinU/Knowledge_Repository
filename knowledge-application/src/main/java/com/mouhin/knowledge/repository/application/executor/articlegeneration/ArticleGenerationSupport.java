package com.mouhin.knowledge.repository.application.executor.articlegeneration;

import com.mouhin.knowledge.repository.application.util.AgentExecutorFactory;
import com.mouhin.knowledge.repository.domain.model.entity.WritingHistory;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import com.mouhin.knowledge.repository.domain.model.valueobject.SearchResult;
import com.mouhin.knowledge.repository.domain.gateway.VectorStoreGateway;
import com.mouhin.knowledge.repository.domain.gateway.WritingHistoryGateway;
import com.mouhin.knowledge.repository.domain.service.BlackboardAgent;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import com.mouhin.knowledge.repository.domain.service.PermissionDomainService;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 文章生成支撑组件（app 层，黑板模式编排）
 * <p>
 * 编排黑板模式完整流程：初始化黑板 → 知识库检索（无召回回退大模型补充）→
 * 研究员 / 写手 / 审核员三 Agent 协作 → 落库历史 → 通过回调推送进度事件。
 * 异步执行经虚拟线程调度，回调由适配层提供以桥接 SSE。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class ArticleGenerationSupport {

    private static final Logger logger = LoggerFactory.getLogger(ArticleGenerationSupport.class);

    private static final int DEFAULT_MAX_RESULTS = 10;
    private static final double DEFAULT_MIN_SCORE = 0.5;

    private final BlackboardAgent researcherAgent;
    private final BlackboardAgent writerAgent;
    private final BlackboardAgent reviewerAgent;
    private final VectorStoreGateway vectorStoreService;
    private final PermissionDomainService permissionDomainService;
    private final WritingHistoryGateway writingHistoryGateway;
    private final ChatModel chatModel;

    private final ExecutorService agentExecutor = AgentExecutorFactory.newBoundedAgentPool("article-gen");

    @Value("${knowledge.blackboard.search.max-results:10}")
    private int searchMaxResults;

    @Value("${knowledge.blackboard.search.min-score:0.5}")
    private double searchMinScore;

    public ArticleGenerationSupport(
            @Qualifier("researcherAgent") BlackboardAgent researcherAgent,
            @Qualifier("writerAgent") BlackboardAgent writerAgent,
            @Qualifier("reviewerAgent") BlackboardAgent reviewerAgent,
            VectorStoreGateway vectorStoreService,
            PermissionDomainService permissionDomainService,
            WritingHistoryGateway writingHistoryGateway,
            ChatModel chatModel) {
        this.researcherAgent = researcherAgent;
        this.writerAgent = writerAgent;
        this.reviewerAgent = reviewerAgent;
        this.vectorStoreService = vectorStoreService;
        this.permissionDomainService = permissionDomainService;
        this.writingHistoryGateway = writingHistoryGateway;
        this.chatModel = chatModel;
    }

    /**
     * 容器优雅停机时关闭文章生成异步线程池，拒绝新任务并给在途流水线短暂收尾窗口。
     */
    @PreDestroy
    public void shutdown() {
        agentExecutor.shutdown();
        try {
            if (!agentExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                agentExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            agentExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 异步启动文章生成，立即返回。
     *
     * @param question         用户问题
     * @param permission       用户权限上下文
     * @param progressCallback 进度回调（由控制器提供，将事件推送到 SSE）
     * @param sessionId        预分配的会话 ID
     * @param category         知识分类过滤
     */
    public void generateArticleAsync(String question, Permission permission,
                                     BlackboardProgressCallback progressCallback, String sessionId,
                                     String category) {
        logger.info("启动异步文章生成 [session={}, question='{}', category='{}']",
                sessionId, truncate(question, 50), category);

        // 推送初始事件
        if (progressCallback != null) {
            progressCallback.onProgress(
                    BlackboardProgressEvent.phaseChanged(BlackboardPhase.INIT, "正在初始化..."));
        }

        CompletableFuture.runAsync(
                () -> executeGeneration(sessionId, question, permission, progressCallback, category),
                agentExecutor);
    }

    /**
     * 后台执行完整的文章生成流程
     */
    private void executeGeneration(String sessionId, String question,
                                   Permission permission, BlackboardProgressCallback callback,
                                   String category) {
        BlackboardState blackboard = new BlackboardState(sessionId, question);
        blackboard.setUserId(permission.getUserId());
        blackboard.setDepartmentId(permission.getDepartmentId());
        blackboard.setRoles(permission.getRoles());
        blackboard.setAdmin(permission.isAdmin());

        try {
            // 1. 知识库检索
            if (callback != null) {
                callback.onProgress(BlackboardProgressEvent.phaseChanged(
                        BlackboardPhase.RESEARCH, "正在检索知识库..."));
            }

            String filterExpr = permissionDomainService.buildFilterExpression(permission);
            int maxResults = searchMaxResults > 0 ? searchMaxResults : DEFAULT_MAX_RESULTS;
            double minScore = searchMinScore > 0 ? searchMinScore : DEFAULT_MIN_SCORE;

            List<SearchResult> results = vectorStoreService.search(question, maxResults, minScore, filterExpr, category);
            blackboard.setKnowledgeChunks(results);
            logger.info("[Blackboard] 检索到 {} 个知识片段 [session={}]", results.size(), sessionId);

            // 知识库无召回结果时，回退到大模型补充资料
            if (results.isEmpty()) {
                if (callback != null) {
                    callback.onProgress(BlackboardProgressEvent.phaseChanged(
                            BlackboardPhase.RESEARCH, "知识库无相关结果，正在使用大模型补充..."));
                }
                logger.info("[Blackboard] 知识库无召回结果，启用大模型补充 [session={}]", sessionId);
                String llmSupplement = callLlmForSupplement(question);
                if (llmSupplement != null && !llmSupplement.isBlank()) {
                    SearchResult supplementResult = new SearchResult(
                            llmSupplement, null, "大模型补充", null, null, 0.5);
                    blackboard.setKnowledgeChunks(List.of(supplementResult));
                    results = blackboard.getKnowledgeChunks();
                    logger.info("[Blackboard] 大模型补充资料已生成，长度: {} [session={}]",
                            llmSupplement.length(), sessionId);
                }
            }

            // 2. 研究员 Agent
            researcherAgent.execute(blackboard, callback);

            // 3. 写手 Agent
            writerAgent.execute(blackboard, callback);

            // 4. 审核员 Agent
            reviewerAgent.execute(blackboard, callback);

            logger.info("文章生成完成 [session={}, phase={}, score={}]",
                    sessionId, blackboard.getPhase(), blackboard.getQualityScore());

            // 保存历史记录
            saveHistory(sessionId, question, permission, blackboard, results.size(), null);

            // 推送完成事件（包含最终结果）
            if (callback != null) {
                callback.onProgress(BlackboardProgressEvent.completed(blackboard, results.size()));
            }

        } catch (Exception e) {
            logger.error("文章生成失败 [session={}]", sessionId, e);
            blackboard.markFailed(e.getMessage());

            // 保存失败记录
            saveHistory(sessionId, question, permission, blackboard, 0, e.getMessage());

            if (callback != null) {
                callback.onProgress(BlackboardProgressEvent.error(e.getMessage()));
            }
        }
    }

    /**
     * 保存写作历史记录
     */
    private void saveHistory(String sessionId, String question, Permission permission,
                             BlackboardState blackboard, int retrievedChunks, String errorMessage) {
        try {
            WritingHistory history = new WritingHistory();
            history.setSessionId(sessionId);
            history.setQuestion(question);
            history.setFinalArticle(blackboard.getFinalArticle());
            history.setDraftArticle(blackboard.getDraftArticle());
            history.setQualityScore(blackboard.getQualityScore());
            history.setRetrievedChunks(retrievedChunks);
            history.setKeyFindings(blackboard.getKeyFindings());
            history.setReviewFeedback(blackboard.getReviewFeedback());
            history.setUserId(permission.getUserId());
            history.setDepartmentId(permission.getDepartmentId());
            history.setStatus(errorMessage != null ? "FAILED" : "COMPLETED");
            history.setErrorMessage(errorMessage);
            history.setCreateTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
            history.setUpdateTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
            writingHistoryGateway.save(history);
        } catch (Exception ex) {
            logger.error("保存写作历史失败 [session={}]", sessionId, ex);
        }
    }

    /**
     * 知识库无召回结果时，调用大模型补充相关资料
     *
     * @param question 用户问题
     * @return 大模型生成的补充资料，失败时返回 null
     */
    private String callLlmForSupplement(String question) {
        try {
            String systemPrompt = """
                    你是一个知识助手。当知识库中没有找到与用户问题相关的内容时，
                    请根据你的通用知识提供准确、有用的信息来回答用户的问题。
                    输出要求：使用 Markdown 格式，结构清晰，内容准确。
                    如果确实无法回答，请明确说明。
                    """;
            String userPrompt = "用户问题：" + question + "\n\n请根据你的知识回答上述问题。";

            ChatRequest request = ChatRequest.builder()
                    .messages(
                            SystemMessage.from(systemPrompt),
                            UserMessage.from(userPrompt)
                    )
                    .build();

            ChatResponse response = chatModel.chat(request);
            String answer = response.aiMessage().text();
            logger.info("大模型补充资料生成完成，长度: {}", answer != null ? answer.length() : 0);
            return answer;
        } catch (Exception e) {
            logger.error("大模型补充资料调用失败", e);
            return null;
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    public WritingHistoryGateway writingHistoryGateway() {
        return writingHistoryGateway;
    }
}
