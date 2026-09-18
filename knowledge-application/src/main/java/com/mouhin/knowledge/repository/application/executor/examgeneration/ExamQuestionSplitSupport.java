package com.mouhin.knowledge.repository.application.executor.examgeneration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mouhin.knowledge.repository.application.util.AnswerKeyParser;
import com.mouhin.knowledge.repository.application.util.ExamPaperParser;
import com.mouhin.knowledge.repository.domain.gateway.ExamQuestionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.service.ExamContractValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 出卷期切分支撑（app 层，V2「出卷即切分」核心节点）
 * <p>
 * 把「试卷 Markdown + 标准答案 Markdown + 题型分布方案」一次性切分为结构化
 * {@link ExamQuestion} 列表：保留印刷题号、按题序绑定标准答案与解析、序列化选项、
 * 统计填空空数，并落库 {@code kb_exam_question}。下游评分 / 展示 / 错题本据此纯读结构化行，
 * 不再重复解析自由文本答案键。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-18
 */
@Component
public class ExamQuestionSplitSupport {

    private static final Logger logger = LoggerFactory.getLogger(ExamQuestionSplitSupport.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 填空题空位：连续 2 个及以上半角 / 全角下划线，或空括号
     */
    private static final Pattern BLANK_PATTERN = Pattern.compile("(_{2,}|＿{2,}|\\(\\s*\\)|（\\s*）)");

    private final ExamQuestionGateway examQuestionGateway;

    public ExamQuestionSplitSupport(ExamQuestionGateway examQuestionGateway) {
        this.examQuestionGateway = examQuestionGateway;
    }

    /**
     * 切分并绑定答案，返回结构化题目列表（不落库）。
     *
     * @param sessionKey  试卷标识（出卷会话 session_id）
     * @param examPaperMd 试卷 Markdown
     * @param answerKeyMd 标准答案与评分标准 Markdown
     * @param plan        题型分布方案（可为 null，缺省时解析器回退关键词判定）
     * @return 结构化题目列表，按印刷题号出现顺序排列
     */
    public List<ExamQuestion> splitAndBind(String sessionKey, String examPaperMd,
                                           String answerKeyMd, ExamPlan plan) {
        List<ExamQuestion> result = new ArrayList<>();
        List<Map<String, Object>> rawQuestions = ExamPaperParser.parse(examPaperMd, plan);
        if (rawQuestions.isEmpty()) {
            logger.warn("[Split] 试卷切分为空 [session={}]", sessionKey);
            return result;
        }
        Map<Integer, AnswerKeyParser.QuestionKey> keyMap = AnswerKeyParser.parse(answerKeyMd);

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        for (Map<String, Object> q : rawQuestions) {
            ExamQuestion question = new ExamQuestion();
            question.setSessionKey(sessionKey);
            question.setQuestionNumber(readInt(q.get("number")));
            question.setSectionLabel(readString(q.get("sectionLabel")));
            String type = readString(q.get("type"));
            question.setQuestionType(type);
            String stem = readString(q.get("content"));
            question.setStem(stem);
            question.setMaxScore(readInt(q.get("maxScore")));
            question.setOptionsJson(serializeOptions(q.get("options"), type));
            question.setBlankCount("FILL_BLANK".equals(type) ? countBlanks(stem) : 0);

            // 按题目位置序号（与答案键题序一致）绑定标准答案与解析
            Integer positionalIndex = readInt(q.get("index"));
            AnswerKeyParser.QuestionKey key = positionalIndex != null ? keyMap.get(positionalIndex) : null;
            if (key != null) {
                question.setCorrectAnswer(key.answer());
                question.setAnalysis(key.analysis());
            }

            question.setCreateTime(now);
            question.setUpdateTime(now);
            result.add(question);
        }
        logger.info("[Split] 切分完成 [session={}, questions={}, keyEntries={}]",
                sessionKey, result.size(), keyMap.size());
        return result;
    }

    /**
     * 切分并落库（幂等：先清理该试卷已有行再批量写入），随后执行出卷契约校验。
     *
     * @return 落库数量与契约校验结果
     */
    public SplitOutcome splitAndPersist(String sessionKey, String examPaperMd,
                                         String answerKeyMd, ExamPlan plan) {
        if (sessionKey == null || sessionKey.isBlank()) {
            logger.warn("[Split] sessionKey 为空，跳过落库");
            return new SplitOutcome(0,
                    new ExamContractValidator.Result(false, List.of("试卷标识为空，无法落库校验")));
        }
        List<ExamQuestion> questions = splitAndBind(sessionKey, examPaperMd, answerKeyMd, plan);
        if (questions.isEmpty()) {
            return new SplitOutcome(0,
                    new ExamContractValidator.Result(false, List.of("试卷切分结果为空")));
        }
        examQuestionGateway.deleteBySessionKey(sessionKey);
        examQuestionGateway.batchInsert(questions);
        ExamContractValidator.Result validation = ExamContractValidator.validate(questions, plan);
        return new SplitOutcome(questions.size(), validation);
    }

    // ==================== 工具 ====================

    private String serializeOptions(Object optionsObj, String type) {
        if (!"SINGLE_CHOICE".equals(type) && !"MULTI_CHOICE".equals(type) && !"TRUE_FALSE".equals(type)) {
            return null;
        }
        if (optionsObj == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(optionsObj);
        } catch (Exception e) {
            logger.warn("[Split] 选项序列化失败（忽略）: {}", e.getMessage());
            return null;
        }
    }

    private int countBlanks(String stem) {
        if (stem == null || stem.isBlank()) {
            return 1;
        }
        Matcher matcher = BLANK_PATTERN.matcher(stem);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count > 0 ? count : 1;
    }

    private Integer readInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str && !str.isBlank()) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String readString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * 切分落库结果。
     *
     * @param count      落库题目数量
     * @param validation 出卷契约校验结果
     */
    public record SplitOutcome(int count, ExamContractValidator.Result validation) {
    }
}
