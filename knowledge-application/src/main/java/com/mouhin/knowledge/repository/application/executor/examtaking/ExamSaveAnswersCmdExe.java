package com.mouhin.knowledge.repository.application.executor.examtaking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mouhin.knowledge.repository.domain.gateway.ExamAnswerGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 保存 / 更新答题命令执行器（app 层用例，事务边界，支持断点续答）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class ExamSaveAnswersCmdExe {

    private static final Logger logger = LoggerFactory.getLogger(ExamSaveAnswersCmdExe.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    private final ExamAnswerGateway examAnswerGateway;
    private final ExamSessionGateway examSessionGateway;
    private final ExamTakingSupport support;

    public ExamSaveAnswersCmdExe(
            ExamAnswerGateway examAnswerGateway,
            ExamSessionGateway examSessionGateway,
            ExamTakingSupport support) {
        this.examAnswerGateway = examAnswerGateway;
        this.examSessionGateway = examSessionGateway;
        this.support = support;
    }

    @Transactional
    public void execute(String sessionKey, String studentToken, List<Map<String, String>> answers) {
        ExamSession session = support.resolveSession(sessionKey, studentToken);

        if (!STATUS_IN_PROGRESS.equals(session.getStatus())) {
            throw new IllegalStateException("考试已结束，无法修改答案");
        }

        // 清除旧答案，重新保存
        examAnswerGateway.deleteBySessionId(session.getId());

        List<ExamAnswer> answerEntities;
        List<ExamQuestion> paperQuestions = support.listPaperQuestions(session);
        if (!paperQuestions.isEmpty()) {
            // V2 出卷即切分 · 阶段 2-A/2-C：以结构化题目行为权威源，逐题落行（含未答），
            // 按印刷题号精确匹配学生作答，避免"未答题无行 → 分母虚高 / 错题本漏收"。
            answerEntities = buildFromStructured(session, paperQuestions, answers);
        } else {
            // 回退：结构化行缺失（异常老卷），沿用投影 questionsJson 匹配逻辑
            answerEntities = buildFromProjection(session, answers);
        }

        examAnswerGateway.saveAll(answerEntities);
        session.setUpdateTime(LocalDateTime.now());
        examSessionGateway.update(session);

        logger.debug("保存答题 [session={}, rows={}]", sessionKey, answerEntities.size());
    }

    /** 以结构化题目行为权威源构建答题行：每道题必落一行，未答则 studentAnswer 置空。 */
    private List<ExamAnswer> buildFromStructured(
            ExamSession session,
            List<ExamQuestion> paperQuestions,
            List<Map<String, String>> answers) {
        // 按印刷题号与位置序号双索引入账，兼容前端任一编号口径
        Map<Integer, Map<String, String>> byNumber = new java.util.HashMap<>();
        for (Map<String, String> ans : answers) {
            Integer num = parseIntOrNull(ans.get("questionNumber"));
            Integer idx = parseIntOrNull(ans.get("questionIndex"));
            if (num != null) {
                byNumber.put(num, ans);
            }
            if (idx != null && !byNumber.containsKey(idx)) {
                byNumber.putIfAbsent(idx, ans);
            }
        }

        List<ExamAnswer> entities = new ArrayList<>();
        int position = 0;
        for (ExamQuestion q : paperQuestions) {
            position++;
            Integer qNumber = q.getQuestionNumber();
            Map<String, String> matched =
                    qNumber != null ? byNumber.get(qNumber) : byNumber.get(position);
            if (matched == null) {
                matched = byNumber.get(position);
            }

            ExamAnswer answer = new ExamAnswer();
            answer.setSessionId(session.getId());
            answer.setQuestionIndex(position);
            answer.setQuestionNumber(qNumber != null ? qNumber : position);
            answer.setQuestionType(q.getQuestionType());
            answer.setQuestionContent(q.getStem());
            answer.setMaxScore(q.getMaxScore());
            answer.setOptionsJson(q.getOptionsJson());
            answer.setStudentAnswer(matched != null ? matched.get("answer") : null);
            answer.setCreateTime(LocalDateTime.now());
            answer.setUpdateTime(LocalDateTime.now());
            entities.add(answer);
        }
        return entities;
    }

    /** 回退路径：结构化行缺失时按投影 questionsJson 匹配（保持改造前行为）。 */
    private List<ExamAnswer> buildFromProjection(
            ExamSession session, List<Map<String, String>> answers) {
        List<Map<String, Object>> questions = support.parseQuestions(session.getQuestionsJson());
        List<ExamAnswer> entities = new ArrayList<>();
        for (Map<String, String> ans : answers) {
            ExamAnswer answer = new ExamAnswer();
            answer.setSessionId(session.getId());

            int qIndex = Integer.parseInt(ans.get("questionIndex"));
            answer.setQuestionIndex(qIndex);
            answer.setQuestionNumber(parseIntOrDefault(ans.get("questionNumber"), qIndex));

            Map<String, Object> questionMeta = support.findQuestion(questions, qIndex);
            if (questionMeta != null) {
                answer.setQuestionType((String) questionMeta.get("type"));
                answer.setQuestionContent((String) questionMeta.get("content"));
                answer.setMaxScore((Integer) questionMeta.get("maxScore"));
                if (questionMeta.get("options") != null) {
                    try {
                        answer.setOptionsJson(
                                OBJECT_MAPPER.writeValueAsString(questionMeta.get("options")));
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
            entities.add(answer);
        }
        return entities;
    }

    private Integer parseIntOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int parseIntOrDefault(String raw, int fallback) {
        Integer parsed = parseIntOrNull(raw);
        return parsed != null ? parsed : fallback;
    }
}
