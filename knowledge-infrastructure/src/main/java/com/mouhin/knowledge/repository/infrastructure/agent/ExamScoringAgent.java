package com.mouhin.knowledge.repository.infrastructure.agent;

import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.service.BlackboardAgent;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import com.mouhin.knowledge.repository.domain.service.ScorePlanValidator;
import com.mouhin.knowledge.repository.domain.service.ScoreRuleEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 分值校验与评估 Agent
 * <p>
 * 出卷流水线第 2 节点。<b>不再重排分值</b>——分值分配已由前置「题型分布方案」阶段生成并经用户确认；
 * 本 Agent 只做：
 * </p>
 * <ol>
 *     <li>硬校验：合计分值 = 本卷满分、每题 ≥ 1 分、perQuestion 长度 = count</li>
 *     <li>合理性评估：单题占比、题型小计占比、题量均值、主客观题覆盖、满分是否标准（100/120/150）</li>
 *     <li>不合格：抛出异常阻断流水线，向 SSE 上报"分值校验未通过 + 建议"，后续「试卷编写 / 答案生成 /
 *         难度校准 / 内容审核 / 查重去重」节点均不再执行</li>
 *     <li>合格：把方案渲染为 Markdown 塞入 blackboard.scoringScheme，供 Writer 严格遵循</li>
 * </ol>
 * <p>无方案时（老路径只传 questionConfig），退回 {@link ScoreRuleEngine#buildDefaultPlan} 生成兜底方案，
 * 再走同一份校验。</p>
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
        String topic = blackboard.getQuestion();
        String questionConfig = blackboard.getExamQuestionConfig();
        ExamPlan plan = blackboard.getExamPlan();

        blackboard.advanceTo(BlackboardPhase.SCORING);

        String materials = describeInput(plan, questionConfig);
        emitProgress(progressCallback, BlackboardProgressEvent.agentStartedWithMaterials(
                "exam-scoring", "正在校验题型分布方案的总分与分值分布...", materials));
        logger.info("[ExamScoring] 开始校验评估，主题：{}，学段：{}，题型配置：{}",
                topic, blackboard.getExamSchoolLevel(), questionConfig);

        // 兜底：无方案时基于 questionConfig 生成默认方案（保持旧行为）
        if (plan == null || plan.getTypes() == null || plan.getTypes().isEmpty()) {
            LinkedHashMap<String, Integer> counts = parseQuestionConfig(questionConfig);
            if (counts.isEmpty()) {
                String report = "### 结论\n\n❌ 未提供题型分布方案，且无法从 questionConfig 解析出题量，无法进入校验评估";
                failPipeline(progressCallback, report, materials);
                return;
            }
            plan = ScoreRuleEngine.buildDefaultPlan(topic, counts);
            blackboard.setExamPlan(plan);
            logger.info("[ExamScoring] 未收到方案，基于 questionConfig 构建默认方案继续校验");
        }

        ScorePlanValidator.Result result = ScorePlanValidator.validate(plan);
        String report = ScorePlanValidator.renderReport(plan, result);

        if (!result.pass()) {
            // 先输出报告，让 UI 展示"输入 / 原始思考 / 建议"三段
            emitProgress(progressCallback, BlackboardProgressEvent.agentFailed("exam-scoring", report));
            String error = "分值校验未通过，共 " + result.issues().size() + " 项硬性错误。请回到题型分布方案调整后再重新生成试卷。\n"
                    + String.join("\n", result.issues());
            logger.warn("[ExamScoring] 校验未通过 [topic='{}', issues={}]", topic, result.issues());
            throw new IllegalStateException(error);
        }

        // 校验通过：写入 scoringScheme 供 Writer 严格遵循；不再重排题目分值
        blackboard.setExamTotalScore(plan.getTotalFullMark());
        String schemeText = ScoreRuleEngine.renderPlan(plan, topic);
        blackboard.setScoringScheme(schemeText);

        String finalOutput = report + "\n---\n\n### 分值方案（供 Writer 严格遵循）\n\n" + schemeText;
        emitProgress(progressCallback, BlackboardProgressEvent.agentCompleted("exam-scoring", finalOutput));
        if (!result.suggestions().isEmpty()) {
            logger.info("[ExamScoring] 校验通过但含 {} 条优化建议 [topic='{}']",
                    result.suggestions().size(), topic);
        } else {
            logger.info("[ExamScoring] 校验通过（满分 {} 分）", plan.getTotalFullMark());
        }
    }

    /**
     * 无方案且无 questionConfig 时统一走异常路径阻断流水线
     */
    private void failPipeline(BlackboardProgressCallback callback, String report, String materials) {
        emitProgress(callback, BlackboardProgressEvent.agentFailed("exam-scoring", report));
        throw new IllegalStateException("分值校验未通过：方案与题型配置均为空，无法确定试卷结构");
    }

    /**
     * 描述本 Agent 的输入物料（供 SSE 面板"输入"区块展示）
     */
    private String describeInput(ExamPlan plan, String questionConfig) {
        StringBuilder sb = new StringBuilder();
        sb.append("主题输入源：");
        if (plan != null && plan.getTypes() != null && !plan.getTypes().isEmpty()) {
            sb.append("已确认的题型分布方案（").append(plan.getTypes().size()).append(" 种题型，合计 ")
                    .append(plan.totalQuestions()).append(" 题，满分 ").append(plan.getTotalFullMark()).append(" 分）");
        } else if (questionConfig != null && !questionConfig.isBlank()) {
            sb.append("题型配置字符串：").append(questionConfig);
        } else {
            sb.append("（空）");
        }
        return sb.toString();
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
