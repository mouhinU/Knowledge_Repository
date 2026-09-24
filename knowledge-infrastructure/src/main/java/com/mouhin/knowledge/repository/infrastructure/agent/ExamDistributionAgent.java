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
 * 题型分布方案 Agent（多阶段，支持轮次循环重生成）
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
 * <p>当评估不合理时，应用层可循环调用 {@link #generateForRound} 重新跑完四阶段， 每轮使用独立的 SSE agent 名称前缀（{@code
 * r{round}-classify} 等），前端为每轮创建独立进度卡片。
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

    @Override
    public ExamPlan generate(
            String topic, String difficulty, String schoolLevelCode, String knowledgeHint) {
        return generate(topic, difficulty, schoolLevelCode, knowledgeHint, null);
    }

    @Override
    public ExamPlan generate(
            String topic,
            String difficulty,
            String schoolLevelCode,
            String knowledgeHint,
            BlackboardProgressCallback callback) {
        return generateForRound(
                topic, difficulty, schoolLevelCode, knowledgeHint, 1, null, null, callback);
    }

    /**
     * 按轮次生成题型分布方案（四阶段完整重跑）。
     *
     * <p>每轮使用独立的 SSE agent 名称前缀（{@code r{round}-classify} 等），使前端能为每轮创建独立的进度卡片， 保留历史评估结果。
     */
    @Override
    public ExamPlan generateForRound(
            String topic,
            String difficulty,
            String schoolLevelCode,
            String knowledgeHint,
            int round,
            List<String> prevEvaluationNotes,
            ExamPlan prevPlan,
            BlackboardProgressCallback callback) {
        ScoreRuleEngine.SchoolLevel level = ScoreRuleEngine.resolveLevel(topic, schoolLevelCode);
        int fullMark = ScoreRuleEngine.resolveTotalFullMark(topic, level, schoolLevelCode);
        String pfx = "r" + round + "-";

        boolean hasPrevFeedback = prevEvaluationNotes != null && !prevEvaluationNotes.isEmpty();
        log.info(
                "[Distribution] 第 {} 轮生成方案 [topic='{}', level='{}', fullMark={}, hasPrevFeedback={}]",
                round,
                topic,
                level,
                fullMark,
                hasPrevFeedback);

        String levelLabel = ScoreRuleEngine.levelLabel(level);
        String diffLabel = difficultyLabel(difficulty);

        // —— 阶段① 题型分类 ——
        String input1 =
                String.format("主题：%s｜学段：%s｜难度：%s｜满分：%d 分", topic, levelLabel, diffLabel, fullMark)
                        + ((knowledgeHint != null && !knowledgeHint.isBlank())
                                ? "\n参考知识点：" + trim(knowledgeHint, 300)
                                : "");
        String think1 = "作为命题专家，依据学科常规结构与学段，在 6 类内核题型中挑选贴合的题型组合。";
        emitRunning(callback, pfx + "classify", input1, think1);

        List<TypePlan> structure =
                classifyAndCount(
                        topic,
                        difficulty,
                        level,
                        fullMark,
                        knowledgeHint,
                        round,
                        prevEvaluationNotes,
                        callback);
        boolean fallback = structure.isEmpty();
        if (fallback) {
            structure = defaultStructure();
        }

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
                countDetail.append(" — ").append(t.getReason());
            }
            countDetail.append("\n");
        }
        String classifyOutput = "选定题型：" + typeNames + (fallback ? "\n（已启用学科通用兜底题型集合）" : "");
        emitDone(callback, pfx + "classify", classifyOutput);

        // —— 阶段② 题型数量 ——
        String input2 =
                "在上一步选定的 "
                        + structure.size()
                        + " 类题型基础上，结合难度与满分分配题量"
                        + (hasPrevFeedback ? "（含上轮评估反馈）" : "");
        String think2 =
                "低学段/易难度侧重客观题；高学段/难难度提高主观题比重。" + (hasPrevFeedback ? "重点参考上一轮评估建议调整各题型题量。" : "");
        int totalQ = structure.stream().mapToInt(TypePlan::getCount).sum();
        emitRunning(callback, pfx + "count", input2, think2);
        emitDone(callback, pfx + "count", "题量安排（共 " + totalQ + " 题）：\n" + countDetail);

        ExamPlan plan = new ExamPlan();
        plan.setSchoolLevel(level.name());
        plan.setTotalFullMark(fullMark);
        List<ScoreRuleEngine.Subject> subjects = ScoreRuleEngine.detectSubjects(topic);
        plan.setCombined(subjects.size() > 1);
        for (ScoreRuleEngine.Subject s : subjects) {
            plan.getSubjects().add(ScoreRuleEngine.subjectLabel(s));
        }
        plan.setTypes(structure);

        // —— 阶段③ 每题分数：确定性计算 ——
        String input3 = "目标满分 " + fullMark + " 分，共 " + totalQ + " 道小题；按题型权重拆分";
        String think3 = "以题型权重为系数，用最大余额法把满分分配到每一道小题。";
        emitRunning(callback, pfx + "score", input3, think3);
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
        emitDone(callback, pfx + "score", scoreDetail.toString());

        // —— 阶段④ 合理性评估：LLM 复审 ——
        String input4 = "题型分布方案（含逐题分值）已生成，交由质检模型评估";
        String think4 = "关注题型贴合度、难度梯度、题量与分值占比均衡性。";
        emitRunning(callback, pfx + "evaluate", input4, think4);
        plan.setEvaluationNotes(
                evaluate(
                        topic,
                        difficulty,
                        level,
                        plan,
                        round,
                        prevPlan,
                        prevEvaluationNotes,
                        callback));
        StringBuilder noteText = new StringBuilder();
        if (plan.getEvaluationNotes() == null || plan.getEvaluationNotes().isEmpty()) {
            noteText.append("题型分布合理，可直接出题");
        } else {
            for (String n : plan.getEvaluationNotes()) {
                noteText.append("· ").append(n).append("\n");
            }
        }
        emitDone(callback, pfx + "evaluate", noteText.toString().trim());

        log.info(
                "[Distribution] 第 {} 轮完成，{} 题型 {} 题，满分 {} 分",
                round,
                plan.getTypes().size(),
                plan.totalQuestions(),
                plan.getTotalFullMark());
        return plan;
    }

    // ==================== 方案修正（保留向后兼容，新流程不再使用） ====================

    @Override
    public ExamPlan adjustPlan(
            String topic,
            ExamPlan plan,
            List<String> issues,
            List<String> suggestions,
            BlackboardProgressCallback callback) {
        ScoreRuleEngine.SchoolLevel level =
                ScoreRuleEngine.resolveLevel(topic, plan.getSchoolLevel());
        int fullMark = plan.getTotalFullMark();

        String agentName = "dist-adjust";
        String input =
                String.format(
                        "修正题型分布方案 [topic='%s', fullMark=%d, issues=%d, suggestions=%d]",
                        topic, fullMark, issues.size(), suggestions.size());
        String thinking = "依据校验问题与建议，调整题型、题量或逐题分值。";
        emitRunning(callback, agentName, input, thinking);

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

        StringBuilder issuesText = new StringBuilder();
        if (!issues.isEmpty()) {
            issuesText.append("【必须修复的问题】\n");
            for (String issue : issues) {
                issuesText.append("- ").append(issue).append("\n");
            }
        }
        StringBuilder suggestionsText = new StringBuilder();
        if (!suggestions.isEmpty()) {
            suggestionsText.append("【改进建议】\n");
            for (String s : suggestions) {
                suggestionsText.append("- ").append(s).append("\n");
            }
        }

        String systemPrompt =
                """
                你是资深命题专家，负责修正一份"题型分布方案"使其更合理。
                内核题型只有 6 类，key 必须严格取自：
                SINGLE_CHOICE(单选)、MULTI_CHOICE(多选)、TRUE_FALSE(判断)、FILL_BLANK(填空)、SHORT_ANSWER(简答)、ESSAY(论述/解答/作文类主观题)。
                修正规则：
                1. 各题型 perQuestion 数组长度必须等于 count；
                2. 所有 perQuestion 分值之和必须恰好等于满分；
                3. 每个 perQuestion 值 >= 1；
                4. 尽量保留原方案的题型和显示名，只调整数量与分值；
                5. 客观题（单选/多选/判断）每题分值较低（2-5分），主观题（简答/论述）每题分值较高（5-25分）。
                只输出 JSON，不要任何解释文字或代码块围栏。JSON 结构：
                {"types":[{"key":"SINGLE_CHOICE","label":"单选题","count":10,"perQuestion":[3,3,3,3,3,3,3,3,3,3]}]}
                """;
        String userPrompt =
                String.format(
                        """
                考试主题/科目：%s
                学段：%s
                本卷满分：%d 分

                当前方案：
                %s
                %s
                %s
                请修正方案使所有问题得到修复，并尽量满足改进建议。只输出 JSON。
                """,
                        topic,
                        ScoreRuleEngine.levelLabel(level),
                        fullMark,
                        schemeText,
                        issuesText,
                        suggestionsText);

        JsonNode root = streamJson(agentName, systemPrompt, userPrompt, callback);
        if (root == null || !root.has("types")) {
            log.warn("[Distribution] 方案修正 LLM 返回无效，保留原方案");
            emitDone(callback, agentName, "修正失败（LLM 返回无效），保留原方案");
            return null;
        }

        List<TypePlan> newTypes = new ArrayList<>();
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
            List<Integer> perQuestion = new ArrayList<>();
            if (node.has("perQuestion") && node.get("perQuestion").isArray()) {
                for (JsonNode pq : node.get("perQuestion")) {
                    perQuestion.add(pq.asInt(0));
                }
            }
            while (perQuestion.size() < count) {
                perQuestion.add(
                        perQuestion.isEmpty() ? 2 : perQuestion.get(perQuestion.size() - 1));
            }
            if (perQuestion.size() > count) {
                perQuestion = new ArrayList<>(perQuestion.subList(0, count));
            }
            TypePlan tp = new TypePlan(kernel, label, count, perQuestion);
            tp.setReason(text(node, "reason"));
            newTypes.add(tp);
        }

        if (newTypes.isEmpty()) {
            log.warn("[Distribution] 方案修正解析为空，保留原方案");
            emitDone(callback, agentName, "修正失败（解析为空），保留原方案");
            return null;
        }

        ExamPlan adjusted = new ExamPlan();
        adjusted.setSchoolLevel(plan.getSchoolLevel());
        adjusted.setTotalFullMark(plan.getTotalFullMark());
        adjusted.setCombined(plan.isCombined());
        adjusted.setSubjects(plan.getSubjects());
        adjusted.setTypes(newTypes);
        adjusted.setEvaluationNotes(plan.getEvaluationNotes());
        ScoreRuleEngine.normalizePlan(adjusted);

        StringBuilder resultText = new StringBuilder("修正后方案：\n");
        for (TypePlan t : adjusted.getTypes()) {
            resultText.append(
                    String.format(
                            "· %s：%d 道，每题 %s 分，小计 %d 分\n",
                            t.getLabel(), t.getCount(), t.getPerQuestion(), t.subtotal()));
        }
        resultText
                .append("合计：")
                .append(adjusted.allocatedTotal())
                .append(" 分 / 满分 ")
                .append(fullMark)
                .append(" 分");
        emitDone(callback, agentName, resultText.toString());
        return adjusted;
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
            int round,
            List<String> prevEvaluationNotes,
            BlackboardProgressCallback callback) {
        boolean hasPrev = prevEvaluationNotes != null && !prevEvaluationNotes.isEmpty();
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

        // 构建上一轮评估反馈段落
        String feedbackSection = "";
        if (hasPrev) {
            StringBuilder fb = new StringBuilder("\n【上一轮合理性评估反馈（务必针对性调整）】\n");
            for (String note : prevEvaluationNotes) {
                fb.append("- ").append(note).append("\n");
            }
            fb.append("请根据以上反馈调整题型选择和各题型题量，解决指出的问题。");
            feedbackSection = fb.toString();
        }

        String userPrompt =
                String.format(
                        """
                考试主题/科目：%s
                学段：%s
                难度：%s
                本卷满分：%d 分
                %s
                %s
                请依据学科特点与学段选择合适的题型组合并确定每种题型的题量。要求：
                - 至少 2 种题型，题型要贴合该学科常规考试结构（客观题+主观题搭配）；
                - 题量与满分匹配（题多则每题分值小，主观题少而分值高）；
                - 低学段/易难度以客观题为主，高学段/难难度增加主观题比重；
                - 每种题型的 count 应结合该科目的考试特点：如语文/英语阅读和写作占比高，数学选择填空占比高，物理/化学实验题和计算题比重大；
                - 各题型题量比例要合理，避免某类题型过多或过少（如主观题不宜超过总题量的 40%%，选择题不宜低于总题量的 30%%）；%s
                - 只输出上述 JSON。
                """,
                        topic,
                        ScoreRuleEngine.levelLabel(level),
                        difficultyLabel(difficulty),
                        fullMark,
                        hint,
                        feedbackSection,
                        hasPrev ? "\n                - 重点解决上一轮评估反馈中指出的题量不合理问题" : "");

        String agentName = "r" + round + "-classify";
        JsonNode root = streamJson(agentName, systemPrompt, userPrompt, callback);
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

    // ==================== 阶段④：结构化合理性评估 ====================

    private List<String> evaluate(
            String topic,
            String difficulty,
            ScoreRuleEngine.SchoolLevel level,
            ExamPlan plan,
            int round,
            ExamPlan prevPlan,
            List<String> prevFeedback,
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

        int totalQ = plan.totalQuestions();
        int fullMark = plan.getTotalFullMark();
        double avgPerQ = totalQ > 0 ? (double) fullMark / totalQ : fullMark;

        // 构建上一轮方案对比段落
        StringBuilder prevSection = new StringBuilder();
        if (prevPlan != null) {
            prevSection.append("\n【上一轮方案（用于对比判断反馈是否已落实）】\n");
            for (TypePlan t : prevPlan.getTypes()) {
                prevSection.append(
                        String.format(
                                "- %s（%s）：%d 道，小计 %d 分\n",
                                t.getLabel(), t.getKey(), t.getCount(), t.subtotal()));
            }
            prevSection
                    .append("上轮总题量：")
                    .append(prevPlan.totalQuestions())
                    .append("，满分：")
                    .append(prevPlan.getTotalFullMark())
                    .append("\n");
        }
        if (prevFeedback != null && !prevFeedback.isEmpty()) {
            prevSection.append("\n【上一轮反馈（需逐条判断是否已解决）】\n");
            for (String fb : prevFeedback) {
                prevSection.append("- ").append(fb).append("\n");
            }
        }

        String systemPrompt =
                """
                你是命题质检专家。按以下固定维度和量化标准评估"题型分布方案"，每个维度独立判定 PASS 或 FAIL。

                ## 评估维度与通过标准

                1. **总题量**：是否在学段合理区间内
                   - 小学低年级(1-2年级)：15-30道
                   - 小学高年级(3-6年级)：20-35道
                   - 初中：22-35道
                   - 高中：18-30道

                2. **题型占比**：任一题型分值占比是否在 10%-45% 之间
                   - 计算：该题型小计分 / 满分 × 100%%
                   - 所有题型都须在 10%-45% 范围内

                3. **单题分值**：各题型每题分值是否符合常规
                   - 选择/判断：1-3分
                   - 填空：2-4分
                   - 解答/计算：4-15分
                   - 论述/作文：10-25分

                4. **客观/主观比**：客观题(单选+多选+判断)分值占比是否在 30%-65%
                   - 计算：客观题小计 / 满分 × 100%%

                5. **分值匹配**：所有小题分值之和是否恰好等于满分
                   - 必须严格相等

                6. **题型适配**：题型选择是否贴合该学科和学段的考试常规
                   - 如一年级不宜有大量阅读理解类主观题，物理化学须有实验/计算题

                ## 判定规则

                - 所有维度 PASS → overallVerdict = "PASS"
                - 任一维度 FAIL → overallVerdict = "FAIL"，在对应 detail 中写明具体数值差距和改进目标值

                ## 上一轮反馈检查

                如果提供了上一轮反馈，需逐条判断：
                - "已解决"：当前方案已按建议调整到位
                - "未解决"：当前方案仍未落实该建议，说明原因

                ## 输出格式

                只输出 JSON，不要任何解释文字或代码块围栏：
                {"overallVerdict":"PASS或FAIL","dimensions":[{"name":"维度名","verdict":"PASS或FAIL","detail":"具体数值与判断依据"}],"resolvedFeedback":["已解决的反馈描述"],"suggestions":["仍需改进的具体建议，须包含明确数值目标"]}

                若所有维度通过且无未解决反馈，返回：
                {"overallVerdict":"PASS","dimensions":[...全PASS...],"resolvedFeedback":[...],"suggestions":[]}
                """;

        String userPrompt =
                String.format(
                        """
                考试主题/科目：%s
                学段：%s
                难度：%s
                目标满分：%d 分
                当前总题量：%d 道
                平均每道题分值：%.1f 分

                【当前题型分布方案】
                %s
                %s
                请按固定维度逐项评估，输出 JSON。
                """,
                        topic,
                        ScoreRuleEngine.levelLabel(level),
                        difficultyLabel(difficulty),
                        fullMark,
                        totalQ,
                        avgPerQ,
                        schemeText,
                        prevSection);

        String agentName = "r" + round + "-evaluate";
        JsonNode root = streamJson(agentName, systemPrompt, userPrompt, callback);

        List<String> notes = new ArrayList<>();
        String overallVerdict = "FAIL";

        if (root != null) {
            // 解析整体判定
            if (root.has("overallVerdict")) {
                String v = root.get("overallVerdict").asText("").trim();
                if ("PASS".equalsIgnoreCase(v)) {
                    overallVerdict = "PASS";
                }
            }

            // 解析维度详情，将 FAIL 维度写入 notes
            if (root.has("dimensions") && root.get("dimensions").isArray()) {
                for (JsonNode dim : root.get("dimensions")) {
                    String dimName = dim.has("name") ? dim.get("name").asText("") : "";
                    String dimVerdict = dim.has("verdict") ? dim.get("verdict").asText("") : "";
                    String dimDetail = dim.has("detail") ? dim.get("detail").asText("") : "";
                    if (!"PASS".equalsIgnoreCase(dimVerdict)) {
                        String note = dimName.isEmpty() ? dimDetail : dimName + "：" + dimDetail;
                        if (!note.isBlank()) {
                            notes.add(note);
                        }
                    }
                }
            }

            // 解析仍需改进的建议
            if (root.has("suggestions") && root.get("suggestions").isArray()) {
                for (JsonNode s : root.get("suggestions")) {
                    String text = s.asText("").trim();
                    if (!text.isEmpty()) {
                        notes.add(text);
                    }
                }
            }
        }

        // 确定性兜底检查（不受 LLM 输出影响）
        int sum = plan.allocatedTotal();
        if (sum != plan.getTotalFullMark()) {
            notes.add("当前各题分值之和为 " + sum + " 分，与满分 " + plan.getTotalFullMark() + " 分不一致，请调整。");
            overallVerdict = "FAIL";
        }
        if (plan.totalQuestions() > plan.getTotalFullMark()) {
            notes.add("题目数量多于满分，将出现分值为 0 的小题，建议减少题量。");
            overallVerdict = "FAIL";
        }

        // 无任何 FAIL 时强制 PASS
        if (notes.isEmpty()) {
            overallVerdict = "PASS";
            notes.add("题型分布合理，可直接出题");
        }

        plan.setOverallVerdict(overallVerdict);
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
