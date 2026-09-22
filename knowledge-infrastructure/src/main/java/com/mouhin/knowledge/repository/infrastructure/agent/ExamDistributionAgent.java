package com.mouhin.knowledge.repository.infrastructure.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mouhin.knowledge.repository.domain.gateway.ExamDistributionGateway;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.model.valueobject.TypePlan;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import com.mouhin.knowledge.repository.domain.service.ScoreRuleEngine;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 题型分布方案 Agent（多阶段）
 *
 * <p>依据"考试主题/科目 + 难度 + 学段"产出一份可编辑的题型分布方案，内部按四个阶段推进：
 *
 * <ol>
 *   <li>题型分类：结合学科与学段选定合适的题型集合（内核题型固定 6 类，显示名可按科目命名）；
 *   <li>题型数量：为每个题型确定题量（结合难度与目标题量）；
 *   <li>每题分数：由 {@link ScoreRuleEngine} 确定性地把满分拆到每一道小题（Σ 恒等于满分）；
 *   <li>合理性评估：LLM 复审方案，仅给出建议、不自动改写。
 * </ol>
 *
 * 其中阶段①②由一次结构化 LLM 调用合并完成，阶段④为一次 LLM 调用，阶段③为纯计算。
 *
 * @author mouhinU
 * @date 2026-09-16
 */
@Component("examDistributionAgent")
@Slf4j
public class ExamDistributionAgent implements ExamDistributionGateway {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 允许的内核题型 */
    private static final List<String> ALLOWED_KEYS =
            List.of(
                    "SINGLE_CHOICE",
                    "MULTI_CHOICE",
                    "TRUE_FALSE",
                    "FILL_BLANK",
                    "SHORT_ANSWER",
                    "ESSAY");

    private final BlackboardAgentStreamer agentStreamer;

    public ExamDistributionAgent(BlackboardAgentStreamer agentStreamer) {
        this.agentStreamer = agentStreamer;
    }

    /**
     * 生成题型分布方案。
     *
     * @param topic 考试主题/科目
     * @param difficulty 难度（EASY / MEDIUM / HARD）
     * @param schoolLevelCode 学段编码（可空自动识别）
     * @param knowledgeHint 知识点摘要（用于让分类更贴合内容，可空）
     * @return 已归一化（Σ=满分）的分布方案
     */
    @Override
    public ExamPlan generate(
            String topic, String difficulty, String schoolLevelCode, String knowledgeHint) {
        return generate(topic, difficulty, schoolLevelCode, knowledgeHint, null);
    }

