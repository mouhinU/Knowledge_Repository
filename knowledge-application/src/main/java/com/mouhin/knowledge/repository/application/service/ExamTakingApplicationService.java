package com.mouhin.knowledge.repository.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mouhin.knowledge.repository.application.agent.ExamContentRenderAgent;
import com.mouhin.knowledge.repository.application.agent.ExamContentValidatorAgent;
import com.mouhin.knowledge.repository.application.agent.PaperValidationReport;
import com.mouhin.knowledge.repository.application.util.ExamPaperParser;
import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import com.mouhin.knowledge.repository.domain.repository.ExamAnswerRepository;
import com.mouhin.knowledge.repository.domain.repository.ExamHistoryRepository;
import com.mouhin.knowledge.repository.domain.repository.ExamSessionRepository;
import com.mouhin.knowledge.repository.domain.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 在线做题应用服务
 * <p>
 * 处理考生开始考试、保存答案、交卷等用例。
 * 交卷后自动触发客观题评分，并异步调用 AI 批改主观题。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@Service
public class ExamTakingApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(ExamTakingApplicationService.class);

    private final ExamSessionRepository examSessionRepository;
    private final ExamAnswerRepository examAnswerRepository;
    private final ExamHistoryRepository examHistoryRepository;
    private final StudentRepository studentRepository;
    private final ExamContentRenderAgent contentRenderAgent;
    private final ExamContentValidatorAgent contentValidatorAgent;

    public ExamTakingApplicationService(ExamSessionRepository examSessionRepository,
                                        ExamAnswerRepository examAnswerRepository,
                                        ExamHistoryRepository examHistoryRepository,
                                        StudentRepository studentRepository,
                                        ExamContentRenderAgent contentRenderAgent,
                                        ExamContentValidatorAgent contentValidatorAgent) {
        this.examSessionRepository = examSessionRepository;
        this.examAnswerRepository = examAnswerRepository;
        this.examHistoryRepository = examHistoryRepository;
        this.studentRepository = studentRepository;
        this.contentRenderAgent = contentRenderAgent;
        this.contentValidatorAgent = contentValidatorAgent;
    }

    /**
     * 从历史试卷开始考试
     *
     * @param studentToken     考生令牌
     * @param historySessionId 出卷历史会话 ID
     * @return 考试场次
     */
    @Transactional
    public ExamSession startFromHistory(String studentToken, String historySessionId) {
        Student student = resolveStudent(studentToken);

        ExamHistory history = examHistoryRepository.findBySessionId(historySessionId)
                .orElseThrow(() -> new IllegalArgumentException("试卷不存在: " + historySessionId));

        if (history.getExamPaper() == null || history.getExamPaper().isBlank()) {
            throw new IllegalArgumentException("试卷内容为空");
        }

        ExamSession session = new ExamSession();
        session.setSessionKey(UUID.randomUUID().toString());
        session.setStudentId(student.getId());
        session.setExamHistoryId(history.getId());
        session.setTopic(history.getTopic());
        session.setDifficulty(history.getDifficulty());
        session.setExamPaper(history.getExamPaper());
        session.setAnswerKey(history.getAnswerKey());
        session.setExamPlan(history.getExamPlan());
        String rendered = contentRenderAgent.render(history.getExamPaper(), null, history.getExamPlan());
        validateOrThrow(rendered);
        session.setQuestionsJson(rendered);
        session.setTotalScore(sumMaxScore(rendered));
        session.setDurationMinutes(history.getDurationMinutes());
        session.setStatus("IN_PROGRESS");
        session.setStartTime(LocalDateTime.now());
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());

        examSessionRepository.save(session);
        logger.info("考生开始考试 [student={}, history={}, session={}]",
                student.getId(), historySessionId, session.getSessionKey());
        return session;
    }

    /**
     * 使用即时生成的试卷开始考试
     *
     * @param studentToken 考生令牌
     * @param examPaper    试卷 Markdown
     * @param answerKey    参考答案
     * @param topic        主题
     * @param difficulty   难度
     * @return 考试场次
     */
    @Transactional
    public ExamSession startWithPaper(String studentToken, String examPaper, String answerKey,
                                      String topic, String difficulty) {
        Student student = resolveStudent(studentToken);

        ExamSession session = new ExamSession();
        session.setSessionKey(UUID.randomUUID().toString());
        session.setStudentId(student.getId());
        session.setTopic(topic != null ? topic : "在线考试");
        session.setDifficulty(difficulty);
        session.setExamPaper(examPaper);
        session.setAnswerKey(answerKey);
        String rendered = contentRenderAgent.render(examPaper, null);
        validateOrThrow(rendered);
        session.setQuestionsJson(rendered);
        session.setTotalScore(sumMaxScore(rendered));
        session.setDurationMinutes(ExamPaperParser.parseDuration(examPaper));
        session.setStatus("IN_PROGRESS");
        session.setStartTime(LocalDateTime.now());
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());

        examSessionRepository.save(session);
        logger.info("考生开始考试（即时试卷）[student={}, session={}]",
                student.getId(), session.getSessionKey());
        return session;
    }

    /**
     * 校验已渲染的题目 JSON，返回校验报告（供前端展示非阻断性提示）
     *
     * @param questionsJson 渲染后的题目 JSON
     * @return 校验报告
     */
    public PaperValidationReport validateReport(String questionsJson) {
        return contentValidatorAgent.validate(questionsJson);
    }

    /**
     * 渲染后立即校验，存在阻断性错误时抛出异常阻止开考
     */
    private void validateOrThrow(String questionsJson) {
        PaperValidationReport report = contentValidatorAgent.validate(questionsJson);
        if (!report.pass()) {
            String detail = String.join("；", report.errors());
            logger.warn("试卷内容校验未通过，阻止开考：{}", detail);
            throw new IllegalArgumentException("试卷内容校验未通过：" + detail);
        }
    }

    /**
     * 依据渲染后的题目列表累加各题 maxScore，得到与试卷/方案一致的卷面总分；解析失败或为 0 回退 100。
     */
    private int sumMaxScore(String questionsJson) {
        try {
            List<Map<String, Object>> qs = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(questionsJson,
                            new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
                            });
            int sum = 0;
            for (Map<String, Object> q : qs) {
                Object ms = q.get("maxScore");
                if (ms instanceof Number num) {
                    sum += num.intValue();
                }
            }
            return sum > 0 ? sum : 100;
        } catch (Exception e) {
            logger.warn("累加题目总分失败，回退为 100：{}", e.getMessage());
            return 100;
        }
    }

    /**
     * 保存 / 更新答题（支持断点续答）
     *
     * @param sessionKey   场次标识
     * @param studentToken 考生令牌
     * @param answers      答题列表（questionIndex → studentAnswer）
     */
    @Transactional
    public void saveAnswers(String sessionKey, String studentToken,
                            List<Map<String, String>> answers) {
        ExamSession session = resolveSession(sessionKey, studentToken);

        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new IllegalStateException("考试已结束，无法修改答案");
        }

        // 清除旧答案，重新保存
        examAnswerRepository.deleteBySessionId(session.getId());

        // 解析题目 JSON 获取题目信息
        List<Map<String, Object>> questions = parseQuestions(session.getQuestionsJson());

        List<ExamAnswer> answerEntities = new ArrayList<>();
        for (Map<String, String> ans : answers) {
            ExamAnswer answer = new ExamAnswer();
            answer.setSessionId(session.getId());

            int qIndex = Integer.parseInt(ans.get("questionIndex"));
            answer.setQuestionIndex(qIndex);

            // 从题目列表中匹配题目信息
            Map<String, Object> questionMeta = findQuestion(questions, qIndex);
            if (questionMeta != null) {
                answer.setQuestionType((String) questionMeta.get("type"));
                answer.setQuestionContent((String) questionMeta.get("content"));
                answer.setMaxScore((Integer) questionMeta.get("maxScore"));
                if (questionMeta.get("options") != null) {
                    try {
                        answer.setOptionsJson(
                                new com.fasterxml.jackson.databind.ObjectMapper()
                                        .writeValueAsString(questionMeta.get("options")));
                    } catch (Exception ignored) {
                        // 序列化失败忽略
                    }
                }
            } else {
                answer.setQuestionType(ans.getOrDefault("questionType", "SHORT_ANSWER"));
                answer.setQuestionContent(ans.getOrDefault("content", ""));
                answer.setMaxScore(0);
            }

            answer.setStudentAnswer(ans.get("answer"));
            answer.setCreateTime(LocalDateTime.now());
            answer.setUpdateTime(LocalDateTime.now());
            answerEntities.add(answer);
        }

        examAnswerRepository.saveAll(answerEntities);
        session.setUpdateTime(LocalDateTime.now());
        examSessionRepository.update(session);

        logger.debug("保存答题 [session={}, count={}]", sessionKey, answers.size());
    }

    /**
     * 交卷
     * <p>
     * 标记考试为已提交，自动评分客观题，然后异步触发 AI 批改主观题。
     * </p>
     *
     * @param sessionKey   场次标识
     * @param studentToken 考生令牌
     */
    @Transactional
    public void submitExam(String sessionKey, String studentToken) {
        ExamSession session = resolveSession(sessionKey, studentToken);

        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new IllegalStateException("考试已提交，请勿重复操作");
        }

        // 服务端校验：检查是否超过考试时长（30 秒宽限期）
        if (session.getDurationMinutes() != null && session.getDurationMinutes() > 0
                && session.getStartTime() != null) {
            long elapsedSeconds = java.time.Duration.between(
                    session.getStartTime(), LocalDateTime.now()).getSeconds();
            long allowedSeconds = (long) session.getDurationMinutes() * 60 + 30;
            if (elapsedSeconds > allowedSeconds) {
                logger.warn("考试超时提交 [session={}, elapsed={}s, allowed={}s]",
                        sessionKey, elapsedSeconds, allowedSeconds);
                // 超时仍允许交卷（前端已自动触发），但记录日志
            }
        }

        session.submit();
        session.setUpdateTime(LocalDateTime.now());
        examSessionRepository.update(session);

        logger.info("考生交卷 [session={}, student={}]", sessionKey, session.getStudentId());
    }

    /**
     * 获取考试场次详情
     */
    public ExamSession getSession(String sessionKey, String studentToken) {
        return resolveSession(sessionKey, studentToken);
    }

    /**
     * 获取考试场次的所有答题记录
     */
    public List<ExamAnswer> getAnswers(String sessionKey, String studentToken) {
        ExamSession session = resolveSession(sessionKey, studentToken);
        return examAnswerRepository.listBySessionId(session.getId());
    }

    /**
     * 查询考生的考试历史
     */
    public List<ExamSession> listMySessions(String studentToken) {
        Student student = resolveStudent(studentToken);
        return examSessionRepository.listByStudentId(student.getId());
    }

    /**
     * 更新考试场次的 questionsJson（用于修复旧数据的选项解析问题）
     *
     * @param sessionKey    场次标识
     * @param studentToken  考生令牌
     * @param questionsJson 新的题目 JSON
     */
    @Transactional
    public void updateQuestionsJson(String sessionKey, String studentToken, String questionsJson) {
        ExamSession session = resolveSession(sessionKey, studentToken);
        session.setQuestionsJson(questionsJson);
        session.setUpdateTime(LocalDateTime.now());
        examSessionRepository.update(session);
        logger.info("已重新解析 questionsJson [session={}]", sessionKey);
    }

    /**
     * 更新考试时长（管理员覆盖）
     *
     * @param sessionKey      场次标识
     * @param durationMinutes 新的考试时长（分钟），null 表示不限时
     */
    @Transactional
    public void updateDuration(String sessionKey, Integer durationMinutes) {
        ExamSession session = examSessionRepository.findBySessionKey(sessionKey)
                .orElseThrow(() -> new IllegalArgumentException("考试场次不存在: " + sessionKey));
        session.setDurationMinutes(durationMinutes);
        session.setUpdateTime(LocalDateTime.now());
        examSessionRepository.update(session);
        logger.info("已更新考试时长 [session={}, duration={}]", sessionKey, durationMinutes);
    }

    // ==================== 内部方法 ====================

    private Student resolveStudent(String token) {
        return studentRepository.findBySessionToken(token)
                .filter(Student::isTokenValid)
                .orElseThrow(() -> new IllegalArgumentException("登录已过期，请重新登录"));
    }

    private ExamSession resolveSession(String sessionKey, String studentToken) {
        Student student = resolveStudent(studentToken);
        ExamSession session = examSessionRepository.findBySessionKey(sessionKey)
                .orElseThrow(() -> new IllegalArgumentException("考试场次不存在: " + sessionKey));
        if (!session.getStudentId().equals(student.getId())) {
            throw new IllegalArgumentException("无权访问此考试");
        }
        return session;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseQuestions(String questionsJson) {
        if (questionsJson == null || questionsJson.isBlank()) {
            return List.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(questionsJson, List.class);
        } catch (JsonProcessingException e) {
            logger.warn("解析题目 JSON 失败: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> findQuestion(List<Map<String, Object>> questions, int index) {
        return questions.stream()
                .filter(q -> Integer.valueOf(index).equals(q.get("index")))
                .findFirst()
                .orElse(null);
    }
}
