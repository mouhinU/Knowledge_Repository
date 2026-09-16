package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.application.util.AnswerKeyParser;
import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.domain.repository.ExamAnswerRepository;
import com.mouhin.knowledge.repository.domain.repository.ExamSessionRepository;
import com.mouhin.knowledge.repository.domain.repository.StudentRepository;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 错题本应用服务
 * <p>
 * 基于已评分的考试答题记录，筛选出答错或部分得分的题目，
 * 支持按考生、主题、题型等维度过滤，并可调用 AI 生成针对性错题总结。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@Service
public class WrongAnswerApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(WrongAnswerApplicationService.class);

    /**
     * 已评分的状态列表（AI_GRADED / REVIEWED / PUBLISHED）
     */
    private static final List<String> GRADED_STATUSES = List.of("AI_GRADED", "REVIEWED", "PUBLISHED");

    private static final String AI_SUMMARY_SYSTEM_PROMPT = """
            你是一位专业的教育分析师。请根据学生的错题信息进行分析总结，帮助教师了解学生的薄弱环节。
            
            请从以下维度进行分析：
            1. 错误原因分析（知识性错误、理解偏差、粗心等）
            2. 知识点薄弱领域
            3. 按题型分析表现差异
            4. 针对性的改进建议和学习方向
            
            请用中文输出，结构清晰，重点突出。
            """;

    private final ExamAnswerRepository examAnswerRepository;
    private final ExamSessionRepository examSessionRepository;
    private final StudentRepository studentRepository;
    private final ChatModel chatModel;

    public WrongAnswerApplicationService(ExamAnswerRepository examAnswerRepository,
                                         ExamSessionRepository examSessionRepository,
                                         StudentRepository studentRepository,
                                         ChatModel chatModel) {
        this.examAnswerRepository = examAnswerRepository;
        this.examSessionRepository = examSessionRepository;
        this.studentRepository = studentRepository;
        this.chatModel = chatModel;
    }

    /**
     * 查询错题列表（管理端，支持按考生 / 主题 / 题型过滤）
     *
     * @param studentId    考生 ID（可选，为空查全部）
     * @param topic        主题关键词（可选，模糊匹配）
     * @param questionType 题型（可选）
     * @return 错题列表（每条包含场次信息）
     */
    public List<Map<String, Object>> listWrongAnswers(Long studentId, String topic, String questionType) {
        // 1. 查询已评分的场次
        List<ExamSession> sessions = findGradedSessions(studentId, topic);
        if (sessions.isEmpty()) {
            return List.of();
        }

        // 2. 查询这些场次的所有答题记录
        List<Long> sessionIds = sessions.stream().map(ExamSession::getId).toList();
        List<ExamAnswer> allAnswers = examAnswerRepository.listBySessionIds(sessionIds);

        // 3. 筛选错题（得分 < 满分）
        List<ExamAnswer> wrongAnswers = allAnswers.stream()
                .filter(a -> a.getEffectiveScore() < a.getMaxScore())
                .toList();

        // 4. 按题型过滤
        if (questionType != null && !questionType.isBlank()) {
            wrongAnswers = wrongAnswers.stream()
                    .filter(a -> questionType.equals(a.getQuestionType()))
                    .toList();
        }

        // 5. 构建场次映射
        Map<Long, ExamSession> sessionMap = sessions.stream()
                .collect(Collectors.toMap(ExamSession::getId, s -> s));

        // 5.1 预解析各场次的「标准答案与评分标准」，缓存到 sessionId → (题号 → QuestionKey)
        Map<Long, Map<Integer, AnswerKeyParser.QuestionKey>> answerKeyBySession = new HashMap<>();
        for (ExamSession session : sessions) {
            answerKeyBySession.put(session.getId(),
                    AnswerKeyParser.parse(session.getAnswerKey()));
        }

        // 6. 解析考生名称
        Map<Long, String> studentNames = resolveStudentNames(
                sessions.stream().map(ExamSession::getStudentId).distinct().toList());

        // 7. 按提交时间倒序组装结果
        List<Map<String, Object>> result = new ArrayList<>(wrongAnswers.size());
        for (ExamAnswer answer : wrongAnswers) {
            ExamSession session = sessionMap.get(answer.getSessionId());
            if (session == null) {
                continue;
            }
            Map<Integer, AnswerKeyParser.QuestionKey> keyMap =
                    answerKeyBySession.getOrDefault(answer.getSessionId(), Map.of());
            AnswerKeyParser.QuestionKey key = keyMap.get(answer.getQuestionIndex());
            result.add(buildWrongAnswerMap(answer, session, studentNames, key));
        }

        result.sort(Comparator.comparing(
                (Map<String, Object> m) -> m.get("submitTime") != null
                        ? m.get("submitTime").toString() : "",
                Comparator.reverseOrder()));

        logger.debug("查询错题列表 [studentId={}, topic={}, type={}, count={}]",
                studentId, topic, questionType, result.size());

        return result;
    }

    /**
     * 分页查询错题列表（管理端）
     *
     * @param studentId    考生 ID（可选）
     * @param topic        主题关键词（可选）
     * @param questionType 题型（可选）
     * @param page         页码（从 0 开始）
     * @param size         每页数量
     * @return 分页结果（records / total / page / size）
     */
    public Map<String, Object> pageWrongAnswers(Long studentId, String topic, String questionType,
                                                int page, int size) {
        List<Map<String, Object>> all = listWrongAnswers(studentId, topic, questionType);
        int safePage = Math.max(page, 0);
        int safeSize = size > 0 ? size : 10;
        int from = Math.min(safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        List<Map<String, Object>> records = new ArrayList<>(all.subList(from, to));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", all.size());
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("stats", buildStats(all));
        return result;
    }

    /**
     * 基于全量错题构建统计概览（错题总数 / 涉及主题 / 涉及考生 / 最多错题题型 / 平均得分率）
     */
    private Map<String, Object> buildStats(List<Map<String, Object>> all) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalCount", all.size());
        if (all.isEmpty()) {
            stats.put("topicCount", 0);
            stats.put("studentCount", 0);
            stats.put("topType", null);
            stats.put("avgScoreRate", 0.0);
            return stats;
        }

        long topicCount = all.stream()
                .map(m -> m.get("topic"))
                .filter(java.util.Objects::nonNull)
                .distinct().count();
        long studentCount = all.stream()
                .map(m -> m.get("studentId"))
                .filter(java.util.Objects::nonNull)
                .distinct().count();

        Map<String, Long> typeCounts = new HashMap<>();
        double rateSum = 0;
        int rateCount = 0;
        for (Map<String, Object> m : all) {
            String type = m.get("questionType") != null ? m.get("questionType").toString() : "UNKNOWN";
            typeCounts.merge(type, 1L, Long::sum);
            int maxScore = toInt(m.get("maxScore"), 0);
            int effScore = toInt(m.get("effectiveScore"), 0);
            if (maxScore > 0) {
                rateSum += (double) effScore / maxScore;
                rateCount++;
            }
        }
        String topType = typeCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        double avgScoreRate = rateCount > 0 ? rateSum / rateCount : 0;

        stats.put("topicCount", topicCount);
        stats.put("studentCount", studentCount);
        stats.put("topType", topType);
        stats.put("avgScoreRate", Math.round(avgScoreRate * 10000.0) / 10000.0);
        return stats;
    }

    private int toInt(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * AI 错题总结（根据筛选后的错题生成分析报告）
     *
     * @param studentId    考生 ID（可选）
     * @param topic        主题（可选）
     * @param questionType 题型（可选）
     * @return AI 生成的分析文本
     */
    public String generateAiSummary(Long studentId, String topic, String questionType) {
        List<Map<String, Object>> wrongAnswers = listWrongAnswers(studentId, topic, questionType);
        if (wrongAnswers.isEmpty()) {
            return "暂无错题数据，无法生成总结。";
        }

        // 构建 AI prompt
        StringBuilder prompt = new StringBuilder();
        prompt.append("以下是学生的错题信息（共 ").append(wrongAnswers.size()).append(" 题）：\n\n");

        int limit = Math.min(wrongAnswers.size(), 50);
        for (int i = 0; i < limit; i++) {
            Map<String, Object> item = wrongAnswers.get(i);
            prompt.append("--- 第 ").append(i + 1).append(" 题 ---\n");
            prompt.append("考试主题：").append(item.get("topic")).append("\n");
            prompt.append("题型：").append(item.get("questionType")).append("\n");
            prompt.append("题目：").append(truncate(item.get("questionContent"), 200)).append("\n");
            prompt.append("满分：").append(item.get("maxScore")).append("\n");
            prompt.append("得分：").append(item.get("effectiveScore")).append("\n");
            prompt.append("学生答案：").append(truncate(item.get("studentAnswer"), 200)).append("\n");
            prompt.append("正确答案：").append(truncate(item.get("correctAnswer"), 200)).append("\n");
            prompt.append("AI反馈：").append(truncate(item.get("aiFeedback"), 150)).append("\n\n");
        }

        if (wrongAnswers.size() > 50) {
            prompt.append("（还有 ").append(wrongAnswers.size() - 50).append(" 题省略）\n\n");
        }

        // 统计信息
        Map<String, Long> typeStats = wrongAnswers.stream()
                .collect(Collectors.groupingBy(
                        m -> m.get("questionType") != null ? m.get("questionType").toString() : "UNKNOWN",
                        Collectors.counting()));
        prompt.append("题型分布统计：\n");
        typeStats.forEach((type, count) -> prompt.append("- ").append(type).append("：")
                .append(count).append("题\n"));

        prompt.append("\n请对这些错题进行全面分析总结。");

        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(
                            SystemMessage.from(AI_SUMMARY_SYSTEM_PROMPT),
                            UserMessage.from(prompt.toString())
                    )
                    .build();

            ChatResponse response = chatModel.chat(request);
            String output = response.aiMessage().text();

            if (output == null || output.isBlank()) {
                return "AI 返回为空，请稍后重试。";
            }

            return output;
        } catch (Exception e) {
            logger.error("AI 错题总结失败", e);
            return "AI 总结生成失败：" + e.getMessage();
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 查询已评分的场次（支持按考生和主题过滤）
     */
    private List<ExamSession> findGradedSessions(Long studentId, String topic) {
        List<ExamSession> sessions;
        if (studentId != null) {
            sessions = examSessionRepository.listByStudentIdAndStatuses(studentId, GRADED_STATUSES);
        } else {
            sessions = examSessionRepository.listByStatuses(GRADED_STATUSES);
        }

        // 按主题关键词过滤（应用层）
        if (topic != null && !topic.isBlank()) {
            String lowerTopic = topic.toLowerCase();
            sessions = sessions.stream()
                    .filter(s -> s.getTopic() != null && s.getTopic().toLowerCase().contains(lowerTopic))
                    .toList();
        }

        return sessions;
    }

    /**
     * 批量解析考生名称
     */
    private Map<Long, String> resolveStudentNames(List<Long> studentIds) {
        Map<Long, String> names = new HashMap<>(studentIds.size());
        for (Long id : studentIds) {
            studentRepository.findById(id)
                    .ifPresent(s -> names.put(id,
                            s.getDisplayName() != null ? s.getDisplayName() : s.getUsername()));
        }
        return names;
    }

    /**
     * 组装单条错题的返回数据
     */
    private Map<String, Object> buildWrongAnswerMap(ExamAnswer answer, ExamSession session,
                                                    Map<Long, String> studentNames,
                                                    AnswerKeyParser.QuestionKey key) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("answerId", answer.getId());
        map.put("sessionId", answer.getSessionId());
        map.put("sessionKey", session.getSessionKey());
        map.put("topic", session.getTopic());
        map.put("difficulty", session.getDifficulty());
        map.put("studentId", session.getStudentId());
        map.put("studentName", studentNames.getOrDefault(session.getStudentId(), "未知考生"));
        map.put("questionIndex", answer.getQuestionIndex());
        map.put("questionType", answer.getQuestionType());
        map.put("questionContent", answer.getQuestionContent());
        map.put("optionsJson", answer.getOptionsJson());
        map.put("maxScore", answer.getMaxScore());
        map.put("effectiveScore", answer.getEffectiveScore());
        String correctAnswer = answer.getCorrectAnswer();
        if ((correctAnswer == null || correctAnswer.isBlank()) && key != null && key.answer() != null) {
            correctAnswer = key.answer();
        }
        map.put("correctAnswer", correctAnswer);
        map.put("studentAnswer", answer.getStudentAnswer());
        map.put("aiFeedback", answer.getAiFeedback());
        map.put("analysis", key != null ? key.analysis() : null);
        map.put("scoringCriteria", key != null ? key.scoringCriteria() : null);
        map.put("correct", answer.getCorrect());
        map.put("submitTime", session.getSubmitTime() != null ? session.getSubmitTime().toString() : null);
        return map;
    }

    private String truncate(Object value, int maxLen) {
        if (value == null) {
            return "（无）";
        }
        String str = value.toString();
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }
}
