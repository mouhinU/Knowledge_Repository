package com.mouhin.knowledge.repository.application.executor.examgrading;

import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.domain.gateway.ExamAnswerGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.service.ExamGradingProgressCallback;
import com.mouhin.knowledge.repository.domain.service.StreamingChatGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 考试评分支撑组件（app 层）
 *
 * <p>承载评分核心逻辑：客观题自动比对、主观题 AI 评分、评分进度上报与落库 trace，
 * 以及标准答案解析等工具方法。同步评分由 {@code TriggerGradingCmdExe} 调用
 * {@link #gradeExamInternal}（事务边界在执行器）；异步评分由虚拟线程执行器调度。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class ExamGradingSupport {

    private static final Logger logger = LoggerFactory.getLogger(ExamGradingSupport.class);

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
    private static final Pattern ANSWER_LINE = Pattern.compile("(?:标准答案|参考答案|正确答案|答案)\\s*[：:]\\s*(.*)$");

    private final ExamSessionGateway examSessionGateway;
    private final ExamAnswerGateway examAnswerGateway;
    private final StreamingChatGateway streamingChatGateway;
    private final ExecutorService agentExecutor;

    public ExamGradingSupport(ExamSessionGateway examSessionGateway,
                              ExamAnswerGateway examAnswerGateway,
                              StreamingChatGateway streamingChatGateway) {
        this.examSessionGateway = examSessionGateway;
        this.examAnswerGateway = examAnswerGateway;
        this.streamingChatGateway = streamingChatGateway;
        this.agentExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 评分核心实现：客观题自动评分 + 主观题 AI 评分，可选地按每题上报进度并落库 trace。
     * <p>事务边界由调用方执行器持有（同步路径 {@code @Transactional}，异步路径无事务）。</p>
     *
     * @param sessionId 考试场次 ID
     * @param callback  进度回调，null 表示静默模式
     */
    public void gradeExamInternal(Long sessionId, ExamGradingProgressCallback callback) {

        ExamSession session = examSessionGateway.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("考试场次不存在: " + sessionId));

        List<ExamAnswer> answers = examAnswerGateway.listBySessionId(sessionId);
        if (answers.isEmpty()) {
            logger.warn("考试场次无答题记录 [session={}]", sessionId);
            if (callback != null) {
                callback.onComplete(0, 0);
            }
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
            // 重跑评分时清空历史 trace，避免残留
            answer.setAiInput(null);
            answer.setAiRawOutput(null);
        }

        int totalAiScore = 0;
        for (ExamAnswer answer : answers) {
            long start = System.currentTimeMillis();
            int questionIndex = answer.getQuestionIndex() != null ? answer.getQuestionIndex() : 0;
            try {
                if (answer.isObjective()) {
                    gradeObjective(answer, callback);
                } else {
                    gradeSubjectiveWithAi(answer, session, callback);
                }
                long elapsed = System.currentTimeMillis() - start;
                if (callback != null && answer.getAiInput() != null && answer.getAiRawOutput() != null) {
                    callback.onQuestionDone(questionIndex,
                            answer.getAiRawOutput(),
                            answer.getAiScore() != null ? answer.getAiScore() : 0,
                            answer.getMaxScore() != null ? answer.getMaxScore() : 0,
                            answer.getAiFeedback(),
                            elapsed);
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onQuestionError(questionIndex, e.getMessage());
                }
                throw e;
            }
            totalAiScore += answer.getEffectiveScore();
            examAnswerGateway.update(answer);
        }

        // 更新场次状态
        session.markAiGraded(totalAiScore);
        session.setTotalScore(answers.stream().mapToInt(ExamAnswer::getMaxScore).sum());
        session.setUpdateTime(LocalDateTime.now());
        examSessionGateway.update(session);

        if (callback != null) {
            callback.onComplete(answers.size(), totalAiScore);
        }

        logger.info("评分完成 [session={}, aiScore={}, total={}]", sessionId, totalAiScore, session.getTotalScore());
    }

    /**
     * 异步评分：在虚拟线程中执行，通过回调上报每题输入 / 原始输出 / 得分。
     *
     * @param sessionId 考试场次 ID
     * @param callback  进度回调，可为 null
     */
    public void gradeExamAsync(Long sessionId, ExamGradingProgressCallback callback) {
        agentExecutor.submit(() -> {
            try {
                gradeExamInternal(sessionId, callback);
            } catch (Exception e) {
                logger.error("异步评分异常 [session={}]", sessionId, e);
                if (callback != null) {
                    try {
                        callback.onError(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                    } catch (Exception ignored) {
                        // 回调内部异常吞掉
                    }
                }
            }
        });
    }

    /**
     * 异步触发 AI 评分：立即返回，评分过程通过回调上报每题输入 / 原始输出。
     *
     * @param sessionId 考试场次 ID
     * @param callback  进度回调
     */
    public void triggerGradingAsync(Long sessionId, ExamGradingProgressCallback callback) {
        try {

            ExamSession session = examSessionGateway.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("考试场次不存在: " + sessionId));
            if (!"SUBMITTED".equals(session.getStatus())) {
                if (callback != null) {
                    callback.onError("仅已交卷的考试可以触发评分，当前状态: " + session.getStatus());
                }
                return;
            }
        } catch (Exception e) {
            if (callback != null) {
                callback.onError(e.getMessage());
            }
            return;
        }
        gradeExamAsync(sessionId, callback);
    }

    // ==================== 内部评分方法 ====================

    /**
     * 客观题自动评分：按题型分派比对逻辑（含多选部分给分）
     *
     * @param answer   待评分的答题记录
     * @param callback 进度回调（可为 null）
     */
    private void gradeObjective(ExamAnswer answer, ExamGradingProgressCallback callback) {
        int qIdx = answer.getQuestionIndex() != null ? answer.getQuestionIndex() : 0;
        String input = buildObjectiveInput(answer);

        if (callback != null) {
            callback.onQuestionStart(qIdx, answer.getQuestionType(), input);
        }
        answer.setAiInput(input);

        String rawCorrect = answer.getCorrectAnswer();
        String rawStudent = answer.getStudentAnswer();
        String type = answer.getQuestionType();
        int maxScore = answer.getMaxScore() != null ? answer.getMaxScore() : 0;

        // 缺少标准答案：判 0 分 + 标记待复核 + 告警，绝不静默给满分
        if (rawCorrect == null || rawCorrect.isBlank()) {
            logger.warn("grading: sessionId answer_key missing for questionIndex={}, forcing 0 + review", qIdx);
            answer.setCorrect(false);
            answer.setAiScore(0);
            answer.setAiFeedback("缺少标准答案，待人工确认");
            answer.setAiRawOutput("客观题自动比对：缺少参考答案 → 判 0 分（待复核）");
            return;
        }

        // 按题型分派规范化与比较
        String normalizedCorrect = normalizeForCompare(rawCorrect, type);
        String normalizedStudent = normalizeForCompare(rawStudent, type);
        int score;
        boolean isCorrect;
        String feedbackDetail;

        if ("MULTI_CHOICE".equals(type)) {
            // 多选部分给分：全对满分 / 少选半分 / 含错选或未选 0 分
            java.util.Set<Character> correctSet = parseChoiceSet(normalizedCorrect);
            java.util.Set<Character> studentSet = parseChoiceSet(normalizedStudent);
            if (studentSet.isEmpty()) {
                score = 0;
                isCorrect = false;
                feedbackDetail = "未选择任何选项";
            } else if (!correctSet.containsAll(studentSet)) {
                score = 0;
                isCorrect = false;
                feedbackDetail = "含错选（选择了不在正确答案中的选项）";
            } else if (studentSet.equals(correctSet)) {
                score = maxScore;
                isCorrect = true;
                feedbackDetail = "回答正确（全对）";
            } else {
                // 少选（student ⊂ correct）→ 半分，向下取整
                score = maxScore / 2;
                isCorrect = false;
                feedbackDetail = "少选（正确答案：" + rawCorrect + "），得半分";
            }
        } else {
            // 单选 / 判断：等值比较
            boolean match = normalizedCorrect != null && normalizedCorrect.equals(normalizedStudent);
            score = match ? maxScore : 0;
            isCorrect = match;
            feedbackDetail = match ? "回答正确" : "回答错误，正确答案：" + rawCorrect;
        }

        answer.setCorrect(isCorrect);
        answer.setAiScore(score);
        answer.setAiFeedback(feedbackDetail);
        answer.setAiRawOutput("客观题自动比对：期望[" + rawCorrect + "] 实际["
                + (rawStudent == null ? "" : rawStudent) + "] → "
                + (isCorrect ? "匹配" : "不匹配") + "（得分" + score + "/" + maxScore + "）");
    }

    /**
     * 组装客观题的 trace 输入（题目 / 期望 / 实际）
     */
    private String buildObjectiveInput(ExamAnswer answer) {
        StringBuilder sb = new StringBuilder();
        sb.append("[客观题自动比对 · 无需 LLM]\n");
        sb.append("题型：").append(answer.getQuestionType()).append("\n");
        sb.append("题目（第").append(answer.getQuestionIndex()).append("题）：\n").append(answer.getQuestionContent()).append("\n\n");
        sb.append("满分：").append(answer.getMaxScore()).append("分\n");
        sb.append("参考答案：").append(answer.getCorrectAnswer() == null ? "-" : answer.getCorrectAnswer()).append("\n");
        sb.append("学生答案：").append(answer.getStudentAnswer() == null ? "" : answer.getStudentAnswer()).append("\n");
        return sb.toString();
    }

    /**
     * 主观题 AI 评分
     */
    private void gradeSubjectiveWithAi(ExamAnswer answer, ExamSession session, ExamGradingProgressCallback callback) {
        int qIdx = answer.getQuestionIndex() != null ? answer.getQuestionIndex() : 0;
        String userPrompt = buildGradingPrompt(answer, session);
        String fullInput = "[System]\n" + AI_GRADING_SYSTEM_PROMPT + "\n\n[User]\n" + userPrompt;
        answer.setAiInput(fullInput);

        if (callback != null) {
            callback.onQuestionStart(qIdx, answer.getQuestionType(), fullInput);
        }

        if (answer.getStudentAnswer() == null || answer.getStudentAnswer().isBlank()) {
            answer.setAiScore(0);
            answer.setAiFeedback("学生未作答，跳过 AI 评分");
            answer.setAiRawOutput("[SKIP] 学生答案为空 → 直接 0 分");
            return;
        }

        try {
            String output = streamingChatGateway.streamCompletion(
                    AI_GRADING_SYSTEM_PROMPT, userPrompt,
                    (kind, delta) -> {
                        if (callback != null) {
                            callback.onQuestionToken(qIdx, kind, delta);
                        }
                    });

            if (output == null || output.isBlank()) {
                logger.warn("AI 评分返回空 [question={}]", answer.getQuestionIndex());
                answer.setAiScore(0);
                answer.setAiFeedback("AI 评分失败，请人工复核");
                answer.setAiRawOutput("[EMPTY] 模型返回空内容");
                return;
            }

            // 解析分数
            int score = extractScore(output, answer.getMaxScore());
            String reason = extractReason(output);

            answer.setAiScore(score);
            answer.setAiFeedback(reason);
            answer.setAiRawOutput(output);

            logger.debug("AI 评分完成 [question={}, score={}/{}]",
                    answer.getQuestionIndex(), score, answer.getMaxScore());

        } catch (Exception e) {
            logger.error("AI 评分异常 [question={}]", answer.getQuestionIndex(), e);
            answer.setAiScore(0);
            answer.setAiFeedback("AI 评分异常：" + e.getMessage());
            answer.setAiRawOutput("[ERROR] " + (e.getMessage() != null ? e.getMessage() : e.getClass().getName()));
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

    // ==================== 答案规范化工具（按题型分派） ====================

    /**
     * 按题型规范化答案字符串，用于客观题等值比较。
     * <ul>
     *     <li>多选题：拆字母 → 去重 → 排序 → 拼接（使 "A,C"=="AC"=="C,A"）</li>
     *     <li>判断题：符号字典折叠 → "TRUE" / "FALSE"（正确=√=T=对=是=Y → TRUE）</li>
     *     <li>其他（单选等）：trim + 全半角 + 去空白 + 大写</li>
     * </ul>
     *
     * @param raw           原始答案字符串
     * @param questionType  题型 key
     * @return 规范化后的比较用字符串（null 入参返回 null）
     */
    private String normalizeForCompare(String raw, String questionType) {
        if (raw == null) {
            return null;
        }
        String base = toHalfWidth(raw).trim().replaceAll("\\s+", "").toUpperCase();
        if ("MULTI_CHOICE".equals(questionType)) {
            return normalizeChoiceSet(base);
        }
        if ("TRUE_FALSE".equals(questionType)) {
            return normalizeTrueFalse(base);
        }
        return base;
    }

    /**
     * 多选规范化：仅保留大写字母 A-D，去重排序拼接。
     */
    private String normalizeChoiceSet(String s) {
        return s.chars()
                .filter(c -> c >= 'A' && c <= 'D')
                .distinct()
                .sorted()
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    /**
     * 解析多选字母集合：从已规范化的纯字母串拆为 Set。
     */
    private java.util.Set<Character> parseChoiceSet(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return java.util.Collections.emptySet();
        }
        java.util.Set<Character> set = new java.util.HashSet<>();
        for (char c : normalized.toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                set.add(c);
            }
        }
        return set;
    }

    /**
     * 判断题符号字典：所有"对"的变体 → TRUE，所有"错"的变体 → FALSE。
     */
    private String normalizeTrueFalse(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        // 正向集
        if ("正确".equals(s) || "√".equals(s) || "T".equals(s) || "TRUE".equals(s)
                || "对".equals(s) || "Y".equals(s) || "是".equals(s) || "✓".equals(s)
                || "TRUE".equals(s) || "对".equals(s) || "YES".equals(s)) {
            return "TRUE";
        }
        // 负向集
        if ("错误".equals(s) || "×".equals(s) || "X".equals(s) || "FALSE".equals(s)
                || "错".equals(s) || "N".equals(s) || "否".equals(s) || "✗".equals(s)
                || "✕".equals(s) || "NO".equals(s)) {
            return "FALSE";
        }
        return s;
    }

    /**
     * 全角字符 → 半角（仅处理常见全角 ASCII 范围 0xFF01-0xFF5E，以及全角空格 0x3000）。
     */
    private String toHalfWidth(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\uFF01' && c <= '\uFF5E') {
                sb.append((char) (c - 0xFEE0));
            } else if (c == '\u3000') {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
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

    public ExamSessionGateway examSessionGateway() {
        return examSessionGateway;
    }

    public ExamAnswerGateway examAnswerGateway() {
        return examAnswerGateway;
    }
}
