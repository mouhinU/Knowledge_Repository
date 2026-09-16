package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.domain.repository.ExamAnswerRepository;
import com.mouhin.knowledge.repository.domain.repository.ExamSessionRepository;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 考试评分应用服务
 * <p>
 * 评分流程：
 * <ol>
 *     <li>客观题（选择题、判断题）自动比对答案评分</li>
 *     <li>主观题（填空、简答、论述）调用 AI 评分</li>
 *     <li>管理端人工复核（可调整分数和反馈）</li>
 *     <li>发布成绩</li>
 * </ol>
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@Service
public class ExamGradingApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(ExamGradingApplicationService.class);

    private static final String AI_GRADING_SYSTEM_PROMPT = """
            你是一位专业的考试阅卷老师。请根据题目、参考答案和学生的回答进行评分。
            
            评分要求：
            1. 严格按照满分上限评分，不能超过满分
            2. 给出合理的分数和简短的评分理由
            3. 如果学生未作答，给 0 分
            4. 答案意思相近即可给分，不要求与参考答案完全一致
            
            请严格按以下格式输出：
            分数：X
            理由：XXX
            """;

    /**
     * 匹配 AI 返回的分数
     */
    private static final Pattern SCORE_PATTERN = Pattern.compile("分数[：:]\\s*(\\d+)");

    /**
     * 匹配 AI 返回的理由
     */
    private static final Pattern REASON_PATTERN = Pattern.compile("理由[：:]\\s*(.+)", Pattern.DOTALL);
    /**
     * 题号标记（行首）：**1. 或 1. 或 1、
     */
    private static final Pattern QNUM_INLINE = Pattern.compile("^\\*{0,2}(\\d{1,3})[.、．]\\s*");
    /**
     * 题号标记（标题）：第1题 / 第 1 题
     */
    private static final Pattern QNUM_HEADER = Pattern.compile("第\\s*(\\d{1,3})\\s*题");
    /**
     * 答案标记：答案/标准答案/参考答案/正确答案 后跟冒号与内容
     */
    private static final Pattern ANSWER_LINE = Pattern.compile(
            "(?:标准答案|参考答案|正确答案|答案)\\s*[：:]\\s*(.*)$");
    private final ExamSessionRepository examSessionRepository;
    private final ExamAnswerRepository examAnswerRepository;
    private final ChatModel chatModel;
    private final ExecutorService agentExecutor;

    public ExamGradingApplicationService(ExamSessionRepository examSessionRepository,
                                         ExamAnswerRepository examAnswerRepository,
                                         ChatModel chatModel) {
        this.examSessionRepository = examSessionRepository;
        this.examAnswerRepository = examAnswerRepository;
        this.chatModel = chatModel;
        this.agentExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 评分一场考试（客观题自动评分 + 主观题 AI 评分）
     * <p>
     * 由 ExamTakingApplicationService.submitExam() 调用。
     * </p>
     *
     * @param sessionId 考试场次 ID
     */
    @Transactional
    public void gradeExam(Long sessionId) {
        ExamSession session = examSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("考试场次不存在: " + sessionId));

        List<ExamAnswer> answers = examAnswerRepository.listBySessionId(sessionId);
        if (answers.isEmpty()) {
            logger.warn("考试场次无答题记录 [session={}]", sessionId);
            return;
        }

        // 解析标准答案，填充到每道题的 correctAnswer
        Map<Integer, String> answerKeyMap = parseAnswerKey(session.getAnswerKey());
        for (ExamAnswer answer : answers) {
            if (answer.getCorrectAnswer() == null || answer.getCorrectAnswer().isBlank()) {
                String correctAnswer = answerKeyMap.get(answer.getQuestionIndex());
                if (correctAnswer != null && !correctAnswer.isBlank()) {
                    answer.setCorrectAnswer(correctAnswer);
                }
            }
        }

        int totalAiScore = 0;

        for (ExamAnswer answer : answers) {
            if (answer.isObjective()) {
                // 客观题自动评分
                gradeObjective(answer);
            } else {
                // 主观题 AI 评分
                gradeSubjectiveWithAi(answer, session);
            }
            totalAiScore += answer.getEffectiveScore();
            examAnswerRepository.update(answer);
        }

        // 更新场次状态
        session.markAiGraded(totalAiScore);
        session.setTotalScore(answers.stream().mapToInt(ExamAnswer::getMaxScore).sum());
        session.setUpdateTime(LocalDateTime.now());
        examSessionRepository.update(session);

        logger.info("评分完成 [session={}, aiScore={}, total={}]",
                sessionId, totalAiScore, session.getTotalScore());
    }

    /**
     * 人工复核单题
     *
     * @param answerId       答题记录 ID
     * @param reviewScore    复核分数
     * @param reviewFeedback 复核反馈
     * @param reviewer       复核人
     */
    @Transactional
    public void reviewAnswer(Long answerId, Integer reviewScore, String reviewFeedback,
                             String reviewer) {
        ExamAnswer answer = examAnswerRepository.findById(answerId)
                .orElseThrow(() -> new IllegalArgumentException("答题记录不存在: " + answerId));

        answer.setReviewScore(reviewScore);
        answer.setReviewFeedback(reviewFeedback);
        answer.setReviewedBy(reviewer);
        answer.setReviewTime(LocalDateTime.now());
        answer.setUpdateTime(LocalDateTime.now());
        examAnswerRepository.update(answer);

        logger.info("人工复核单题 [answerId={}, score={}, reviewer={}]",
                answerId, reviewScore, reviewer);
    }

    /**
     * 完成复核并发布成绩
     *
     * @param sessionId 考试场次 ID
     * @param reviewer  复核人
     */
    @Transactional
    public void publishScore(Long sessionId, String reviewer) {
        ExamSession session = examSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("考试场次不存在: " + sessionId));

        // 重新计算最终成绩（人工复核分数优先）
        List<ExamAnswer> answers = examAnswerRepository.listBySessionId(sessionId);
        int finalScore = answers.stream()
                .mapToInt(ExamAnswer::getEffectiveScore)
                .sum();

        session.markReviewed(finalScore);
        session.markPublished();
        session.setUpdateTime(LocalDateTime.now());
        examSessionRepository.update(session);

        logger.info("成绩已发布 [session={}, finalScore={}, reviewer={}]",
                sessionId, finalScore, reviewer);
    }

    /**
     * 触发 AI 评分（管理员手动触发或定时任务调用）
     * <p>
     * 仅对 SUBMITTED 状态的考试生效。
     * </p>
     *
     * @param sessionId 考试场次 ID
     */
    public void triggerGrading(Long sessionId) {
        ExamSession session = examSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("考试场次不存在: " + sessionId));

        if (!"SUBMITTED".equals(session.getStatus())) {
            throw new IllegalStateException("仅已交卷的考试可以触发评分，当前状态: " + session.getStatus());
        }

        gradeExam(sessionId);
    }

    /**
     * 批量触发所有待评分考试的 AI 评分
     *
     * @return 触发评分的场次数量
     */
    public int batchTriggerGrading() {
        List<ExamSession> pending = examSessionRepository.listPendingGrading(100);
        int count = 0;
        for (ExamSession session : pending) {
            try {
                gradeExam(session.getId());
                count++;
            } catch (Exception e) {
                logger.error("批量评分失败 [session={}]", session.getId(), e);
            }
        }
        logger.info("批量评分完成，共处理 {} 场考试", count);
        return count;
    }

    /**
     * 查询待评分的考试列表（SUBMITTED 状态）
     */
    public List<ExamSession> listPendingGradingSessions(int limit, int offset) {
        return examSessionRepository.listByStatus("SUBMITTED", limit, offset);
    }

    /**
     * 统计待评分数量
     */
    public long countPendingGrading() {
        return examSessionRepository.countByStatus("SUBMITTED");
    }

    /**
     * 查询待复核的考试列表
     */
    public List<ExamSession> listPendingReview(int limit, int offset) {
        return examSessionRepository.listPendingReview(limit, offset);
    }

    // ==================== 内部评分方法 ====================

    /**
     * 统计待复核数量
     */
    public long countPendingReview() {
        return examSessionRepository.countPendingReview();
    }

    /**
     * 查询某场考试的答题记录（含评分详情）
     */
    public List<ExamAnswer> listAnswersWithGrading(Long sessionId) {
        return examAnswerRepository.listBySessionId(sessionId);
    }

    /**
     * 根据场次 ID 获取考试场次信息
     */
    public ExamSession getSessionById(Long sessionId) {
        return examSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("考试场次不存在: " + sessionId));
    }

    /**
     * 客观题自动评分：比对标准答案
     */
    private void gradeObjective(ExamAnswer answer) {
        String correct = normalizeAnswer(answer.getCorrectAnswer());
        String student = normalizeAnswer(answer.getStudentAnswer());

        if (correct == null || correct.isBlank()) {
            // 没有标准答案，给满分（可能是题目问题）
            answer.setCorrect(true);
            answer.setAiScore(answer.getMaxScore());
            answer.setAiFeedback("未设置标准答案，默认给满分");
            return;
        }

        boolean isCorrect = correct.equals(student);
        answer.setCorrect(isCorrect);
        answer.setAiScore(isCorrect ? answer.getMaxScore() : 0);
        answer.setAiFeedback(isCorrect ? "回答正确" : "回答错误，正确答案：" + answer.getCorrectAnswer());
    }

    /**
     * 主观题 AI 评分
     */
    private void gradeSubjectiveWithAi(ExamAnswer answer, ExamSession session) {
        if (answer.getStudentAnswer() == null || answer.getStudentAnswer().isBlank()) {
            answer.setAiScore(0);
            answer.setAiFeedback("学生未作答");
            return;
        }

        String userPrompt = buildGradingPrompt(answer, session);

        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(
                            SystemMessage.from(AI_GRADING_SYSTEM_PROMPT),
                            UserMessage.from(userPrompt)
                    )
                    .build();

            ChatResponse response = chatModel.chat(request);
            String output = response.aiMessage().text();

            if (output == null || output.isBlank()) {
                logger.warn("AI 评分返回空 [question={}]", answer.getQuestionIndex());
                answer.setAiScore(0);
                answer.setAiFeedback("AI 评分失败，请人工复核");
                return;
            }

            // 解析分数
            int score = extractScore(output, answer.getMaxScore());
            String reason = extractReason(output);

            answer.setAiScore(score);
            answer.setAiFeedback(reason);

            logger.debug("AI 评分完成 [question={}, score={}/{}]",
                    answer.getQuestionIndex(), score, answer.getMaxScore());

        } catch (Exception e) {
            logger.error("AI 评分异常 [question={}]", answer.getQuestionIndex(), e);
            answer.setAiScore(0);
            answer.setAiFeedback("AI 评分异常：" + e.getMessage());
        }
    }

    private String buildGradingPrompt(ExamAnswer answer, ExamSession session) {
        StringBuilder sb = new StringBuilder();
        sb.append("考试主题：").append(session.getTopic()).append("\n");
        sb.append("难度：").append(session.getDifficulty()).append("\n\n");
        sb.append("题目（第").append(answer.getQuestionIndex()).append("题）：\n");
        sb.append(answer.getQuestionContent()).append("\n\n");
        sb.append("满分：").append(answer.getMaxScore()).append("分\n\n");

        if (answer.getCorrectAnswer() != null && !answer.getCorrectAnswer().isBlank()) {
            sb.append("参考答案：\n").append(answer.getCorrectAnswer()).append("\n\n");
        }

        sb.append("学生答案：\n").append(answer.getStudentAnswer()).append("\n\n");
        sb.append("请评分。");
        return sb.toString();
    }

    private int extractScore(String output, int maxScore) {
        Matcher matcher = SCORE_PATTERN.matcher(output);
        if (matcher.find()) {
            int score = Integer.parseInt(matcher.group(1));
            return Math.min(score, maxScore);
        }
        // 尝试从文本中提取数字
        Matcher numMatcher = Pattern.compile("(\\d+)").matcher(output);
        if (numMatcher.find()) {
            int score = Integer.parseInt(numMatcher.group(1));
            return Math.min(score, maxScore);
        }
        return 0;
    }

    private String extractReason(String output) {
        Matcher matcher = REASON_PATTERN.matcher(output);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return output.length() > 200 ? output.substring(0, 200) + "..." : output;
    }

    private String normalizeAnswer(String answer) {
        if (answer == null) {
            return null;
        }
        return answer.trim()
                .replaceAll("\\s+", "")
                .toUpperCase();
    }

    /**
     * 从标准答案 Markdown 中解析每道题的正确答案。
     * <p>
     * 兼容两种主流格式：
     * <ul>
     *     <li>行内格式：{@code **1. 答案：B**}</li>
     *     <li>分块格式：{@code ### 第1题 ... **标准答案：B**}</li>
     * </ul>
     * 对于填空 / 论述题，若 {@code 答案：} 后为空，则向下收集内容行直到遇到
     * {@code 解析} 或下一题。
     * </p>
     *
     * @param answerKey 标准答案 Markdown
     * @return 题号 → 答案 的映射
     */
    private Map<Integer, String> parseAnswerKey(String answerKey) {
        Map<Integer, String> result = new HashMap<>();
        if (answerKey == null || answerKey.isBlank()) {
            return result;
        }

        String[] lines = answerKey.split("\\n");
        Integer currentQ = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }

            // 1. 识别题号：优先行内 **N.，其次标题 第N题
            Integer detected = null;
            Matcher inline = QNUM_INLINE.matcher(line);
            if (inline.find()) {
                detected = Integer.parseInt(inline.group(1));
            } else {
                Matcher header = QNUM_HEADER.matcher(line);
                if (header.find()) {
                    detected = Integer.parseInt(header.group(1));
                }
            }
            if (detected != null) {
                currentQ = detected;
            }

            // 2. 识别答案行
            Matcher am = ANSWER_LINE.matcher(line);
            if (am.find() && currentQ != null && !result.containsKey(currentQ)) {
                String value = am.group(1).replaceAll("\\*+", "").trim();
                // 行内没有答案内容（如填空/论述题），向下收集内容行
                if (value.isEmpty()) {
                    value = collectAnswerBody(lines, i + 1);
                }
                if (!value.isEmpty()) {
                    result.put(currentQ, value);
                }
            }
        }

        logger.debug("解析标准答案：共 {} 道题", result.size());
        return result;
    }

    /**
     * 从给定行开始向下收集答案正文，直到遇到"解析"或下一题标记。
     */
    private String collectAnswerBody(String[] lines, int start) {
        StringBuilder sb = new StringBuilder();
        for (int j = start; j < lines.length; j++) {
            String line = lines[j].trim();
            if (line.isEmpty()) {
                continue;
            }
            String clean = line.replaceAll("\\*+", "").trim();
            if (clean.startsWith("解析") || clean.startsWith("评分标准")
                    || QNUM_HEADER.matcher(clean).find()
                    || QNUM_INLINE.matcher(clean).find()) {
                break;
            }
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(clean);
        }
        return sb.toString().trim();
    }
}
