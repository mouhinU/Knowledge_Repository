package com.mouhin.knowledge.repository.infrastructure.agent;

import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.service.BlackboardAgent;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 试卷分值分配 Agent
 * <p>
 * 根据题型配置和难度等级，计算各题型的每题分值、小计和总分。
 * 纯计算型 Agent，不调用 LLM。
 * </p>
 * <p>
 * 读取：examQuestionConfig（题型配置）、examDifficulty（难度）
 * 写入：scoringScheme（分值分配方案）
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-14
 */
@Component("examScoringAgent")
public class ExamScoringAgent implements BlackboardAgent {

    private static final Logger logger = LoggerFactory.getLogger(ExamScoringAgent.class);

    /** 题型配置解析正则：匹配 "单选题10道" 或 "单选 10 道" 等格式 */
    private static final Pattern QUESTION_PATTERN =
            Pattern.compile("(单选题|多选题|判断题|填空题|简答题|论述题)\\s*(\\d+)\\s*[道题个]");

    /** 各难度下各题型的每题分值 */
    private static final Map<String, Map<String, Integer>> SCORING_TABLE = buildScoringTable();

    private static Map<String, Map<String, Integer>> buildScoringTable() {
        Map<String, Map<String, Integer>> table = new LinkedHashMap<>();

        Map<String, Integer> easy = new LinkedHashMap<>();
        easy.put("单选题", 2);
        easy.put("多选题", 3);
        easy.put("判断题", 2);
        easy.put("填空题", 2);
        easy.put("简答题", 5);
        easy.put("论述题", 10);
        table.put("EASY", easy);

        Map<String, Integer> medium = new LinkedHashMap<>();
        medium.put("单选题", 3);
        medium.put("多选题", 4);
        medium.put("判断题", 2);
        medium.put("填空题", 3);
        medium.put("简答题", 8);
        medium.put("论述题", 12);
        table.put("MEDIUM", medium);

        Map<String, Integer> hard = new LinkedHashMap<>();
        hard.put("单选题", 5);
        hard.put("多选题", 5);
        hard.put("判断题", 3);
        hard.put("填空题", 3);
        hard.put("简答题", 10);
        hard.put("论述题", 15);
        table.put("HARD", hard);

        return table;
    }

    @Override
    public void execute(BlackboardState blackboard, BlackboardProgressCallback progressCallback) {
        String questionConfig = blackboard.getExamQuestionConfig();
        String difficulty = blackboard.getExamDifficulty();

        logger.info("[ExamScoring] 开始计算分值分配，题型配置：{}，难度：{}", questionConfig, difficulty);

        blackboard.advanceTo(BlackboardPhase.SCORING);

        emitProgress(progressCallback, BlackboardProgressEvent.agentStartedWithMaterials(
                "exam-scoring", "正在计算各题型分值分配...", questionConfig));

        if (questionConfig == null || questionConfig.isBlank()) {
            logger.warn("[ExamScoring] 题型配置为空，跳过分值计算");
            blackboard.setScoringScheme("");
            emitProgress(progressCallback, BlackboardProgressEvent.agentCompleted(
                    "exam-scoring", "题型配置为空，跳过分值计算。"));
            return;
        }

        // 解析题型配置
        Map<String, Integer> questionCounts = parseQuestionConfig(questionConfig);
        if (questionCounts.isEmpty()) {
            logger.warn("[ExamScoring] 未能从配置中解析到题型，配置内容：{}", questionConfig);
            blackboard.setScoringScheme("");
            emitProgress(progressCallback, BlackboardProgressEvent.agentCompleted(
                    "exam-scoring", "未能解析题型配置，跳过分值计算。"));
            return;
        }

        // 获取难度对应的分值表
        String difficultyKey = difficulty != null ? difficulty.toUpperCase() : "MEDIUM";
        Map<String, Integer> pointsPerQuestion = SCORING_TABLE.getOrDefault(difficultyKey, SCORING_TABLE.get("MEDIUM"));

        // 计算分值方案
        String scoringScheme = buildScoringScheme(questionCounts, pointsPerQuestion, difficultyKey);
        blackboard.setScoringScheme(scoringScheme);

        emitProgress(progressCallback, BlackboardProgressEvent.agentCompleted("exam-scoring", scoringScheme));
        logger.info("[ExamScoring] 分值分配完成：\n{}", scoringScheme);
    }

    /**
     * 解析题型配置字符串，提取各题型及其数量
     *
     * @param config 题型配置，如 "单选题10道, 多选题5道, 判断题10道"
     * @return 题型名称 → 数量 的有序映射
     */
    private Map<String, Integer> parseQuestionConfig(String config) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Matcher matcher = QUESTION_PATTERN.matcher(config);
        while (matcher.find()) {
            String type = matcher.group(1);
            int count = Integer.parseInt(matcher.group(2));
            counts.put(type, count);
        }
        return counts;
    }

    /**
     * 构建分值分配方案文本
     *
     * @param questionCounts    各题型数量
     * @param pointsPerQuestion 各题型每题分值
     * @param difficulty        难度标识
     * @return 格式化的分值方案
     */
    private String buildScoringScheme(Map<String, Integer> questionCounts,
                                      Map<String, Integer> pointsPerQuestion,
                                      String difficulty) {
        String difficultyLabel = switch (difficulty) {
            case "EASY" -> "简单";
            case "HARD" -> "困难";
            default -> "中等";
        };

        StringBuilder sb = new StringBuilder();
        sb.append("# 分值分配方案\n\n");
        sb.append("**难度：** ").append(difficultyLabel).append("\n\n");
        sb.append("| 题型 | 题数 | 每题分值 | 小计 |\n");
        sb.append("|------|------|----------|------|\n");

        int totalQuestions = 0;
        int totalPoints = 0;

        for (Map.Entry<String, Integer> entry : questionCounts.entrySet()) {
            String type = entry.getKey();
            int count = entry.getValue();
            int pointsEach = pointsPerQuestion.getOrDefault(type, 3);
            int subtotal = count * pointsEach;

            sb.append("| ").append(type)
                    .append(" | ").append(count)
                    .append(" | ").append(pointsEach)
                    .append(" | ").append(subtotal)
                    .append(" |\n");

            totalQuestions += count;
            totalPoints += subtotal;
        }

        sb.append("| **合计** | **").append(totalQuestions)
                .append("** | — | **").append(totalPoints).append("** |\n");

        return sb.toString();
    }

    private void emitProgress(BlackboardProgressCallback callback, BlackboardProgressEvent event) {
        if (callback != null) {
            callback.onProgress(event);
        }
    }

    @Override
    public String getName() {
        return "ExamScoring";
    }
}
