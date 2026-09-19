package com.mouhin.knowledge.repository.application.executor.examgeneration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mouhin.knowledge.repository.application.util.ExamPaperParser;
import com.mouhin.knowledge.repository.application.util.AgentExecutorFactory;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import com.mouhin.knowledge.repository.domain.model.valueobject.SearchResult;
import com.mouhin.knowledge.repository.domain.model.valueobject.TypePlan;
import com.mouhin.knowledge.repository.domain.gateway.ExamAlertGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamDistributionGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import com.mouhin.knowledge.repository.application.support.AuthorizedSearchSupport;
import com.mouhin.knowledge.repository.domain.service.BlackboardAgent;
import com.mouhin.knowledge.repository.domain.service.ExamContractValidator;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import com.mouhin.knowledge.repository.domain.service.PermissionDomainService;
import com.mouhin.knowledge.repository.domain.service.ScoreRuleEngine;
import com.mouhin.knowledge.repository.domain.service.StreamingChatGateway;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 试卷生成支撑组件（app 层，黑板模式 7 步 Agent 流水线编排）
 * <p>
 * 承载出卷核心编排：同步出卷（单次 LLM）、异步 7 步流水线（含并行 + 低分自动重试）、
 * 题型分布方案生成（同步 / 流式）、分值自动平衡、方案异步校验，以及出卷历史落库。
 * 这些用例出入参含领域类型（Permission / ExamPlan / 回调 / BalanceResult），
 * 故由 app 执行器承载并供适配层直接调用，不纳入 client 契约。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class ExamGenerationSupport {

    private static final Logger logger = LoggerFactory.getLogger(ExamGenerationSupport.class);

    private static final int DEFAULT_MAX_RESULTS = 20;
    private static final double DEFAULT_MIN_SCORE = 0.3;
    private static final int QUALITY_SCORE_THRESHOLD = 80;
    private static final int MAX_REVIEW_RETRIES = 2;
    /** 手动调整方案下，连续两轮评分差 ≤ 该值即视为已收敛，提前结束改进循环 */
    private static final int SCORE_CONVERGENCE_DELTA = 3;
    /** 自动发布（免人工校对）时写入的审核人标识 */
    private static final String AUTO_PUBLISH_REVIEWER = "system:auto-publish";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final BlackboardAgent examResearcherAgent;
    private final BlackboardAgent examScoringAgent;
    private final BlackboardAgent examWriterAgent;
    private final BlackboardAgent answerKeyGeneratorAgent;
    private final BlackboardAgent examReviewerAgent;
    private final BlackboardAgent examCalibratorAgent;
    private final BlackboardAgent examDeduplicatorAgent;
    private final ExamDistributionGateway examDistributionAgent;
    private final AuthorizedSearchSupport authorizedSearch;
    private final PermissionDomainService permissionDomainService;
    private final ChatModel chatModel;
    private final StreamingChatGateway streamingChatGateway;
    private final ExamHistoryGateway examHistoryGateway;
    private final ExamQuestionSplitSupport examQuestionSplitSupport;
    private final ExamAlertGateway examAlertGateway;

    private final ExecutorService agentExecutor = AgentExecutorFactory.newBoundedAgentPool("exam-gen");

    @Value("${knowledge.blackboard.search.max-results:20}")
    private int searchMaxResults;

    @Value("${knowledge.blackboard.search.min-score:0.3}")
    private double searchMinScore;

    /** 是否强制人工校对后方可发布（默认 true：所有卷须校对通过才发布） */
    @Value("${knowledge.exam.review-required:true}")
    private boolean examReviewRequired;

    public ExamGenerationSupport(
            @Qualifier("examResearcherAgent") BlackboardAgent examResearcherAgent,
            @Qualifier("examScoringAgent") BlackboardAgent examScoringAgent,
            @Qualifier("examWriterAgent") BlackboardAgent examWriterAgent,
            @Qualifier("answerKeyGeneratorAgent") BlackboardAgent answerKeyGeneratorAgent,
            @Qualifier("examReviewerAgent") BlackboardAgent examReviewerAgent,
            @Qualifier("examCalibratorAgent") BlackboardAgent examCalibratorAgent,
            @Qualifier("examDeduplicatorAgent") BlackboardAgent examDeduplicatorAgent,
            ExamDistributionGateway examDistributionAgent,
            AuthorizedSearchSupport authorizedSearch,
            PermissionDomainService permissionDomainService,
            ChatModel chatModel,
            StreamingChatGateway streamingChatGateway,
            ExamHistoryGateway examHistoryGateway,
            ExamQuestionSplitSupport examQuestionSplitSupport,
            ExamAlertGateway examAlertGateway) {
        this.examResearcherAgent = examResearcherAgent;
        this.examScoringAgent = examScoringAgent;
        this.examWriterAgent = examWriterAgent;
        this.answerKeyGeneratorAgent = answerKeyGeneratorAgent;
        this.examReviewerAgent = examReviewerAgent;
        this.examCalibratorAgent = examCalibratorAgent;
        this.examDeduplicatorAgent = examDeduplicatorAgent;
        this.examDistributionAgent = examDistributionAgent;
        this.authorizedSearch = authorizedSearch;
        this.permissionDomainService = permissionDomainService;
        this.chatModel = chatModel;
        this.streamingChatGateway = streamingChatGateway;
        this.examHistoryGateway = examHistoryGateway;
        this.examQuestionSplitSupport = examQuestionSplitSupport;
        this.examAlertGateway = examAlertGateway;
    }

    /**
     * 容器优雅停机时关闭出卷异步线程池，拒绝新任务并给在途流水线短暂收尾窗口。
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
     * 异步启动试卷生成（7 步 Agent 流水线，含并行），立即返回。
     */
    public void generateExamAsync(String topic, String difficulty, String questionConfig,
                                  Permission permission, BlackboardProgressCallback progressCallback,
                                  String sessionId, String category, String schoolLevel, ExamPlan plan,
                                  boolean skipScoringValidation) {
        logger.info("启动异步试卷生成 [session={}, topic='{}', difficulty='{}', category='{}', level='{}', hasPlan={}, skipValidation={}]",
                sessionId, topic, difficulty, category, schoolLevel, plan != null, skipScoringValidation);

        if (progressCallback != null) {
            progressCallback.onProgress(
                    BlackboardProgressEvent.phaseChanged(BlackboardPhase.INIT, "正在初始化..."));
        }

        try {
            CompletableFuture.runAsync(
                    () -> executeExamPipeline(sessionId, topic, difficulty, questionConfig, permission,
                            progressCallback, category, schoolLevel, plan, skipScoringValidation),
                    agentExecutor);
        } catch (RejectedExecutionException rex) {
            // 线程池已达并发上限：不在请求线程上同步跑流水线（AbortPolicy），
            // 先经 SSE 推送友好错误让前端优雅收尾，再向上冒泡由控制器转 429。
            logger.warn("出卷任务被拒绝（并发已达上限）[session={}]", sessionId);
            if (progressCallback != null) {
                progressCallback.onProgress(BlackboardProgressEvent.error(
                        "系统繁忙，出卷任务已达并发上限，请稍后重试。"));
            }
            throw rex;
        }
    }

    /**
     * 后台执行完整的试卷生成流水线
     */
    private void executeExamPipeline(String sessionId, String topic, String difficulty,
                                     String questionConfig, Permission permission,
                                     BlackboardProgressCallback callback, String category,
                                     String schoolLevel, ExamPlan plan, boolean skipScoringValidation) {
        BlackboardState blackboard = new BlackboardState(sessionId, topic);
        blackboard.setUserId(permission.getUserId());
        blackboard.setDepartmentId(permission.getDepartmentId());
        blackboard.setRoles(permission.getRoles());
        blackboard.setAdmin(permission.isAdmin());
        blackboard.setExamDifficulty(difficulty);
        blackboard.setExamQuestionConfig(questionConfig);
        blackboard.setExamSchoolLevel(schoolLevel);
        blackboard.setSkipScoringValidation(skipScoringValidation);
        if (plan != null) {
            blackboard.setExamPlan(plan);
        }

        try {
            // 1. 知识库检索
            if (callback != null) {
                callback.onProgress(BlackboardProgressEvent.phaseChanged(
                        BlackboardPhase.RESEARCH, "正在检索知识库..."));
            }

            String filterExpr = permissionDomainService.buildFilterExpression(permission);
            int maxResults = searchMaxResults > 0 ? searchMaxResults : DEFAULT_MAX_RESULTS;
            double minScore = searchMinScore > 0 ? searchMinScore : DEFAULT_MIN_SCORE;

            List<SearchResult> results = authorizedSearch.searchAuthorized(topic, maxResults, minScore, filterExpr, category, permission);
            blackboard.setKnowledgeChunks(results);
            logger.info("[ExamPipeline] 检索到 {} 个知识片段 [session={}]", results.size(), sessionId);

            // 知识库无召回结果时，回退到大模型补充
            if (results.isEmpty()) {
                if (callback != null) {
                    callback.onProgress(BlackboardProgressEvent.phaseChanged(
                            BlackboardPhase.RESEARCH, "知识库无相关结果，正在使用大模型补充..."));
                }
                String llmSupplement = callLlmForSupplement(topic);
                if (llmSupplement != null && !llmSupplement.isBlank()) {
                    SearchResult supplementResult = new SearchResult(
                            llmSupplement, null, "大模型补充", null, null, 0.5);
                    blackboard.setKnowledgeChunks(List.of(supplementResult));
                    results = blackboard.getKnowledgeChunks();
                }
            }

            // 2. 出卷研究员 Agent ∥ 分值校验与评估 Agent（无数据依赖，并行执行）
            if (callback != null) {
                callback.onProgress(BlackboardProgressEvent.phaseChanged(
                        BlackboardPhase.RESEARCH, "正在检索知识库并校验分值方案..."));
            }
            CompletableFuture<Void> researchFuture = CompletableFuture.runAsync(
                    () -> examResearcherAgent.execute(blackboard, callback), agentExecutor);
            CompletableFuture<Void> scoringFuture = CompletableFuture.runAsync(
                    () -> examScoringAgent.execute(blackboard, callback), agentExecutor);
            CompletableFuture.allOf(researchFuture, scoringFuture).join();

            // 3-5. 试卷编写 → (答案生成 ∥ 难度校准) → (内容审核 ∥ 查重去重)
            // 低分自动重试，最多 MAX_REVIEW_RETRIES 次；
            // 手动调整方案时若第 2 次评分与首轮相差 ≤ SCORE_CONVERGENCE_DELTA 视为收敛，提前通过。
            boolean manualPlan = plan != null && plan.isManualAdjusted();
            int firstAttemptScore = -1;
            for (int attempt = 0; attempt <= MAX_REVIEW_RETRIES; attempt++) {
                // 仅在重试轮（attempt>=1，即上一轮质量分未达阈触发重新生成）显示"第 N 轮"，首轮静默
                if (attempt >= 1 && callback != null) {
                    int currentRound = attempt + 1;
                    int totalRounds = MAX_REVIEW_RETRIES + 1;
                    callback.onProgress(BlackboardProgressEvent.retryRoundChanged(
                            BlackboardPhase.WRITING,
                            String.format("上一轮未达标，正在重新生成试卷（第 %d / 共 %d 轮）...", currentRound, totalRounds),
                            currentRound, totalRounds));
                }
                // 3. 试卷编写 Agent（重试时会读取审核反馈进行改进）
                examWriterAgent.execute(blackboard, callback);

                // 4. 答案生成 Agent ∥ 难度校准 Agent（均只依赖 examPaper，并行执行）
                CompletableFuture<Void> answerFuture = CompletableFuture.runAsync(
                        () -> answerKeyGeneratorAgent.execute(blackboard, callback), agentExecutor);
                CompletableFuture<Void> calibratorFuture = CompletableFuture.runAsync(
                        () -> examCalibratorAgent.execute(blackboard, callback), agentExecutor);
                answerFuture.join();  // 等待答案完成，审核和查重依赖 answerKey

                // 5. 内容审核 Agent ∥ 查重去重 Agent（均依赖 examPaper + answerKey，并行执行）
                CompletableFuture<Void> reviewFuture = CompletableFuture.runAsync(
                        () -> examReviewerAgent.execute(blackboard, callback), agentExecutor);
                CompletableFuture<Void> dedupFuture = CompletableFuture.runAsync(
                        () -> examDeduplicatorAgent.execute(blackboard, callback), agentExecutor);
                CompletableFuture.allOf(reviewFuture, dedupFuture).join();

                int score = blackboard.getQualityScore();
                if (firstAttemptScore < 0) {
                    firstAttemptScore = score;
                }

                // 手动方案下的收敛早停：第 2 轮起，若与首轮分差 ≤ 阈值则视为已收敛
                boolean converged = manualPlan && attempt >= 1
                        && Math.abs(score - firstAttemptScore) <= SCORE_CONVERGENCE_DELTA;
                if (converged) {
                    if (score < QUALITY_SCORE_THRESHOLD) {
                        blackboard.setQualityScore(QUALITY_SCORE_THRESHOLD);
                        score = QUALITY_SCORE_THRESHOLD;
                    }
                    logger.info("[ExamPipeline] 手动方案评分收敛（首轮 {}，本轮 {}，差值 ≤ {}），提前结束改进 [session={}, attempt={}]",
                            firstAttemptScore, score, SCORE_CONVERGENCE_DELTA, sessionId, attempt + 1);
                    if (callback != null) {
                        callback.onProgress(BlackboardProgressEvent.phaseChanged(
                                BlackboardPhase.WRITING,
                                String.format("手动方案：评分已收敛（%d → %d，差值 ≤ %d），停止改进。",
                                        firstAttemptScore, score, SCORE_CONVERGENCE_DELTA)));
                    }
                }

                if (score >= QUALITY_SCORE_THRESHOLD || attempt == MAX_REVIEW_RETRIES || converged) {
                    logger.info("[ExamPipeline] 审核完成 [session={}, attempt={}, score={}, threshold={}, manual={}, converged={}]",
                            sessionId, attempt + 1, score, QUALITY_SCORE_THRESHOLD, manualPlan, converged);
                    break;
                }

                logger.info("[ExamPipeline] 评分 {} 低于阈值 {}，启动第 {} 次改进 [session={}]",
                        score, QUALITY_SCORE_THRESHOLD, attempt + 1, sessionId);
                if (callback != null) {
                    callback.onProgress(BlackboardProgressEvent.phaseChanged(
                            BlackboardPhase.WRITING,
                            String.format("质量评分 %d 分，低于 %d 分，正在根据审核意见改进（第 %d/%d 轮）...",
                                    score, QUALITY_SCORE_THRESHOLD, attempt + 1, MAX_REVIEW_RETRIES)));
                }
            }

            logger.info("试卷生成完成 [session={}, phase={}, score={}]",
                    sessionId, blackboard.getPhase(), blackboard.getQualityScore());

            // 推送完成事件
            if (callback != null) {
                callback.onProgress(BlackboardProgressEvent.examCompleted(blackboard, results.size()));
            }

            // 出卷即切分（V2）+ 发布门禁：先一次性切分并做确定性契约校验，据此决定试卷状态，
            // 再把状态写入出卷历史。契约不通过 → VALIDATION_FAILED（强制人工校对，绝不自动发布）；
            // 契约通过且关闭校对要求（review-required=false）且质量分达阈 → 自动 PUBLISHED；
            // 其余通过情形 → REVIEWABLE（等待管理员 / 出题人在校对关口批准发布）。
            String paperStatus = resolvePaperStatus(sessionId, blackboard);
            if (callback != null) {
                callback.onProgress(BlackboardProgressEvent.phaseChanged(
                        BlackboardPhase.WRITING, describePaperStatus(paperStatus)));
            }

            // 保存历史记录（携带试卷生命周期状态）
            saveHistory(sessionId, topic, difficulty, questionConfig, blackboard, permission,
                    category, results.size(), null, paperStatus);

        } catch (Exception e) {
            String friendly = unwrapErrorMessage(e);
            logger.error("试卷生成失败 [session={}, msg={}]", sessionId, friendly, e);
            blackboard.markFailed(friendly);

            // 保存失败记录
            saveHistory(sessionId, topic, difficulty, questionConfig, blackboard, permission,
                    category, 0, friendly, ExamHistory.STATUS_FAILED);

            if (callback != null) {
                callback.onProgress(BlackboardProgressEvent.error(friendly));
            }
        }
    }

    /**
     * 拆解并行 Agent 抛出的包装异常，返回面向用户的最内层原因消息。
     */
    private static String unwrapErrorMessage(Throwable e) {
        Throwable cur = e;
        String last = null;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null && !msg.isBlank()) {
                last = msg;
            }
            if (cur.getCause() == cur) {
                break;
            }
            cur = cur.getCause();
        }
        return last != null ? last : e.toString();
    }

    /**
     * 同步生成试卷（按题量兜底方案，单次 LLM 调用，用于 Word 导出）
     */
    public String generateExam(String topic, String difficulty, String schoolLevel,
                               int singleChoice, int multiChoice, int trueFalse,
                               int fillBlank, int shortAnswer, int essay,
                               Permission permission, String category) {

        logger.info("同步生成试卷 [topic='{}', difficulty='{}', level='{}', total={}]",
                topic, difficulty, schoolLevel,
                singleChoice + multiChoice + trueFalse + fillBlank + shortAnswer + essay);

        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        if (singleChoice > 0) {
            counts.put("单选题", singleChoice);
        }
        if (multiChoice > 0) {
            counts.put("多选题", multiChoice);
        }
        if (trueFalse > 0) {
            counts.put("判断题", trueFalse);
        }
        if (fillBlank > 0) {
            counts.put("填空题", fillBlank);
        }
        if (shortAnswer > 0) {
            counts.put("简答题", shortAnswer);
        }
        if (essay > 0) {
            counts.put("论述题", essay);
        }

        ScoreRuleEngine.SchoolLevel level = ScoreRuleEngine.resolveLevel(topic, schoolLevel);
        int total = ScoreRuleEngine.resolveTotalFullMark(topic, level, schoolLevel);
        String schemeText = ScoreRuleEngine.renderScheme(
                ScoreRuleEngine.allocate(total, counts), topic, level);

        String filterExpr = permissionDomainService.buildFilterExpression(permission);
        int maxResults = searchMaxResults > 0 ? searchMaxResults : DEFAULT_MAX_RESULTS;
        double minScore = searchMinScore > 0 ? searchMinScore : DEFAULT_MIN_SCORE;

        List<SearchResult> results = authorizedSearch.searchAuthorized(topic, maxResults, minScore, filterExpr, category, permission);
        logger.info("[Exam] 检索到 {} 个知识片段 [topic='{}']", results.size(), topic);

        String knowledgeContext = buildKnowledgeContext(results);
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(topic, difficulty, singleChoice, multiChoice,
                trueFalse, fillBlank, shortAnswer, essay, knowledgeContext, total, schemeText);

        ChatRequest request = ChatRequest.builder()
                .messages(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from(userPrompt)
                )
                .build();

        ChatResponse response = chatModel.chat(request);
        String examPaper = response.aiMessage().text();

        if (examPaper == null || examPaper.isBlank()) {
            logger.error("[Exam] LLM 返回空结果 [topic='{}']", topic);
            return "# 试卷生成失败\n\n大模型返回了空结果，请重试。";
        }

        logger.info("[Exam] 试卷生成完成，长度: {} 字符 [topic='{}']", examPaper.length(), topic);
        return examPaper;
    }

    /**
     * 生成题型分布方案（多阶段 Agent，同步）。
     */
    public ExamPlan generateDistribution(String topic, String difficulty, String schoolLevel,
                                         String category, Permission permission) {
        String knowledgeHint = null;
        try {
            String filterExpr = permissionDomainService.buildFilterExpression(permission);
            int maxResults = searchMaxResults > 0 ? searchMaxResults : DEFAULT_MAX_RESULTS;
            double minScore = searchMinScore > 0 ? searchMinScore : DEFAULT_MIN_SCORE;
            List<SearchResult> results = authorizedSearch.searchAuthorized(topic, maxResults, minScore, filterExpr, category, permission);
            if (!results.isEmpty()) {
                knowledgeHint = buildKnowledgeContext(results);
            }
        } catch (Exception e) {
            logger.warn("[Distribution] 生成前检索知识点失败（忽略，继续出题）: {}", e.getMessage());
        }
        return examDistributionAgent.generate(topic, difficulty, schoolLevel, knowledgeHint);
    }

    /**
     * 异步生成题型分布方案（流式）。
     */
    public void generateDistributionAsync(String sessionId, String topic, String difficulty,
                                          String schoolLevel, String category,
                                          Permission permission,
                                          BlackboardProgressCallback progressCallback) {
        logger.info("启动题型分布方案生成 [session={}, topic='{}', difficulty='{}', level='{}']",
                sessionId, topic, difficulty, schoolLevel);
        CompletableFuture.runAsync(() -> {
            try {
                if (progressCallback != null) {
                    progressCallback.onProgress(BlackboardProgressEvent.phaseChanged(
                            BlackboardPhase.INIT, "正在准备题型分布方案生成..."));
                }
                String knowledgeHint = null;
                try {
                    String filterExpr = permissionDomainService.buildFilterExpression(permission);
                    int maxResults = searchMaxResults > 0 ? searchMaxResults : DEFAULT_MAX_RESULTS;
                    double minScore = searchMinScore > 0 ? searchMinScore : DEFAULT_MIN_SCORE;
                    List<SearchResult> results = authorizedSearch.searchAuthorized(
                            topic, maxResults, minScore, filterExpr, category, permission);
                    if (!results.isEmpty()) {
                        knowledgeHint = buildKnowledgeContext(results);
                    }
                } catch (Exception e) {
                    logger.warn("[Distribution] 检索知识点失败（忽略）: {}", e.getMessage());
                }
                ExamPlan plan = examDistributionAgent.generate(
                        topic, difficulty, schoolLevel, knowledgeHint, progressCallback);
                String planJson = objectMapper.writeValueAsString(plan);
                if (progressCallback != null) {
                    progressCallback.onProgress(BlackboardProgressEvent.distributionCompleted(planJson));
                }
                logger.info("[Distribution] 方案生成完成 [session={}, types={}, questions={}, fullMark={}]",
                        sessionId, plan.getTypes().size(), plan.totalQuestions(), plan.getTotalFullMark());
            } catch (Exception e) {
                logger.error("[Distribution] 方案生成失败 [session={}]", sessionId, e);
                if (progressCallback != null) {
                    progressCallback.onProgress(BlackboardProgressEvent.error(
                            e.getMessage() != null ? e.getMessage() : "方案生成失败"));
                }
            }
        }, agentExecutor);
    }

    /**
     * 依据当前方案自动平衡分值，返回平衡后的方案 + 过程说明。
     */
    public ScoreRuleEngine.BalanceResult balanceDistribution(ExamPlan plan) {
        return ScoreRuleEngine.balancePlan(plan);
    }

    /**
     * 异步校验题型分布方案（Node 2：分值检验和平衡）。
     */
    public void validatePlanAsync(String sessionId, ExamPlan plan,
                                  BlackboardProgressCallback progressCallback) {
        logger.info("启动方案校验 [session={}, types={}, fullMark={}]",
                sessionId,
                plan != null && plan.getTypes() != null ? plan.getTypes().size() : 0,
                plan != null ? plan.getTotalFullMark() : 0);
        CompletableFuture.runAsync(() -> {
            try {
                if (progressCallback != null) {
                    progressCallback.onProgress(BlackboardProgressEvent.agentStartedWithMaterials(
                            "exam-plan-validator",
                            "正在校验题型分布方案的总分与分值分布...",
                            "方案含 " + (plan != null && plan.getTypes() != null ? plan.getTypes().size() : 0)
                                    + " 种题型，满分 " + (plan != null ? plan.getTotalFullMark() : 0) + " 分"));
                }
                if (plan == null || plan.getTypes() == null || plan.getTypes().isEmpty()) {
                    String report = "### 结论\n\n❌ 方案为空，无法校验";
                    if (progressCallback != null) {
                        progressCallback.onProgress(BlackboardProgressEvent.agentFailed(
                                "exam-plan-validator", report));
                        progressCallback.onProgress(BlackboardProgressEvent.error("方案为空，请先生成方案"));
                    }
                    return;
                }
                com.mouhin.knowledge.repository.domain.service.ScorePlanValidator.Result result =
                        com.mouhin.knowledge.repository.domain.service.ScorePlanValidator.validate(plan);
                String report = com.mouhin.knowledge.repository.domain.service.ScorePlanValidator
                        .renderReport(plan, result);

                // 附加：以真流式让模型解读该方案（思考链 + 结果逐字推送到前端"校验过程"卡片）；
                // 模型异常不影响门禁，done 快照仍以规则报告为准。
                String aiAnalysis = streamValidationAnalysis(plan, report, progressCallback);
                if (aiAnalysis != null) {
                    logger.debug("[PlanValidate] 模型解读输出长度 {} 字符 [session={}]", aiAnalysis.length(), sessionId);
                }

                if (result.pass()) {
                    if (progressCallback != null) {
                        progressCallback.onProgress(BlackboardProgressEvent.agentCompleted(
                                "exam-plan-validator", report));
                    }
                    logger.info("[PlanValidate] 校验通过 [session={}, fullMark={}]",
                            sessionId, plan.getTotalFullMark());
                } else {
                    if (progressCallback != null) {
                        progressCallback.onProgress(BlackboardProgressEvent.agentFailed(
                                "exam-plan-validator", report));
                        progressCallback.onProgress(BlackboardProgressEvent.error(
                                "分值校验未通过，共 " + result.issues().size() + " 项硬性错误"));
                    }
                    logger.warn("[PlanValidate] 校验未通过 [session={}, issues={}]",
                            sessionId, result.issues());
                }
            } catch (Exception e) {
                logger.error("[PlanValidate] 校验失败 [session={}]", sessionId, e);
                if (progressCallback != null) {
                    progressCallback.onProgress(BlackboardProgressEvent.error(
                            e.getMessage() != null ? e.getMessage() : "校验过程异常"));
                }
            }
        }, agentExecutor);
    }

    /**
     * 以真流式方式让大模型解读题型分布方案的合理性（思考链与结果逐 token 推送）。
     * <p>仅为增强可读性，<b>不</b>参与门禁判定；模型异常时返回 {@code null}，
     * 校验结论仍由 {@link com.mouhin.knowledge.repository.domain.service.ScorePlanValidator} 决定。</p>
     */
    private String streamValidationAnalysis(ExamPlan plan, String report,
                                            BlackboardProgressCallback progressCallback) {
        if (progressCallback == null) {
            return null;
        }
        try {
            StringBuilder planText = new StringBuilder();
            for (TypePlan t : plan.getTypes()) {
                planText.append("- ").append(t.getLabel()).append("（").append(t.getKey()).append("）：")
                        .append(t.getCount()).append(" 道，每题分值 ").append(t.getPerQuestion())
                        .append("，小计 ").append(t.subtotal()).append(" 分\n");
            }
            String systemPrompt = """
                    你是资深命题质检专家，正在复核一份试卷的"题型分布与分值方案"。
                    请结合学科与学段常识，判断题型搭配、题量、分值占比、难度梯度是否合理，
                    并给出简洁的中文点评与优化建议（2~5 条）。
                    注意：系统已有确定性规则给出硬性校验结论，你无需复述其数字，只做专业解读即可。
                    """;
            String userPrompt = String.format("""
                    本卷满分：%d 分，共 %d 题。
                    题型分布：
                    %s
                    规则校验结果摘要：
                    %s

                    请给出你对该方案合理性的专业解读与优化建议。
                    """, plan.getTotalFullMark(), plan.totalQuestions(), planText, trimText(report, 800));
            return streamingChatGateway.streamCompletion(systemPrompt, userPrompt,
                    (kind, delta) -> progressCallback.onProgress(
                            BlackboardProgressEvent.tokenDelta("exam-plan-validator", kind, delta)));
        } catch (Exception e) {
            logger.warn("[PlanValidate] 模型解读失败（忽略，仅用规则结果）: {}", e.getMessage());
            return null;
        }
    }

    private static String trimText(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    /**
     * 依据已确认的题型分布方案同步生成试卷（单次 LLM 调用，用于同步出卷 / Word 导出）。
     */
    public String generateExam(String topic, String difficulty, String schoolLevel, ExamPlan plan,
                               Permission permission, String category) {
        if (plan == null || plan.getTypes() == null || plan.getTypes().isEmpty()) {
            return "# 试卷生成失败\n\n缺少题型分布方案。";
        }
        ScoreRuleEngine.normalizePlan(plan);
        int total = plan.getTotalFullMark();
        String schemeText = ScoreRuleEngine.renderPlan(plan, topic);

        String filterExpr = permissionDomainService.buildFilterExpression(permission);
        int maxResults = searchMaxResults > 0 ? searchMaxResults : DEFAULT_MAX_RESULTS;
        double minScore = searchMinScore > 0 ? searchMinScore : DEFAULT_MIN_SCORE;
        List<SearchResult> results = authorizedSearch.searchAuthorized(topic, maxResults, minScore, filterExpr, category, permission);
        String knowledgeContext = buildKnowledgeContext(results);

        String userPrompt = buildUserPromptFromPlan(topic, difficulty, plan, knowledgeContext, total, schemeText);
        ChatRequest request = ChatRequest.builder()
                .messages(SystemMessage.from(buildSystemPrompt()), UserMessage.from(userPrompt))
                .build();
        ChatResponse response = chatModel.chat(request);
        String examPaper = response.aiMessage().text();
        if (examPaper == null || examPaper.isBlank()) {
            logger.error("[Exam/Plan] LLM 返回空结果 [topic='{}']", topic);
            return "# 试卷生成失败\n\n大模型返回了空结果，请重试。";
        }
        logger.info("[Exam/Plan] 依据方案生成试卷完成，长度 {} 字符 [topic='{}']", examPaper.length(), topic);
        return examPaper;
    }

    private String buildUserPromptFromPlan(String topic, String difficulty, ExamPlan plan,
                                           String knowledgeContext, int total, String schemeText) {
        String difficultyLabel = switch (difficulty != null ? difficulty : "MEDIUM") {
            case "EASY" -> "简单（基础概念为主）";
            case "HARD" -> "困难（深入理解、综合分析）";
            default -> "中等（理解与应用）";
        };
        StringBuilder typeDesc = new StringBuilder();
        for (TypePlan t : plan.getTypes()) {
            typeDesc.append("- ").append(t.getLabel()).append("：").append(t.getCount()).append(" 道\n");
        }
        return String.format("""
                请根据以下知识库内容，生成一份考试试卷。
                
                **考试主题：** %s
                **难度要求：** %s
                **题型分布：**
                %s
                **目标满分：** %d 分
                **【分值分配方案（严格遵守）】**
                %s
                **知识库参考内容：**
                
                %s
                """, topic, difficultyLabel, typeDesc, total, schemeText, knowledgeContext);
    }

    /**
     * 知识库无召回结果时，调用大模型补充相关资料
     */
    private String callLlmForSupplement(String topic) {
        try {
            String systemPrompt = """
                    你是一个知识助手。当知识库中没有找到与用户问题相关的内容时，
                    请根据你的通用知识提供准确、有用的信息来回答用户的问题。
                    输出要求：使用 Markdown 格式，结构清晰，内容准确。
                    如果确实无法回答，请明确说明。
                    """;
            String userPrompt = "考试主题：" + topic + "\n\n请根据你的知识提供与上述主题相关的核心知识点。";

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

    private String buildSystemPrompt() {
        return """
                你是一个专业的出题专家。你的任务是根据提供的知识库内容，生成高质量的考试试卷。
                
                出题要求：
                1. 题目必须基于知识库内容，不要编造知识库中不存在的知识点
                2. 题目表述清晰准确，避免歧义
                3. 选择题的干扰项要合理，不能明显荒谬
                4. 难度要符合用户要求
                5. 每道题目都要有明确的参考答案
                6. 使用 Markdown 格式输出
                7. 分值必须严格遵循用户提供的【分值分配方案】：卷面总分等于方案给定的本卷满分，每题分值等于方案给定的小题分值，各题分值之和必须等于总分
                8. 每题分值用中文括号 "（X分）" 标注，且必须紧跟在该题题干文字的最末尾、所有选项（A./B./C./D.）之前；严禁把分值放在选项之后或写进选项文本里（如 "D. 选项（3分）" 是错误的）
                
                输出格式（严格遵守）：
                
                # [主题] 考试试卷
                
                **科目：** [主题]  **难度：** [难度]  **总分：** [取自分值分配方案的本卷满分] 分
                
                ---
                
                ## 一、单选题（每题 X 分，共 X 分）
                
                **1.** 题目内容（X分）
                - A. 选项A
                - B. 选项B
                - C. 选项C
                - D. 选项D
                
                ## 二、多选题（每题 X 分，共 X 分）
                
                **X.** 题目内容（X分）
                - A. 选项A
                - B. 选项B
                - C. 选项C
                - D. 选项D
                
                ## 三、判断题（每题 X 分，共 X 分）
                
                **X.** 题目内容（    ）（X分）
                
                ## 四、填空题（每题 X 分，共 X 分）
                
                **X.** 题目内容，空白处用 ______ 表示。（X分）
                
                ## 五、简答题（每题 X 分，共 X 分）
                
                **X.** 题目内容（X分）
                
                ## 六、论述题（每题 X 分，共 X 分）
                
                **X.** 题目内容（X分）
                
                ---
                
                # 参考答案
                
                ## 一、单选题
                1. **B** — 解析简要说明
                ## 二、多选题
                X. **AC** — 解析简要说明（多个选项字母间无逗号）
                ## 三、判断题
                X. **正确**（或 **错误**）— 解析
                ## 四、填空题
                X. **答案**
                ## 五、简答题
                X. 参考答案要点
                ## 六、论述题
                X. 参考答案要点
                
                注意：
                - 只输出用户要求的题型，不要求的题型不要输出
                - 每种题型的题目数量必须严格匹配用户要求
                - 题目序号全局连续编排（1, 2, 3, ...），禁止分节重新从 1 起号
                - 判断题答案统一使用"正确"或"错误"，禁止使用 √/× 符号
                - 多选题答案用纯字母无分隔拼接（如 AC、ABD），不加逗号
                - 分值分配合理：每题分值、大题小计与卷面总分必须严格取自【分值分配方案】，各题分值之和等于总分；若方案含合卷说明，按科目分节组织大题
                """;
    }

    private String buildUserPrompt(String topic, String difficulty,
                                   int singleChoice, int multiChoice, int trueFalse,
                                   int fillBlank, int shortAnswer, int essay,
                                   String knowledgeContext, int total, String schemeText) {

        String difficultyLabel = switch (difficulty != null ? difficulty : "MEDIUM") {
            case "EASY" -> "简单（基础概念为主）";
            case "HARD" -> "困难（深入理解、综合分析）";
            default -> "中等（理解与应用）";
        };

        StringBuilder typeDesc = new StringBuilder();
        if (singleChoice > 0) {
            typeDesc.append("- 单选题：").append(singleChoice).append(" 道\n");
        }
        if (multiChoice > 0) {
            typeDesc.append("- 多选题：").append(multiChoice).append(" 道\n");
        }
        if (trueFalse > 0) {
            typeDesc.append("- 判断题：").append(trueFalse).append(" 道\n");
        }
        if (fillBlank > 0) {
            typeDesc.append("- 填空题：").append(fillBlank).append(" 道\n");
        }
        if (shortAnswer > 0) {
            typeDesc.append("- 简答题：").append(shortAnswer).append(" 道\n");
        }
        if (essay > 0) {
            typeDesc.append("- 论述题：").append(essay).append(" 道\n");
        }

        return String.format("""
                请根据以下知识库内容，生成一份考试试卷。
                
                **考试主题：** %s
                **难度要求：** %s
                **题型分布：**
                %s
                **目标满分：** %d 分
                **【分值分配方案（严格遵守）】**
                %s
                **知识库参考内容：**
                
                %s
                """, topic, difficultyLabel, typeDesc, total, schemeText, knowledgeContext);
    }

    private String buildKnowledgeContext(List<SearchResult> results) {
        if (results.isEmpty()) {
            return "（知识库中未检索到相关内容，请基于通用知识出题）";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            SearchResult chunk = results.get(i);
            sb.append("【知识片段 ").append(i + 1).append("】");
            if (chunk.getDocumentName() != null && !chunk.getDocumentName().isEmpty()) {
                sb.append("（来源：").append(chunk.getDocumentName());
                if (chunk.getPageNumber() != null) {
                    sb.append(" 第").append(chunk.getPageNumber()).append("页");
                }
                sb.append("）");
            }
            sb.append("\n");
            sb.append(chunk.getText()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 出卷即切分（V2）+ 发布门禁：切分落库、契约校验，并据校对要求决定试卷生命周期状态。
     * <p>契约校验不通过（含切分异常）→ {@code VALIDATION_FAILED}，强制人工校对，绝不自动发布；
     * 校验通过且 {@code review-required=false} 且质量分 ≥ 阈值 → {@code PUBLISHED}（自动发布）；
     * 其余通过情形 → {@code REVIEWABLE}（等待管理员 / 出题人校对批准后发布）。</p>
     *
     * @param sessionId  出卷会话（= 试卷标识）
     * @param blackboard 黑板状态（含试卷 / 答案键 / 方案 / 质量分）
     * @return 试卷状态字符串（{@link ExamHistory} 的 STATUS_* 常量）
     */
    private String resolvePaperStatus(String sessionId, BlackboardState blackboard) {
        ExamContractValidator.Result validation;
        try {
            ExamQuestionSplitSupport.SplitOutcome outcome = examQuestionSplitSupport.splitAndPersist(
                    sessionId, blackboard.getExamPaper(), blackboard.getAnswerKey(), blackboard.getExamPlan());
            validation = outcome.validation();
            logger.info("[ExamPipeline] 出卷即切分落库完成 [session={}, questions={}, pass={}]",
                    sessionId, outcome.count(), validation.pass());
        } catch (Exception splitEx) {
            logger.error("[ExamPipeline] 出卷即切分失败，置 VALIDATION_FAILED [session={}]", sessionId, splitEx);
            examAlertGateway.validationFailed(sessionId, -1);
            return ExamHistory.STATUS_VALIDATION_FAILED;
        }

        if (!validation.pass()) {
            logger.warn("[ExamPipeline] 出卷契约校验未通过，强制人工校对 [session={}, issues={}]",
                    sessionId, validation.issues());
            examAlertGateway.validationFailed(sessionId, validation.issues().size());
            return ExamHistory.STATUS_VALIDATION_FAILED;
        }

        int quality = blackboard.getQualityScore();
        if (quality < QUALITY_SCORE_THRESHOLD) {
            examAlertGateway.lowQualityScore(sessionId, quality, QUALITY_SCORE_THRESHOLD);
        }
        if (!examReviewRequired && quality >= QUALITY_SCORE_THRESHOLD) {
            logger.info("[ExamPipeline] 免校对自动发布 [session={}, quality={}]", sessionId, quality);
            return ExamHistory.STATUS_PUBLISHED;
        }
        return ExamHistory.STATUS_REVIEWABLE;
    }

    /**
     * 把试卷状态翻译为面向 SSE 进度面板的一句话提示。
     */
    private String describePaperStatus(String paperStatus) {
        if (ExamHistory.STATUS_PUBLISHED.equals(paperStatus)) {
            return "出卷契约校验通过，已自动发布，学生可开考。";
        }
        if (ExamHistory.STATUS_VALIDATION_FAILED.equals(paperStatus)) {
            return "出卷契约校验未通过，已标记为待人工校对（校验不过不可发布）。";
        }
        return "试卷已生成并通过校验，等待管理员 / 出题人校对批准后发布。";
    }

    /**
     * 保存出卷历史记录
     *
     * @param paperStatus 试卷生命周期状态（PUBLISHED / REVIEWABLE / VALIDATION_FAILED / FAILED）
     */
    private void saveHistory(String sessionId, String topic, String difficulty,
                             String questionConfig, BlackboardState blackboard,
                             Permission permission, String category,
                             int retrievedChunks, String errorMessage, String paperStatus) {
        try {
            ExamHistory history = new ExamHistory();
            history.setSessionId(sessionId);
            history.setTopic(topic);
            history.setDifficulty(difficulty);
            history.setQuestionConfig(questionConfig);
            ExamPlan plan = blackboard.getExamPlan();
            if (plan != null) {
                try {
                    history.setExamPlan(objectMapper.writeValueAsString(plan));
                } catch (Exception pe) {
                    logger.warn("序列化题型分布方案失败，考试端将回退到试卷解析: {}", pe.getMessage());
                }
            }
            history.setExamPaper(blackboard.getExamPaper());
            history.setAnswerKey(blackboard.getAnswerKey());
            history.setDurationMinutes(ExamPaperParser.parseDuration(blackboard.getExamPaper()));
            history.setQualityScore(blackboard.getQualityScore());
            history.setScoreDetail(blackboard.getExamScoreDetail());
            history.setRetrievedChunks(retrievedChunks);
            history.setKeyFindings(blackboard.getKeyFindings());
            history.setReviewFeedback(blackboard.getExamReviewFeedback());
            history.setDifficultyAssessment(blackboard.getDifficultyAssessment());
            history.setDeduplicationReport(blackboard.getDeduplicationReport());
            history.setUserId(permission.getUserId());
            history.setDepartmentId(permission.getDepartmentId());
            history.setCategory(category);
            if (ExamHistory.STATUS_PUBLISHED.equals(paperStatus)) {
                // 自动发布（review-required=false 且校验通过 + 质量达阈）：记系统审核人
                history.markPublished(AUTO_PUBLISH_REVIEWER);
            } else {
                history.setStatus(paperStatus);
            }
            history.setErrorMessage(errorMessage);
            history.setCreateTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
            history.setUpdateTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
            examHistoryGateway.save(history);
            logger.info("出卷历史记录已保存 [session={}, status={}]", sessionId, history.getStatus());
        } catch (Exception e) {
            logger.error("保存出卷历史记录失败 [session={}]", sessionId, e);
        }
    }
}
