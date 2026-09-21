package com.mouhin.knowledge.repository.application.executor.examgeneration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mouhin.knowledge.repository.application.util.AnswerKeyParser;
import com.mouhin.knowledge.repository.application.util.ExamPaperParser;
import com.mouhin.knowledge.repository.domain.gateway.ExamQuestionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.service.ExamAnswerNormalizer;
import com.mouhin.knowledge.repository.domain.service.ExamBlankCounter;
import com.mouhin.knowledge.repository.domain.service.ExamContractValidator;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 出卷期切分支撑（app 层，V2「出卷即切分」核心节点）
 *
 * <p>把「试卷 Markdown + 标准答案 Markdown + 题型分布方案」一次性切分为结构化 {@link ExamQuestion}
 * 列表：保留印刷题号、按题序绑定标准答案与解析、序列化选项、 统计填空空数，并落库 {@code kb_exam_question}。下游评分 / 展示 / 错题本据此纯读结构化行，
 * 不再重复解析自由文本答案键。
 *
 * @author Knowledge-Repository
 * @date 2026-09-18
 */
@Component
@Slf4j
public class ExamQuestionSplitSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 客观题题型：答案存在「结论 + 内联解释」整串风险，切分时需拆出纯净答案头部 */
    private static final Set<String> OBJECTIVE_TYPES =
            Set.of("SINGLE_CHOICE", "MULTI_CHOICE", "TRUE_FALSE");

    private final ExamQuestionGateway examQuestionGateway;

    public ExamQuestionSplitSupport(ExamQuestionGateway examQuestionGateway) {
        this.examQuestionGateway = examQuestionGateway;
    }

    /**
     * 切分并绑定答案，返回结构化题目列表（不落库）。
     *
     * @param sessionKey 试卷标识（出卷会话 session_id）
     * @param examPaperMd 试卷 Markdown
     * @param answerKeyMd 标准答案与评分标准 Markdown
     * @param plan 题型分布方案（可为 null，缺省时解析器回退关键词判定）
     * @return 结构化题目列表，按印刷题号出现顺序排列
     */
    public List<ExamQuestion> splitAndBind(
            String sessionKey, String examPaperMd, String answerKeyMd, ExamPlan plan) {
        List<ExamQuestion> result = new ArrayList<>();
        List<Map<String, Object>> rawQuestions = ExamPaperParser.parse(examPaperMd, plan);
        if (rawQuestions.isEmpty()) {
            log.warn("[Split] 试卷切分为空 [session={}]", sessionKey);
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
            // 统一按共享口径统计填空数（下划线 + 括号空两相累加），对所有题型生效（不再仅限填空题）
            Integer parsedBlankCount = readInt(q.get("blankCount"));
            question.setBlankCount(
                    parsedBlankCount != null ? parsedBlankCount : ExamBlankCounter.count(stem));

            // 按题目位置序号（与答案键题序一致）绑定标准答案与解析
            Integer positionalIndex = readInt(q.get("index"));
            AnswerKeyParser.QuestionKey key =
                    positionalIndex != null ? keyMap.get(positionalIndex) : null;
            if (key != null) {
                bindAnswerAndAnalysis(question, key, type);
            }

            question.setCreateTime(now);
            question.setUpdateTime(now);
            result.add(question);
        }
        log.info(
                "[Split] 切分完成 [session={}, questions={}, keyEntries={}]",
                sessionKey,
                result.size(),
                keyMap.size());
        return result;
    }

    /**
     * 切分并落库（幂等：先清理该试卷已有行再批量写入），随后执行出卷契约校验。
     *
     * @return 落库数量与契约校验结果
     */
    public SplitOutcome splitAndPersist(
            String sessionKey, String examPaperMd, String answerKeyMd, ExamPlan plan) {
        if (sessionKey == null || sessionKey.isBlank()) {
            log.warn("[Split] sessionKey 为空，跳过落库");
            return new SplitOutcome(
                    0, new ExamContractValidator.Result(false, List.of("试卷标识为空，无法落库校验")));
        }
        // 重新切分（幂等回灌）会先删后插，人工在校对页绑定的配图不应随之丢失：
        // 删除前先按印刷题号快照既有 images_json，切分后回填题号未变的行（阶段 2 决策：按题号保留图片）。
        Map<Integer, String> imageSnapshot =
                snapshotImagesByNumber(examQuestionGateway.listBySessionKey(sessionKey));
        List<ExamQuestion> questions = splitAndBind(sessionKey, examPaperMd, answerKeyMd, plan);
        if (questions.isEmpty()) {
            return new SplitOutcome(
                    0, new ExamContractValidator.Result(false, List.of("试卷切分结果为空")));
        }
        int preserved = applyImageSnapshot(questions, imageSnapshot);
        examQuestionGateway.deleteBySessionKey(sessionKey);
        examQuestionGateway.batchInsert(questions);
        ExamContractValidator.Result validation = ExamContractValidator.validate(questions, plan);
        if (preserved > 0) {
            log.info("[Split] 重新切分保留人工配图绑定 [session={}, preserved={}]", sessionKey, preserved);
        }
        return new SplitOutcome(questions.size(), validation);
    }

    /** 快照既有行的人工配图绑定（印刷题号 → images_json），仅收录非空项。 */
    static Map<Integer, String> snapshotImagesByNumber(List<ExamQuestion> existingQuestions) {
        Map<Integer, String> snapshot = new HashMap<>();
        for (ExamQuestion existing : existingQuestions) {
            if (existing.getQuestionNumber() != null
                    && existing.getImagesJson() != null
                    && !existing.getImagesJson().isBlank()) {
                snapshot.put(existing.getQuestionNumber(), existing.getImagesJson());
            }
        }
        return snapshot;
    }

    /** 把配图快照回填到重切后的题目行（按印刷题号匹配），返回成功保留的绑定数。 */
    static int applyImageSnapshot(List<ExamQuestion> questions, Map<Integer, String> snapshot) {
        if (snapshot.isEmpty()) {
            return 0;
        }
        int preserved = 0;
        for (ExamQuestion question : questions) {
            String imagesJson = snapshot.get(question.getQuestionNumber());
            if (imagesJson != null) {
                question.setImagesJson(imagesJson);
                preserved++;
            }
        }
        return preserved;
    }

    // ==================== 工具 ====================

    /**
     * 绑定标准答案、解析与评分标准（按题型区分处理）。
     *
     * <p>客观题（单选 / 多选 / 判断）若答案键把「结论 + 解释」写在同一行（如 {@code 正确。理由：…}、 {@code B【解析】…}），拆出答案头部作为 {@code
     * correct_answer}，并在解析字段为空时把解释正文并入 {@code analysis}，使落库答案纯净、便于展示与校对；主观题（填空 / 简答 / 论述）答案本身即正文，
     * 原样绑定，避免误截。{@code scoring_criteria} 与题型无关，统一从答案键一次性绑定。
     *
     * @param question 目标题目行
     * @param key 答案键解析结果（非空）
     * @param type 题型 key
     */
    private void bindAnswerAndAnalysis(
            ExamQuestion question, AnswerKeyParser.QuestionKey key, String type) {
        question.setScoringCriteria(key.scoringCriteria());
        String rawAnswer = key.answer();
        boolean objective = type != null && OBJECTIVE_TYPES.contains(type.toUpperCase());
        if (!objective) {
            question.setCorrectAnswer(rawAnswer);
            question.setAnalysis(key.analysis());
            return;
        }
        String head = ExamAnswerNormalizer.answerHead(rawAnswer);
        question.setCorrectAnswer(head != null && !head.isBlank() ? head : rawAnswer);
        String analysis = key.analysis();
        if (analysis == null || analysis.isBlank()) {
            analysis = ExamAnswerNormalizer.explanationTail(rawAnswer);
        }
        question.setAnalysis(analysis);
    }

    private String serializeOptions(Object optionsObj, String type) {
        if (!"SINGLE_CHOICE".equals(type)
                && !"MULTI_CHOICE".equals(type)
                && !"TRUE_FALSE".equals(type)) {
            return null;
        }
        if (optionsObj == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(optionsObj);
        } catch (Exception e) {
            log.warn("[Split] 选项序列化失败（忽略）: {}", e.getMessage());
            return null;
        }
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
     * @param count 落库题目数量
     * @param validation 出卷契约校验结果
     */
    public record SplitOutcome(int count, ExamContractValidator.Result validation) {}
}
