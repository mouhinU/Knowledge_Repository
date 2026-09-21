package com.mouhin.knowledge.repository.application.executor.examgrading;

import com.mouhin.knowledge.repository.application.executor.examgeneration.ExamQuestionSplitSupport;
import com.mouhin.knowledge.repository.application.service.ExamStructuredQuestionSupport;
import com.mouhin.knowledge.repository.application.util.AgentExecutorFactory;
import com.mouhin.knowledge.repository.application.util.ExamPaperParser;
import com.mouhin.knowledge.repository.domain.gateway.ExamAlertGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamAnswerGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamQuestionGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.service.ExamAnswerNormalizer;
import com.mouhin.knowledge.repository.domain.service.ExamGradingProgressCallback;
import com.mouhin.knowledge.repository.domain.service.StreamingChatGateway;
import jakarta.annotation.PreDestroy;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 考试评分支撑组件（app 层）
 *
 * <p>承载评分核心逻辑：客观题自动比对、主观题 AI 评分、评分进度上报与落库 trace， 以及标准答案解析等工具方法。评分<b>刻意不置于数据库事务内</b>（含分钟级 LLM 调用），
 * 并发正确性由原子状态机保证：入口 {@code SUBMITTED→GRADING} CAS 认领、逐题心跳续约、 终态 {@code GRADING→AI_GRADED} CAS
 * 落库。同步评分由 {@code TriggerGradingCmdExe} 调用 {@link #gradeExamInternal}，异步评分由虚拟线程执行器调度，二者共享同一套 CAS
 * 语义。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
@Slf4j
public class ExamGradingSupport {

    private static final String AI_GRADING_SYSTEM_PROMPT =
            """
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

    /** 匹配 AI 返回的分数 */
    private static final Pattern SCORE_PATTERN = Pattern.compile("分数[：:]\\s*(\\d+)");

    /** 匹配 AI 返回的理由 */
    private static final Pattern REASON_PATTERN = Pattern.compile("理由[：:]\\s*(.+)", Pattern.DOTALL);

    /** 关闭评分 Agent 线程池时等待在途任务收尾的秒数 */
    private static final int AGENT_SHUTDOWN_AWAIT_SECONDS = 10;

    /** 场次状态：已交卷，待评分 */
    private static final String STATUS_SUBMITTED = "SUBMITTED";

    /** 场次状态：AI 评分完成（终态之一） */
    private static final String STATUS_AI_GRADED = "AI_GRADED";

    private final ExamSessionGateway examSessionGateway;
    private final ExamAnswerGateway examAnswerGateway;
    private final ExamQuestionGateway examQuestionGateway;
    private final ExamStructuredQuestionSupport structuredQuestionSupport;
    private final ExamQuestionSplitSupport examQuestionSplitSupport;
    private final StreamingChatGateway streamingChatGateway;
    private final ExamAlertGateway examAlertGateway;
    private final ExecutorService agentExecutor;

    public ExamGradingSupport(
            ExamSessionGateway examSessionGateway,
            ExamAnswerGateway examAnswerGateway,
            ExamQuestionGateway examQuestionGateway,
            ExamStructuredQuestionSupport structuredQuestionSupport,
            ExamQuestionSplitSupport examQuestionSplitSupport,
            StreamingChatGateway streamingChatGateway,
            ExamAlertGateway examAlertGateway) {
        this.examSessionGateway = examSessionGateway;
        this.examAnswerGateway = examAnswerGateway;
        this.examQuestionGateway = examQuestionGateway;
        this.structuredQuestionSupport = structuredQuestionSupport;
        this.examQuestionSplitSupport = examQuestionSplitSupport;
        this.streamingChatGateway = streamingChatGateway;
        this.examAlertGateway = examAlertGateway;
        this.agentExecutor = AgentExecutorFactory.newBoundedAgentPool("exam-grading");
    }

    /** 容器优雅停机时关闭评分异步线程池：先温和 shutdown 拒绝新任务， 给在途评分留出短暂收尾窗口，超时则强制中断，杜绝停机后遗留在跑线程。 */
    @PreDestroy
    public void shutdown() {
        agentExecutor.shutdown();
        try {
            if (!agentExecutor.awaitTermination(
                    AGENT_SHUTDOWN_AWAIT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)) {
                agentExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            agentExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 评分核心实现：客观题自动评分 + 主观题 AI 评分，可选地按每题上报进度并落库 trace。
     *
     * <p>事务边界由调用方执行器持有（同步路径 {@code @Transactional}，异步路径无事务）。
     *
     * @param sessionId 考试场次 ID
     * @param callback 进度回调，null 表示静默模式
     */
    public void gradeExamInternal(Long sessionId, ExamGradingProgressCallback callback) {

        ExamSession session =
                examSessionGateway
                        .findById(sessionId)
                        .orElseThrow(() -> new IllegalArgumentException("考试场次不存在: " + sessionId));

        List<ExamAnswer> answers = examAnswerGateway.listBySessionId(sessionId);
        if (answers.isEmpty()) {
            // DATA-5：全空答卷（未落任何作答行）此前直接 return，导致场次永远停在 SUBMITTED、
            // 被定时任务反复认领却永不进入终态。改为仅告警并继续走后续流程：逐题循环空转 0 次
            // （totalAiScore 保持 0），随后照常执行认领 CAS 与终态 CAS，按卷面满分合计、得分 0 落
            // AI_GRADED（语义等同"客观题全部零分判定"），使空卷也能收敛到终态、可被人工复核。
            log.warn("考试场次无答题记录，按零分落终态 [session={}]", sessionId);
        }

        // CONC-1：并发认领改用围栏令牌——原子将 SUBMITTED 抢占为 GRADING 并写入一次性 UUID，
        // 返回令牌；后续心跳 / 终态 / 失败回退均以「status==GRADING 且令牌匹配」为谓词。
        // 认领返回 null 说明已有评分流程持有本场次（或已非 SUBMITTED），直接优雅结束、不重复评分。
        // 相比裸状态 CAS，被超时回收再重新认领后令牌会轮换，令在途旧评分者立即失去所有权判定，
        // 杜绝新旧评分者对同一场次交叉写、覆盖终态。
        String gradingToken = examSessionGateway.claimForGrading(sessionId);
        if (gradingToken == null) {
            log.info("评分认领失败，跳过重复评分 [session={}]", sessionId);
            if (callback != null) {
                callback.onComplete(0, 0);
            }
            return;
        }
        session.markGrading();

        // 认领成功后的任何异常，都以本次令牌安全回退（release 内含令牌匹配判定，绝不误伤接管者），再向上抛出。
        try {

            // V2 出卷即切分 · 阶段 2-A：标准答案唯一来源为结构化题目行 kb_exam_question
            // （生成期一次性绑定 + 契约校验，缺失时 resolveStructuredAnswerMap 惰性回灌）。
            // 已删除自由文本答案键（parseAnswerKey）回退——下游纯读结构，杜绝评分 / 展示双解析口径漂移。
            Map<Integer, String> structuredAnswers = resolveStructuredAnswerMap(session);
            applyStructuredAnswers(answers, structuredAnswers);

            int totalAiScore = 0;
            boolean stillOwner = true;
            for (ExamAnswer answer : answers) {
                long start = System.currentTimeMillis();
                int questionIndex =
                        answer.getQuestionIndex() != null ? answer.getQuestionIndex() : 0;
                try {
                    if (answer.isObjective()) {
                        gradeObjective(answer, callback);
                    } else {
                        gradeSubjectiveWithAi(answer, session, callback);
                    }
                    long elapsed = System.currentTimeMillis() - start;
                    if (callback != null
                            && answer.getAiInput() != null
                            && answer.getAiRawOutput() != null) {
                        callback.onQuestionDone(
                                questionIndex,
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
                // 兜底清残留：若本轮该题意外无 trace（题型变更等），显式将 ai_input / ai_raw_output 置 NULL。
                // updateById 因 NOT_NULL 会跳过 null 字段无法清列，只能走 clearAiTrace 专用通道。
                if (answer.getAiInput() == null || answer.getAiRawOutput() == null) {
                    examAnswerGateway.clearAiTrace(answer.getId());
                }

                // 心跳续约（带围栏令牌）：仅当仍为本次认领的 GRADING 时刷新 update_time；令牌失配说明已被
                // 超时回收并重新认领，立即停止，杜绝两个评分者对同场次交叉写。
                if (!examSessionGateway.touchGradingHeartbeat(sessionId, gradingToken)) {
                    log.warn("评分心跳失败，场次已被接管，停止本次评分 [session={}]", sessionId);
                    stillOwner = false;
                    break;
                }
            }
            if (!stillOwner) {
                if (callback != null) {
                    callback.onError("评分被中断：场次已被其它流程接管");
                }
                return;
            }

            // V2 阶段 2-E：卷面总分取试卷结构化题目满分合计（与答题情况无关），
            // 避免"未答题无落库行 → 分母偏小 → 得分率虚高"。结构化行缺失时回退已入库答案行合计。
            int paperTotal = resolvePaperTotalScore(session, answers);

            // 终态原子落库（带围栏令牌 CAS GRADING→AI_GRADED + 分数）。若期间已被超时回收并重新认领
            // （令牌已轮换），本次令牌失配返回 false，本场慢速评分不得再盲写覆盖，交由接管者收尾。
            session.markAiGraded(totalAiScore);
            session.setTotalScore(paperTotal);
            if (!examSessionGateway.completeGrading(
                    sessionId, gradingToken, STATUS_AI_GRADED, totalAiScore, paperTotal)) {
                log.warn("评分终态落库失败：场次已非本次认领的 GRADING（疑被接管），放弃本次结果 [session={}]", sessionId);
                if (callback != null) {
                    callback.onError("评分结果未被采纳：场次已被其它流程接管");
                }
                return;
            }

            if (callback != null) {
                callback.onComplete(answers.size(), totalAiScore);
            }

            log.info(
                    "评分完成 [session={}, aiScore={}, total={}]", sessionId, totalAiScore, paperTotal);
        } catch (RuntimeException | Error ex) {
            // 认领后任何异常：以本次令牌安全回退为 SUBMITTED（令牌失配则不动，不误伤接管者），再上抛。
            log.error("评分过程异常，回退本场次待重评 [session={}]: {}", sessionId, ex.getMessage(), ex);
            examSessionGateway.releaseGradingToSubmitted(sessionId, gradingToken);
            throw ex;
        }
    }

    /**
     * 用结构化标准答案回填答题行（V2 阶段 2-A）。
     *
     * <p>题号缺失时按全局连续印刷号 / 落库位置序号对齐；{@code correct_answer} 为空时从 {@code structuredAnswers} 回填。trace
     * 字段无需在此清空：每题稍后都会在 gradeObjective / gradeSubjectiveWithAi 中被完整重写，若某题意外不产生 trace（如题型变更）则在主循环
     * update 后走 clearAiTrace 兜底清残留。
     */
    private void applyStructuredAnswers(
            List<ExamAnswer> answers, Map<Integer, String> structuredAnswers) {
        for (ExamAnswer answer : answers) {
            if (answer.getQuestionNumber() == null && answer.getQuestionIndex() != null) {
                // V2 题目号 == 全局连续印刷号 == 落库位置序号，缺失时按位置对齐
                answer.setQuestionNumber(answer.getQuestionIndex());
            }
            if (answer.getCorrectAnswer() == null || answer.getCorrectAnswer().isBlank()) {
                String correctAnswer =
                        answer.getQuestionNumber() != null
                                ? structuredAnswers.get(answer.getQuestionNumber())
                                : null;
                if (correctAnswer != null && !correctAnswer.isBlank()) {
                    answer.setCorrectAnswer(correctAnswer);
                }
            }
        }
    }

    /**
     * 卷面总分：取试卷结构化题目满分合计（与答题情况无关），避免未答题导致的分母偏小、得分率虚高。
     *
     * <p>结构化行缺失或合计 ≤ 0 时回退到已入库答案行的满分合计。
     */
    private int resolvePaperTotalScore(ExamSession session, List<ExamAnswer> answers) {
        int paperTotal =
                examQuestionGateway
                        .listBySessionKey(structuredQuestionSupport.resolvePaperSessionKey(session))
                        .stream()
                        .mapToInt(q -> q.getMaxScore() == null ? 0 : q.getMaxScore())
                        .sum();
        if (paperTotal <= 0) {
            paperTotal =
                    answers.stream()
                            .mapToInt(a -> a.getMaxScore() == null ? 0 : a.getMaxScore())
                            .sum();
        }
        return paperTotal;
    }

    /**
     * 读取结构化题目行的标准答案映射（印刷题号 → 标准答案）。
     *
     * <p>缺失时对老卷 / 即时卷惰性回灌一次（用本场次自带的试卷 + 答案键 + 方案重建）； 回灌仍拿不到则返回空表，调用方保持 {@code correct_answer}
     * 为空（评分走 0-E「缺少标准答案， 待人工确认」），不再回退到自由文本答案键解析。
     */
    private Map<Integer, String> resolveStructuredAnswerMap(ExamSession session) {
        Map<Integer, String> map = new HashMap<>();
        String paperKey = structuredQuestionSupport.resolvePaperSessionKey(session);
        if (paperKey == null || paperKey.isBlank()) {
            return map;
        }
        List<ExamQuestion> questions = examQuestionGateway.listBySessionKey(paperKey);
        if (questions.isEmpty()) {
            try {
                ExamPlan plan = ExamPaperParser.readPlan(session.getExamPlan());
                examQuestionSplitSupport.splitAndPersist(
                        paperKey, session.getExamPaper(), session.getAnswerKey(), plan);
                questions = examQuestionGateway.listBySessionKey(paperKey);
                log.info(
                        "惰性回灌结构化题目 [session={}, paperKey={}, rows={}]",
                        session.getId(),
                        paperKey,
                        questions.size());
            } catch (Exception e) {
                log.warn(
                        "惰性回灌结构化题目失败，本次评分按缺失标准答案处理（待人工确认）[session={}, paperKey={}]: {}",
                        session.getId(),
                        paperKey,
                        e.getMessage());
                return map;
            }
        }
        for (ExamQuestion q : questions) {
            if (q.getQuestionNumber() != null
                    && q.getCorrectAnswer() != null
                    && !q.getCorrectAnswer().isBlank()) {
                map.put(q.getQuestionNumber(), q.getCorrectAnswer());
            }
        }
        return map;
    }

    /**
     * 异步评分：在虚拟线程中执行，通过回调上报每题输入 / 原始输出 / 得分。
     *
     * @param sessionId 考试场次 ID
     * @param callback 进度回调，可为 null
     */
    public void gradeExamAsync(Long sessionId, ExamGradingProgressCallback callback) {
        try {
            agentExecutor.submit(
                    () -> {
                        try {
                            gradeExamInternal(sessionId, callback);
                        } catch (Exception e) {
                            // 认领后的异常已在 gradeExamInternal 内以围栏令牌安全回退并记日志，这里只负责把错误上报回调。
                            log.error("异步评分异常 [session={}]", sessionId, e);
                            if (callback != null) {
                                try {
                                    callback.onError(
                                            e.getMessage() != null
                                                    ? e.getMessage()
                                                    : e.getClass().getSimpleName());
                                } catch (Exception ignored) {
                                    // 回调内部异常吞掉
                                }
                            }
                        }
                    });
        } catch (RejectedExecutionException rex) {
            // 线程池已达并发上限：AbortPolicy 在提交瞬间同步抛出，任务体尚未执行、
            // 未认领任何场次，无需回退围栏令牌。上报繁忙提示后向上冒泡由控制器转 429。
            log.warn("评分任务被拒绝（并发已达上限）[session={}]", sessionId);
            if (callback != null) {
                try {
                    callback.onError("系统繁忙，评分任务已达并发上限，请稍后重试");
                } catch (Exception ignored) {
                    // 回调内部异常吞掉
                }
            }
            throw rex;
        }
    }

    /**
     * 异步触发 AI 评分：立即返回，评分过程通过回调上报每题输入 / 原始输出。
     *
     * @param sessionId 考试场次 ID
     * @param callback 进度回调
     */
    public void triggerGradingAsync(Long sessionId, ExamGradingProgressCallback callback) {
        try {

            ExamSession session =
                    examSessionGateway
                            .findById(sessionId)
                            .orElseThrow(
                                    () -> new IllegalArgumentException("考试场次不存在: " + sessionId));
            if (!STATUS_SUBMITTED.equals(session.getStatus())) {
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
     * <p>包级可见以便 golden-file 回归基线测试直接调用（0-A），不改变任何评分逻辑。
     *
     * @param answer 待评分的答题记录
     * @param callback 进度回调（可为 null）
     */
    void gradeObjective(ExamAnswer answer, ExamGradingProgressCallback callback) {
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
            log.warn(
                    "grading: sessionId answer_key missing for questionIndex={}, forcing 0 + review",
                    qIdx);
            answer.setCorrect(false);
            answer.setAiScore(0);
            answer.setAiFeedback("缺少标准答案，待人工确认");
            answer.setAiRawOutput("客观题自动比对：缺少参考答案 → 判 0 分（待复核）");
            if (examAlertGateway != null) {
                examAlertGateway.answerKeyMissing(
                        answer.getSessionId(), answer.getQuestionNumber());
            }
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
            Set<Character> correctSet = parseChoiceSet(normalizedCorrect);
            Set<Character> studentSet = parseChoiceSet(normalizedStudent);
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
            boolean match =
                    normalizedCorrect != null && normalizedCorrect.equals(normalizedStudent);
            score = match ? maxScore : 0;
            isCorrect = match;
            feedbackDetail = match ? "回答正确" : "回答错误，正确答案：" + rawCorrect;
        }

        answer.setCorrect(isCorrect);
        answer.setAiScore(score);
        answer.setAiFeedback(feedbackDetail);
        answer.setAiRawOutput(
                "客观题自动比对：期望["
                        + rawCorrect
                        + "] 实际["
                        + (rawStudent == null ? "" : rawStudent)
                        + "] → "
                        + (isCorrect ? "匹配" : "不匹配")
                        + "（得分"
                        + score
                        + "/"
                        + maxScore
                        + "）");
    }

    /** 组装客观题的 trace 输入（题目 / 期望 / 实际） */
    private String buildObjectiveInput(ExamAnswer answer) {
        StringBuilder sb = new StringBuilder();
        sb.append("[客观题自动比对 · 无需 LLM]\n");
        sb.append("题型：").append(answer.getQuestionType()).append("\n");
        sb.append("题目（第")
                .append(answer.getQuestionIndex())
                .append("题）：\n")
                .append(answer.getQuestionContent())
                .append("\n\n");
        sb.append("满分：").append(answer.getMaxScore()).append("分\n");
        sb.append("参考答案：")
                .append(answer.getCorrectAnswer() == null ? "-" : answer.getCorrectAnswer())
                .append("\n");
        sb.append("学生答案：")
                .append(answer.getStudentAnswer() == null ? "" : answer.getStudentAnswer())
                .append("\n");
        return sb.toString();
    }

    /** 主观题 AI 评分 */
    private void gradeSubjectiveWithAi(
            ExamAnswer answer, ExamSession session, ExamGradingProgressCallback callback) {
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
            String output =
                    streamingChatGateway.streamCompletion(
                            AI_GRADING_SYSTEM_PROMPT,
                            userPrompt,
                            (kind, delta) -> {
                                if (callback != null) {
                                    callback.onQuestionToken(qIdx, kind, delta);
                                }
                            });

            if (output == null || output.isBlank()) {
                log.warn("AI 评分返回空 [question={}]", answer.getQuestionIndex());
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

            log.debug(
                    "AI 评分完成 [question={}, score={}/{}]",
                    answer.getQuestionIndex(),
                    score,
                    answer.getMaxScore());

        } catch (Exception e) {
            log.error("AI 评分异常 [question={}]", answer.getQuestionIndex(), e);
            answer.setAiScore(0);
            answer.setAiFeedback("AI 评分异常：" + e.getMessage());
            answer.setAiRawOutput(
                    "[ERROR] "
                            + (e.getMessage() != null ? e.getMessage() : e.getClass().getName()));
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
     * 按题型规范化答案字符串，用于客观题等值比较（统一委托 {@link ExamAnswerNormalizer}）。
     *
     * <ul>
     *   <li>单选 / 多选：先剥离内联解释（{@code answerHead}），再取答案头部的 A-D 字母去重排序 （使 "A,C"=="AC"=="C,A"，且 {@code
     *       "B【解析】C…"} 只保留 "B"）
     *   <li>判断题：符号字典折叠 → "TRUE" / "FALSE"（正确=√=T=对=是=Y → TRUE），无法识别时回退原串
     *   <li>其他：trim + 全半角 + 去空白 + 大写
     * </ul>
     *
     * @param raw 原始答案字符串
     * @param questionType 题型 key
     * @return 规范化后的比较用字符串（null 入参返回 null）
     */
    String normalizeForCompare(String raw, String questionType) {
        if (raw == null) {
            return null;
        }
        if ("TRUE_FALSE".equals(questionType)) {
            String token = ExamAnswerNormalizer.trueFalseToken(raw);
            return token != null
                    ? token
                    : toHalfWidth(raw).trim().replaceAll("\\s+", "").toUpperCase();
        }
        if ("SINGLE_CHOICE".equals(questionType) || "MULTI_CHOICE".equals(questionType)) {
            String head = ExamAnswerNormalizer.answerHead(raw);
            return normalizeChoiceSet(toHalfWidth(head).toUpperCase());
        }
        return toHalfWidth(raw).trim().replaceAll("\\s+", "").toUpperCase();
    }

    /**
     * 多选规范化：仅保留大写字母 A-Z，去重排序拼接。
     *
     * <p>上界须与 {@link #parseChoiceSet(String)} 一致（A-Z）。早前硬截断到 A-D 会把 E 及以后的 合法选项字母剥掉，导致「期望 E /
     * 学生未答（空）」双方都归一为空串而误判相等给满分。 解释文字中的字母不在此处过滤，由 {@code ExamAnswerNormalizer.answerHead}
     * 依【解析】等标记截断兜底。
     */
    private String normalizeChoiceSet(String s) {
        return s.chars()
                .filter(c -> c >= 'A' && c <= 'Z')
                .distinct()
                .sorted()
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    /** 解析多选字母集合：从已规范化的纯字母串拆为 Set。 */
    private Set<Character> parseChoiceSet(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return Collections.emptySet();
        }
        Set<Character> set = new HashSet<>();
        for (char c : normalized.toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                set.add(c);
            }
        }
        return set;
    }

    /** 全角字符 → 半角（仅处理常见全角 ASCII 范围 0xFF01-0xFF5E，以及全角空格 0x3000）。 */
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

    public ExamSessionGateway examSessionGateway() {
        return examSessionGateway;
    }

    public ExamAnswerGateway examAnswerGateway() {
        return examAnswerGateway;
    }
}