    /**
     * 生成题型分布方案（多阶段，可推送过程事件）。
     *
     * @param topic 考试主题/科目
     * @param difficulty 难度（EASY / MEDIUM / HARD）
     * @param schoolLevelCode 学段编码（可空自动识别）
     * @param knowledgeHint 知识点摘要（可空）
     * @param callback 进度回调（可空；非空时逐阶段推送输入/思考/输出）
     * @return 已归一化（Σ=满分）的分布方案
     */
    @Override
    public ExamPlan generate(
            String topic,
            String difficulty,
            String schoolLevelCode,
            String knowledgeHint,
            BlackboardProgressCallback callback) {
        ScoreRuleEngine.SchoolLevel level = ScoreRuleEngine.resolveLevel(topic, schoolLevelCode);
        int fullMark = ScoreRuleEngine.resolveTotalFullMark(topic, level, schoolLevelCode);

        log.info(
                "[Distribution] 开始生成方案 [topic='{}', difficulty='{}', level='{}', fullMark={}]",
                topic,
                difficulty,
                level,
                fullMark);

        String levelLabel = ScoreRuleEngine.levelLabel(level);
        String diffLabel = difficultyLabel(difficulty);

        // —— 阶段①② 题型分类 + 题量：一次结构化 LLM 调用（真流式思考与输出）——
        String input1 =
                String.format("主题：%s｜学段：%s｜难度：%s｜满分：%d 分", topic, levelLabel, diffLabel, fullMark)
                        + ((knowledgeHint != null && !knowledgeHint.isBlank())
                                ? "\n参考知识点：" + trim(knowledgeHint, 300)
                                : "");
        String think1 = "作为命题专家，依据学科常规结构与学段，在 6 类内核题型中挑选贴合的题型组合并为每种题型命名（显示名可按科目定制）。";
        emitRunning(callback, "dist-classify", input1, think1);

        List<TypePlan> structure =
                classifyAndCount(topic, difficulty, level, fullMark, knowledgeHint, callback);
        boolean fallback = structure.isEmpty();
        if (fallback) {
            structure = defaultStructure();
        }

        // 汇总选中的题型集合与各题型题量（供 dist-classify / dist-count 的 done 快照展示）
        StringBuilder typeNames = new StringBuilder();
        StringBuilder countDetail = new StringBuilder();
        for (TypePlan t : structure) {
            if (typeNames.length() > 0) {
                typeNames.append("、");
            }
            typeNames
                    .append(t.getLabel())
                    .append("（")
                    .append(ScoreRuleEngine.chineseFromKey(t.getKey()))
                    .append("）");
            countDetail
                    .append("· ")
                    .append(t.getLabel())
                    .append("：")
                    .append(t.getCount())
                    .append(" 道");
            if (t.getReason() != null && !t.getReason().isBlank()) {
                countDetail.append("　— ").append(t.getReason());
            }
            countDetail.append("\n");
        }
        String classifyOutput =
                "选定题型：" + typeNames + (fallback ? "\n（模型未返回有效结构，已启用学科通用兜底题型集合）" : "");
        emitDone(callback, "dist-classify", classifyOutput);

        // —— 阶段② 题型数量：输出各题型题量与依据 ——
        String input2 = "在上一步选定的 " + structure.size() + " 类题型基础上，结合难度与满分分配题量";
        String think2 = "低学段/易难度侧重客观题、题量大而单题分值小；高学段/难难度提高主观题比重。" + "题量与满分匹配：总题量不宜超过满分（否则出现 0 分小题）。";
        int totalQ = structure.stream().mapToInt(TypePlan::getCount).sum();
        emitRunning(callback, "dist-count", input2, think2);
        emitDone(callback, "dist-count", "题量安排（共 " + totalQ + " 题）：\n" + countDetail);

        ExamPlan plan = new ExamPlan();
        plan.setSchoolLevel(level.name());
        plan.setTotalFullMark(fullMark);
        List<ScoreRuleEngine.Subject> subjects = ScoreRuleEngine.detectSubjects(topic);
        plan.setCombined(subjects.size() > 1);
        for (ScoreRuleEngine.Subject s : subjects) {
            plan.getSubjects().add(ScoreRuleEngine.subjectLabel(s));
        }
        plan.setTypes(structure);

        // —— 阶段③ 每题分数：确定性把满分拆到每一道小题 ——
        String input3 = "目标满分 " + fullMark + " 分，共 " + totalQ + " 道小题；按题型权重（简答/论述更高）拆分";
        String think3 = "以题型权重为系数，用最大余额法把满分分配到每一道小题，保证各题分值之和恰等于满分；" + "同题型内默认等值，用户可在页面上逐题调整。";
        emitRunning(callback, "dist-score", input3, think3);
        ScoreRuleEngine.assignScoresByKindWeights(plan);
        ScoreRuleEngine.normalizePlan(plan);
        StringBuilder scoreDetail = new StringBuilder();
        for (TypePlan t : plan.getTypes()) {
            scoreDetail
                    .append("· ")
                    .append(t.getLabel())
                    .append("：每题 ")
                    .append(t.getPerQuestion())
                    .append(" 分，小计 ")
                    .append(t.subtotal())
                    .append(" 分\n");
        }
        scoreDetail
                .append("合计：")
                .append(plan.allocatedTotal())
                .append(" 分 / 满分 ")
                .append(fullMark)
                .append(" 分");
        emitDone(callback, "dist-score", scoreDetail.toString());

        // —— 阶段④ 合理性评估：LLM 复审，仅给建议 ——
        String input4 = "题型分布方案（含逐题分值）已生成，交由质检模型评估";
        String think4 = "关注题型是否贴合学科与学段、难度梯度、题量与分值占比是否均衡、满分是否被合理利用；" + "仅标注改进建议，不自动改写方案。";
        emitRunning(callback, "dist-evaluate", input4, think4);
        plan.setEvaluationNotes(evaluate(topic, difficulty, level, plan, callback));
        StringBuilder noteText = new StringBuilder();
        if (plan.getEvaluationNotes() == null || plan.getEvaluationNotes().isEmpty()) {
            noteText.append("题型分布合理，可直接出题");
        } else {
            for (String n : plan.getEvaluationNotes()) {
                noteText.append("· ").append(n).append("\n");
            }
        }
        emitDone(callback, "dist-evaluate", noteText.toString().trim());

        log.info(
                "[Distribution] 方案生成完成，共 {} 题型 {} 题，满分 {} 分",
                plan.getTypes().size(),
                plan.totalQuestions(),
                plan.getTotalFullMark());
        return plan;
    }

