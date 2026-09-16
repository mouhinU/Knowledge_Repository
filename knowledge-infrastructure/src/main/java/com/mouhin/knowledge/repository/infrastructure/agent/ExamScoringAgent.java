package com.mouhin.knowledge.repository.infrastructure.agent;

import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.service.BlackboardAgent;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import com.mouhin.knowledge.repository.domain.service.ScoreRuleEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 试卷分值分配 Agent
 * <p>
 * 依据学段与科目的分数规则确定试卷目标满分，再按题型权重、小题数量把满分拆分为
 * 整数分值方案（各题型每题分值、小计）。纯计算型 Agent，不调用 LLM。
 * </p>
 * <p>
 * 读取：examQuestionConfig（题型配置）、examSchoolLevel / question（学段与科目）、examDifficulty
 * 写入：scoringScheme（分值分配方案）、examTotalScore（目标满分）
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-14
 */
@Component("examScoringAgent")
public class ExamScoringAgent implements BlackboardAgent {

    private static final Logger logger = LoggerFactory.getLogger(ExamScoringAgent.class);

    /**
     * 题型配置解析正则：匹配 "单选题10道" 或 "单选 10 道" 等格式
     */
    private static final Pattern QUESTION_PATTERN =
            Pattern.compile("(单选题|多选题|判断题|填空题|简答题|论述题)\\s*(\\d+)\\s*[道题个]");

    @Override
    public void execute(BlackboardState blackboard, BlackboardProgressCallback progressCallback) {
        String questionConfig = blackboard.getExamQuestionConfig();
        String topic = blackboard.getQuestion();

        // 优先采用页面确认的题型分布方案（含逐题分值）
        com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan plan = blackboard.getExamPlan();
        if (plan != null && plan.getTypes() != null && !plan.getTypes().isEmpty()) {
            logger.info("[ExamScoring] 采用已确认的题型分布方案，主题：{}，满分：{}", topic, plan.getTotalFullMark());
            blackboard.advanceTo(BlackboardPhase.SCORING);
            ScoreRuleEngine.normalizePlan(plan);
            blackboard.setExamTotalScore(plan.getTotalFullMark());
            String schemeText = ScoreRuleEngine.renderPlan(plan, topic);
            blackboard.setScoringScheme(schemeText);
            emitProgress(progressCallback, BlackboardProgressEvent.agentCompleted("exam-scoring", schemeText));
            logger.info("[ExamScoring] 分值分配完成（采用方案，满分 {} 分）", plan.getTotalFullMark());
            return;
        }

        logger.info("[ExamScoring] 开始计算分值分配，主题：{}，学段：{}，题型配置：{}",
                topic, blackboard.getExamSchoolLevel(), questionConfig);

        blackboard.advanceTo(BlackboardPhase.SCORING);

        emitProgress(progressCallback, BlackboardProgressEvent.agentStartedWithMaterials(
                "exam-scoring", "正在按学段分数规则计算分值分配...", questionConfig));

        LinkedHashMap<String, Integer> questionCounts = parseQuestionConfig(questionConfig);
        if (questionCounts.isEmpty()) {
            logger.warn("[ExamScoring] 未解析到题型配置，按默认满分兜底：{}", questionConfig);
            // 仍计算目标满分，供试卷编写遵循
            ScoreRuleEngine.SchoolLevel level =
                    ScoreRuleEngine.resolveLevel(topic, blackboard.getExamSchoolLevel());
            int total = ScoreRuleEngine.resolveTotalFullMark(topic, level, blackboard.getExamSchoolLevel());
            blackboard.setExamTotalScore(total);
            blackboard.setScoringScheme("本卷满分：" + total + " 分（未能解析题型配置，请自行合理分配分值）");
            emitProgress(progressCallback, BlackboardProgressEvent.agentCompleted(
                    "exam-scoring", blackboard.getScoringScheme()));
            return;
        }

        // 学段解析 + 目标满分
        ScoreRuleEngine.SchoolLevel level =
                ScoreRuleEngine.resolveLevel(topic, blackboard.getExamSchoolLevel());
        int total = ScoreRuleEngine.resolveTotalFullMark(topic, level, blackboard.getExamSchoolLevel());
        blackboard.setExamTotalScore(total);

        // 分值分配
        ScoreRuleEngine.ScoreScheme scheme = ScoreRuleEngine.allocate(total, questionCounts);
        String schemeText = ScoreRuleEngine.renderScheme(scheme, topic, level);
        blackboard.setScoringScheme(schemeText);

        emitProgress(progressCallback, BlackboardProgressEvent.agentCompleted("exam-scoring", schemeText));
        logger.info("[ExamScoring] 分值分配完成（满分 {} 分）：\n{}", total, schemeText);
    }

    /**
     * 解析题型配置字符串，提取各题型及其数量
     *
     * @param config 题型配置，如 "单选题10道, 多选题5道, 判断题10道"
     * @return 题型名称 → 数量 的有序映射
     */
    private LinkedHashMap<String, Integer> parseQuestionConfig(String config) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        if (config == null || config.isBlank()) {
            return counts;
        }
        Matcher matcher = QUESTION_PATTERN.matcher(config);
        while (matcher.find()) {
            String type = matcher.group(1);
            int count = Integer.parseInt(matcher.group(2));
            if (count > 0) {
                counts.merge(type, count, Integer::sum);
            }
        }
        return counts;
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
