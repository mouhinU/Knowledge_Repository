package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import com.mouhin.knowledge.repository.domain.model.valueobject.SearchResult;
import com.mouhin.knowledge.repository.domain.repository.ExamHistoryRepository;
import com.mouhin.knowledge.repository.domain.service.BlackboardAgent;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import com.mouhin.knowledge.repository.domain.service.PermissionDomainService;
import com.mouhin.knowledge.repository.infrastructure.milvus.MilvusVectorStoreService;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 试卷生成应用服务
 * <p>
 * 支持两种模式：
 * <ul>
 *     <li>同步模式（generateExam）：单次 LLM 调用生成试卷，用于 Word 导出</li>
 *     <li>异步模式（generateExamAsync）：7 步 Agent 流水线（含并行），支持 SSE 实时进度</li>
 * </ul>
 * 异步流水线：(知识检索 ∥ 分值分配) → 试卷编写 → (答案生成 ∥ 难度校准) → (内容审核 ∥ 查重去重)
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-13
 */
@Service
public class ExamGenerationApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(ExamGenerationApplicationService.class);

    private static final int DEFAULT_MAX_RESULTS = 20;
    private static final double DEFAULT_MIN_SCORE = 0.3;
    private static final int QUALITY_SCORE_THRESHOLD = 80;
    private static final int MAX_REVIEW_RETRIES = 5;

    private final BlackboardAgent examResearcherAgent;
    private final BlackboardAgent examScoringAgent;
    private final BlackboardAgent examWriterAgent;
    private final BlackboardAgent answerKeyGeneratorAgent;
    private final BlackboardAgent examReviewerAgent;
    private final BlackboardAgent examCalibratorAgent;
    private final BlackboardAgent examDeduplicatorAgent;
    private final MilvusVectorStoreService vectorStoreService;
    private final PermissionDomainService permissionDomainService;
    private final ChatModel chatModel;
    private final ExamHistoryRepository examHistoryRepository;

    private final ExecutorService agentExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @Value("${knowledge.blackboard.search.max-results:20}")
    private int searchMaxResults;

    @Value("${knowledge.blackboard.search.min-score:0.3}")
    private double searchMinScore;

    public ExamGenerationApplicationService(
            @Qualifier("examResearcherAgent") BlackboardAgent examResearcherAgent,
            @Qualifier("examScoringAgent") BlackboardAgent examScoringAgent,
            @Qualifier("examWriterAgent") BlackboardAgent examWriterAgent,
            @Qualifier("answerKeyGeneratorAgent") BlackboardAgent answerKeyGeneratorAgent,
            @Qualifier("examReviewerAgent") BlackboardAgent examReviewerAgent,
            @Qualifier("examCalibratorAgent") BlackboardAgent examCalibratorAgent,
            @Qualifier("examDeduplicatorAgent") BlackboardAgent examDeduplicatorAgent,
            MilvusVectorStoreService vectorStoreService,
            PermissionDomainService permissionDomainService,
            ChatModel chatModel,
            ExamHistoryRepository examHistoryRepository) {
        this.examResearcherAgent = examResearcherAgent;
        this.examScoringAgent = examScoringAgent;
        this.examWriterAgent = examWriterAgent;
        this.answerKeyGeneratorAgent = answerKeyGeneratorAgent;
        this.examReviewerAgent = examReviewerAgent;
        this.examCalibratorAgent = examCalibratorAgent;
        this.examDeduplicatorAgent = examDeduplicatorAgent;
        this.vectorStoreService = vectorStoreService;
        this.permissionDomainService = permissionDomainService;
        this.chatModel = chatModel;
        this.examHistoryRepository = examHistoryRepository;
    }

    /**
     * 异步启动试卷生成（7 步 Agent 流水线，含并行），立即返回。
     *
     * @param topic            考试主题
     * @param difficulty       难度（EASY / MEDIUM / HARD）
     * @param questionConfig   题型配置描述
     * @param permission       用户权限上下文
     * @param progressCallback 进度回调
     * @param sessionId        会话 ID
     * @param schoolLevel      学段（PRIMARY / JUNIOR / SENIOR，可空由主题识别）
     */
    public void generateExamAsync(String topic, String difficulty, String questionConfig,
                                  Permission permission, BlackboardProgressCallback progressCallback,
                                  String sessionId, String category, String schoolLevel) {
        logger.info("启动异步试卷生成 [session={}, topic='{}', difficulty='{}', category='{}', level='{}']",
                sessionId, topic, difficulty, category, schoolLevel);

        if (progressCallback != null) {
            progressCallback.onProgress(
                    BlackboardProgressEvent.phaseChanged(BlackboardPhase.INIT, "正在初始化..."));
        }

        CompletableFuture.runAsync(
                () -> executeExamPipeline(sessionId, topic, difficulty, questionConfig, permission,
                        progressCallback, category, schoolLevel),
                agentExecutor);
    }

    /**
     * 后台执行完整的试卷生成流水线
     */
    private void executeExamPipeline(String sessionId, String topic, String difficulty,
                                     String questionConfig, Permission permission,
                                     BlackboardProgressCallback callback, String category,
                                     String schoolLevel) {
        BlackboardState blackboard = new BlackboardState(sessionId, topic);
        blackboard.setUserId(permission.getUserId());
        blackboard.setDepartmentId(permission.getDepartmentId());
        blackboard.setRoles(permission.getRoles());
        blackboard.setAdmin(permission.isAdmin());
        blackboard.setExamDifficulty(difficulty);
        blackboard.setExamQuestionConfig(questionConfig);
        blackboard.setExamSchoolLevel(schoolLevel);

        try {
            // 1. 知识库检索
            if (callback != null) {
                callback.onProgress(BlackboardProgressEvent.phaseChanged(
                        BlackboardPhase.RESEARCH, "正在检索知识库..."));
            }

            String filterExpr = permissionDomainService.buildFilterExpression(permission);
            int maxResults = searchMaxResults > 0 ? searchMaxResults : DEFAULT_MAX_RESULTS;
            double minScore = searchMinScore > 0 ? searchMinScore : DEFAULT_MIN_SCORE;

            List<SearchResult> results = vectorStoreService.search(topic, maxResults, minScore, filterExpr, category);
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

            // 2. 出卷研究员 Agent ∥ 分值分配 Agent（无数据依赖，并行执行）
            if (callback != null) {
                callback.onProgress(BlackboardProgressEvent.phaseChanged(
                        BlackboardPhase.RESEARCH, "正在检索知识库并计算分值分配..."));
            }
            CompletableFuture<Void> researchFuture = CompletableFuture.runAsync(
                    () -> examResearcherAgent.execute(blackboard, callback), agentExecutor);
            CompletableFuture<Void> scoringFuture = CompletableFuture.runAsync(
                    () -> examScoringAgent.execute(blackboard, callback), agentExecutor);
            CompletableFuture.allOf(researchFuture, scoringFuture).join();

            // 3-5. 试卷编写 → (答案生成 ∥ 难度校准) → (内容审核 ∥ 查重去重)
            // 低分自动重试，最多 MAX_REVIEW_RETRIES 次
            for (int attempt = 0; attempt <= MAX_REVIEW_RETRIES; attempt++) {
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
                if (score >= QUALITY_SCORE_THRESHOLD || attempt == MAX_REVIEW_RETRIES) {
                    logger.info("[ExamPipeline] 审核完成 [session={}, attempt={}, score={}, threshold={}]",
                            sessionId, attempt + 1, score, QUALITY_SCORE_THRESHOLD);
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

            // 保存历史记录
            saveHistory(sessionId, topic, difficulty, questionConfig, blackboard, permission,
                    category, results.size(), null);

        } catch (Exception e) {
            logger.error("试卷生成失败 [session={}]", sessionId, e);
            blackboard.markFailed(e.getMessage());

            // 保存失败记录
            saveHistory(sessionId, topic, difficulty, questionConfig, blackboard, permission,
                    category, 0, e.getMessage());

            if (callback != null) {
                callback.onProgress(BlackboardProgressEvent.error(e.getMessage()));
            }
        }
    }

    /**
     * 同步生成试卷（单次 LLM 调用，用于 Word 导出）
     */
    public String generateExam(String topic, String difficulty, String schoolLevel,
                               int singleChoice, int multiChoice, int trueFalse,
                               int fillBlank, int shortAnswer, int essay,
                               Permission permission, String category) {

        logger.info("同步生成试卷 [topic='{}', difficulty='{}', level='{}', total={}]",
                topic, difficulty, schoolLevel,
                singleChoice + multiChoice + trueFalse + fillBlank + shortAnswer + essay);

        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
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

        com.mouhin.knowledge.repository.domain.service.ScoreRuleEngine.SchoolLevel level =
                com.mouhin.knowledge.repository.domain.service.ScoreRuleEngine.resolveLevel(topic, schoolLevel);
        int total = com.mouhin.knowledge.repository.domain.service.ScoreRuleEngine
                .resolveTotalFullMark(topic, level, schoolLevel);
        String schemeText = com.mouhin.knowledge.repository.domain.service.ScoreRuleEngine
                .renderScheme(
                        com.mouhin.knowledge.repository.domain.service.ScoreRuleEngine.allocate(total, counts),
                        topic, level);

        String filterExpr = permissionDomainService.buildFilterExpression(permission);
        int maxResults = searchMaxResults > 0 ? searchMaxResults : DEFAULT_MAX_RESULTS;
        double minScore = searchMinScore > 0 ? searchMinScore : DEFAULT_MIN_SCORE;

        List<SearchResult> results = vectorStoreService.search(topic, maxResults, minScore, filterExpr, category);
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

                输出格式（严格遵守）：

                # [主题] 考试试卷

                **科目：** [主题]  **难度：** [难度]  **总分：** [取自分值分配方案的本卷满分] 分

                ---

                ## 一、单选题（每题 X 分，共 X 分）

                **1.** 题目内容
                - A. 选项A
                - B. 选项B
                - C. 选项C
                - D. 选项D

                ## 二、多选题（每题 X 分，共 X 分）

                **X.** 题目内容
                - A. 选项A
                - B. 选项B
                - C. 选项C
                - D. 选项D

                ## 三、判断题（每题 X 分，共 X 分）

                **X.** 题目内容（    ）

                ## 四、填空题（每题 X 分，共 X 分）

                **X.** 题目内容，空白处用 ______ 表示。

                ## 五、简答题（每题 X 分，共 X 分）

                **X.** 题目内容

                ## 六、论述题（每题 X 分，共 X 分）

                **X.** 题目内容

                ---

                # 参考答案

                ## 一、单选题
                1. **B** — 解析简要说明
                ## 二、多选题
                X. **ABD** — 解析简要说明
                ## 三、判断题
                X. **√**（或 **×**）— 解析
                ## 四、填空题
                X. **答案**
                ## 五、简答题
                X. 参考答案要点
                ## 六、论述题
                X. 参考答案要点

                注意：
                - 只输出用户要求的题型，不要求的题型不要输出
                - 每种题型的题目数量必须严格匹配用户要求
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
     * 保存出卷历史记录
     */
    private void saveHistory(String sessionId, String topic, String difficulty,
                             String questionConfig, BlackboardState blackboard,
                             Permission permission, String category,
                             int retrievedChunks, String errorMessage) {
        try {
            ExamHistory history = new ExamHistory();
            history.setSessionId(sessionId);
            history.setTopic(topic);
            history.setDifficulty(difficulty);
            history.setQuestionConfig(questionConfig);
            history.setExamPaper(blackboard.getExamPaper());
            history.setAnswerKey(blackboard.getAnswerKey());
            history.setDurationMinutes(
                    com.mouhin.knowledge.repository.application.util.ExamPaperParser
                            .parseDuration(blackboard.getExamPaper()));
            history.setQualityScore(blackboard.getQualityScore());
            history.setRetrievedChunks(retrievedChunks);
            history.setKeyFindings(blackboard.getKeyFindings());
            history.setReviewFeedback(blackboard.getExamReviewFeedback());
            history.setDifficultyAssessment(blackboard.getDifficultyAssessment());
            history.setDeduplicationReport(blackboard.getDeduplicationReport());
            history.setUserId(permission.getUserId());
            history.setDepartmentId(permission.getDepartmentId());
            history.setCategory(category);
            history.setStatus(errorMessage != null ? "FAILED" : "COMPLETED");
            history.setErrorMessage(errorMessage);
            history.setCreateTime(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")));
            history.setUpdateTime(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")));
            examHistoryRepository.save(history);
            logger.info("出卷历史记录已保存 [session={}, status={}]", sessionId, history.getStatus());
        } catch (Exception e) {
            logger.error("保存出卷历史记录失败 [session={}]", sessionId, e);
        }
    }

    /**
     * 查询出卷历史列表
     *
     * @param limit 最大返回数量
     * @return 历史记录列表（按时间倒序）
     */
    public List<ExamHistory> listHistory(int limit) {
        return examHistoryRepository.listRecent(limit > 0 ? limit : 20);
    }

    /**
     * 分页查询出卷历史
     *
     * @param page 页码（从 0 开始）
     * @param size 每页数量
     * @return 分页结果（records / total / page / size）
     */
    public Map<String, Object> pageHistory(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size > 0 ? size : 10;
        int offset = safePage * safeSize;
        List<ExamHistory> records = examHistoryRepository.listPage(safeSize, offset);
        long total = examHistoryRepository.countAll();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    /**
     * 根据会话 ID 查询出卷历史详情
     *
     * @param sessionId 会话 ID
     * @return 历史记录，不存在时返回 null
     */
    public ExamHistory getHistoryBySessionId(String sessionId) {
        return examHistoryRepository.findBySessionId(sessionId).orElse(null);
    }

    /**
     * 删除出卷历史记录
     *
     * @param sessionId 会话 ID
     */
    public void deleteHistory(String sessionId) {
        examHistoryRepository.deleteBySessionId(sessionId);
        logger.info("出卷历史记录已删除 [session={}]", sessionId);
    }
}