    private void emitRunning(
            BlackboardProgressCallback cb, String agent, String input, String thinking) {
        if (cb == null) {
            return;
        }
        cb.onProgress(
                new BlackboardProgressEvent.Builder()
                        .type("AGENT_OUTPUT")
                        .agentName(agent)
                        .agentStatus("running")
                        .message(thinking)
                        .materials(input)
                        .build());
    }

    private void emitDone(BlackboardProgressCallback cb, String agent, String result) {
        if (cb == null) {
            return;
        }
        cb.onProgress(BlackboardProgressEvent.agentCompleted(agent, result));
    }

    // ==================== 阶段①②：题型分类 + 题量 ====================

    private List<TypePlan> classifyAndCount(
            String topic,
            String difficulty,
            ScoreRuleEngine.SchoolLevel level,
            int fullMark,
            String knowledgeHint,
            BlackboardProgressCallback callback) {
        String systemPrompt =
                """
                你是资深命题专家，负责为一份考试确定"题型构成"和"每种题型题量"。
                内核题型只有 6 类，key 必须严格取自：
                SINGLE_CHOICE(单选)、MULTI_CHOICE(多选)、TRUE_FALSE(判断)、FILL_BLANK(填空)、SHORT_ANSWER(简答)、ESSAY(论述/解答/作文类主观题)。
                你可以为不同科目给题型起更贴切的显示名 label（如数学的 SHORT_ANSWER 显示为"解答题"、语文的 ESSAY 显示为"作文"、英语的 SHORT_ANSWER 显示为"阅读理解"），但 key 必须用上面 6 类之一。
                只输出 JSON，不要任何解释文字或代码块围栏。JSON 结构：
                {"types":[{"key":"SINGLE_CHOICE","label":"单选题","count":10,"reason":"覆盖基础概念辨析"}]}
                """;
        String hint =
                (knowledgeHint != null && !knowledgeHint.isBlank())
                        ? "\n参考知识点：\n" + trim(knowledgeHint, 600)
                        : "";
        String userPrompt =
                String.format(
                        """
                考试主题/科目：%s
                学段：%s
                难度：%s
                本卷满分：%d 分
                %s

                请依据学科特点与学段选择合适的题型组合并确定每种题型的题量。要求：
                - 至少 2 种题型，题型要贴合该学科常规考试结构（客观题+主观题搭配）；
                - 题量与满分匹配（题多则每题分值小，主观题少而分值高）；
                - 低学段/易难度以客观题为主，高学段/难难度增加主观题比重；
                - 只输出上述 JSON。
                """,
                        topic,
                        ScoreRuleEngine.levelLabel(level),
                        difficultyLabel(difficulty),
                        fullMark,
                        hint);

        JsonNode root = streamJson("dist-classify", systemPrompt, userPrompt, callback);
        List<TypePlan> plans = new ArrayList<>();
        if (root == null || !root.has("types")) {
            return plans;
        }
        for (JsonNode node : root.get("types")) {
            String key = text(node, "key");
            String kernel = ScoreRuleEngine.keyFromType(key == null ? "" : key.trim());
            if (!ALLOWED_KEYS.contains(kernel)) {
                continue;
            }
            String label = text(node, "label");
            if (label == null || label.isBlank()) {
                label = ScoreRuleEngine.chineseFromKey(kernel);
            }
            int count = node.has("count") ? node.get("count").asInt(0) : 0;
            if (count <= 0 || count > 100) {
                continue;
            }
            TypePlan tp = new TypePlan(kernel, label, count, new ArrayList<>());
            tp.setReason(text(node, "reason"));
            plans.add(tp);
        }
        return plans;
    }

    // ==================== 阶段④：合理性评估 ====================

    private List<String> evaluate(
            String topic,
            String difficulty,
            ScoreRuleEngine.SchoolLevel level,
            ExamPlan plan,
            BlackboardProgressCallback callback) {
        StringBuilder schemeText = new StringBuilder();
        for (TypePlan t : plan.getTypes()) {
            schemeText.append(
                    String.format(
                            "- %s（%s）：%d 道，小计 %d 分，逐题分值 %s\n",
                            t.getLabel(),
                            t.getKey(),
                            t.getCount(),
                            t.subtotal(),
                            t.getPerQuestion()));
        }
        String systemPrompt =
                """
                你是命题质检专家。审阅一份试卷的"题型分布方案"，判断是否合理，并给出简洁的改进建议。
                关注：题型是否贴合学科与学段、难度梯度、各题型题量与分值占比是否均衡、满分是否被合理利用。
                只输出 JSON：{"notes":["建议1","建议2"]}，notes 为 0~4 条中文短句；若方案合理可返回 {"notes":["题型分布合理，可直接出题"]}。
                """;
        String userPrompt =
                String.format(
                        """
                考试主题/科目：%s
                学段：%s
                难度：%s
                目标满分：%d 分
                题型分布方案：
                %s
                请给出合理性评估建议（JSON）。
                """,
                        topic,
                        ScoreRuleEngine.levelLabel(level),
                        difficultyLabel(difficulty),
                        plan.getTotalFullMark(),
                        schemeText);

        JsonNode root = streamJson("dist-evaluate", systemPrompt, userPrompt, callback);
        List<String> notes = new ArrayList<>();
        if (root != null && root.has("notes") && root.get("notes").isArray()) {
            for (JsonNode n : root.get("notes")) {
                String s = n.asText("").trim();
                if (!s.isEmpty()) {
                    notes.add(s);
                }
            }
        }
        // 客观校验并入提示
        int sum = plan.allocatedTotal();
        if (sum != plan.getTotalFullMark()) {
            notes.add("当前各题分值之和为 " + sum + " 分，与满分 " + plan.getTotalFullMark() + " 分不一致，请调整。");
        }
        if (plan.totalQuestions() > plan.getTotalFullMark()) {
            notes.add("题目数量多于满分，将出现分值为 0 的小题，建议减少题量。");
        }
        if (notes.isEmpty()) {
            notes.add("题型分布合理，可直接出题");
        }
        return notes;
    }

    // ==================== 兜底默认结构 ====================

    private List<TypePlan> defaultStructure() {
        List<TypePlan> plans = new ArrayList<>();
        plans.add(new TypePlan("SINGLE_CHOICE", "单选题", 5, new ArrayList<>()));
        plans.add(new TypePlan("MULTI_CHOICE", "多选题", 3, new ArrayList<>()));
        plans.add(new TypePlan("TRUE_FALSE", "判断题", 5, new ArrayList<>()));
        plans.add(new TypePlan("FILL_BLANK", "填空题", 5, new ArrayList<>()));
        plans.add(new TypePlan("SHORT_ANSWER", "简答题", 3, new ArrayList<>()));
        plans.add(new TypePlan("ESSAY", "论述题", 2, new ArrayList<>()));
        return plans;
    }

    // ==================== LLM 调用与 JSON 解析 ====================

    private JsonNode streamJson(
            String agentName,
            String systemPrompt,
            String userPrompt,
            BlackboardProgressCallback callback) {
        try {
            String text = agentStreamer.stream(agentName, systemPrompt, userPrompt, callback);
            return extractJson(text);
        } catch (Exception e) {
            log.warn("[Distribution] 流式 LLM 调用/解析失败: {}", e.getMessage());
            return null;
        }
    }

    private JsonNode extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.replaceAll("```[a-zA-Z]*", "").replace("```", "").trim();
        int objStart = s.indexOf('{');
        int objEnd = s.lastIndexOf('}');
        if (objStart >= 0 && objEnd > objStart) {
            try {
                return OBJECT_MAPPER.readTree(s.substring(objStart, objEnd + 1));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.has(field) && !node.get(field).isNull()
                ? node.get(field).asText("").trim()
                : null;
    }

    private static String difficultyLabel(String difficulty) {
        return switch (difficulty != null ? difficulty : "MEDIUM") {
            case "EASY" -> "简单";
            case "HARD" -> "困难";
            default -> "中等";
        };
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
