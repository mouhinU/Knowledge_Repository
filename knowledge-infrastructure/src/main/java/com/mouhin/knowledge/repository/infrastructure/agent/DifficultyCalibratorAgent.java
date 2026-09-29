package com.mouhin.knowledge.repository.infrastructure.agent;

import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.service.BlackboardAgent;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Bloom 认知层级校准 Agent
 *
 * <p>基于 Bloom 教育目标分类学（记忆/理解/应用/分析/评价/创造）， 逐题标注认知层级并评估整体分布是否与目标难度匹配。 与现有 ExamCalibratorAgent 的区别：本
 * Agent 采用标准 Bloom 六层级分类， 输出结构化分类表而非自由文本评估报告。
 *
 * <p>读取：examPaper（试卷内容）、examDifficulty（目标难度）、question（主题） 写入：bloomClassification（Bloom 分类结果 + 分布统计
 * + 调整建议）
 *
 * @author mouhinU
 * @date 2026-09-28
 */
@Component("difficultyCalibratorAgent")
@Slf4j
public class DifficultyCalibratorAgent implements BlackboardAgent {

    private static final String SYSTEM_PROMPT =
            """
            你是一位教育测量学专家，精通 Bloom 教育目标分类学（修订版）。

            任务：
            1. 逐题分析并标注每道题的认知层级：
               - 记忆（Remember）：回忆事实、术语、基本概念
               - 理解（Understand）：解释、举例、分类、比较
               - 应用（Apply）：在新情境中使用概念、规则、方法
               - 分析（Analyze）：分解信息、识别关系、推断因果
               - 评价（Evaluate）：判断、论证、批判、评估
               - 创造（Create）：设计、构建、规划、生成新方案

            2. 统计各认知层级的题目数量和占比
            3. 与目标难度对应的理想分布对比：
               - 简单：记忆 30% + 理解 40% + 应用 20% + 分析 10%
               - 中等：理解 25% + 应用 35% + 分析 25% + 评价 15%
               - 困难：应用 25% + 分析 30% + 评价 25% + 创造 20%
            4. 标注偏差超过 10% 的层级，给出调整建议

            输出格式（严格按以下 Markdown 结构）：
            ## Bloom 分类明细
            | 题号 | 题目摘要 | 认知层级 | 判定依据 |
            |------|---------|---------|---------|

            ## 层级分布
            | 认知层级 | 题数 | 占比 | 理想占比 | 偏差 |
            |---------|------|------|---------|------|

            ## 校准结论
            （整体评价：分布是否符合目标难度要求）

            ## 调整建议
            （如有偏差过大的层级，给出具体调整建议；否则写"分布合理，无需调整"）
            """;

    private final BlackboardAgentStreamer agentStreamer;

    public DifficultyCalibratorAgent(BlackboardAgentStreamer agentStreamer) {
        this.agentStreamer = agentStreamer;
    }

    @Override
    public void execute(BlackboardState blackboard, BlackboardProgressCallback progressCallback) {
        String examPaper = blackboard.getExamPaper();
        String targetDifficulty = blackboard.getExamDifficulty();

        log.info("[BloomCalibrator] 开始 Bloom 认知层级分类，目标难度：{}", targetDifficulty);

        blackboard.advanceTo(BlackboardPhase.BLOOM_CALIBRATING);

        emitProgress(
                progressCallback,
                BlackboardProgressEvent.agentStartedWithMaterials(
                        "bloom-calibrator", "正在按 Bloom 分类逐题标注认知层级...", truncate(examPaper, 400)));

        if (examPaper == null || examPaper.isBlank()) {
            blackboard.setBloomClassification("试卷为空，无法进行 Bloom 分类。");
            emitProgress(
                    progressCallback,
                    BlackboardProgressEvent.agentCompleted("bloom-calibrator", "试卷为空"));
            return;
        }

        String difficultyDesc =
                switch (targetDifficulty != null ? targetDifficulty : "MEDIUM") {
                    case "EASY" -> "简单（记忆 30% + 理解 40% + 应用 20% + 分析 10%）";
                    case "HARD" -> "困难（应用 25% + 分析 30% + 评价 25% + 创造 20%）";
                    default -> "中等（理解 25% + 应用 35% + 分析 25% + 评价 15%）";
                };

        String userPrompt =
                String.format(
                        """
                考试主题：%s
                目标难度：%s

                【试卷内容】
                %s

                请按 Bloom 教育目标分类学逐题标注认知层级，并评估分布是否与目标难度匹配。
                """,
                        blackboard.getQuestion(), difficultyDesc, examPaper);

        String classification =
                agentStreamer.stream(
                        "bloom-calibrator", SYSTEM_PROMPT, userPrompt, progressCallback);

        if (classification == null || classification.isBlank()) {
            log.error("[BloomCalibrator] LLM 返回空分类结果");
            classification =
                    "## Bloom 分类明细\n分类异常\n\n## 层级分布\n无法评估\n\n## 校准结论\n分析失败\n\n## 调整建议\n请重试";
        }

        blackboard.setBloomClassification(classification);
        Double agentScore = calculateBloomScore(classification);
        emitProgress(
                progressCallback,
                BlackboardProgressEvent.agentCompleted(
                        "bloom-calibrator", classification, agentScore));
        log.info("[BloomCalibrator] Bloom 分类完成，长度：{} 字符", classification.length());
    }

    @Override
    public String getName() {
        return "DifficultyCalibrator";
    }

    /** 启发式评分：结构完整性（四部分各 20 分）+ 长度（上限 20 分） */
    private Double calculateBloomScore(String classification) {
        double score = Math.min(20.0, classification.length() / 100.0 * 2.0);
        if (classification.contains("Bloom 分类明细") || classification.contains("分类明细")) score += 20.0;
        if (classification.contains("层级分布")) score += 20.0;
        if (classification.contains("校准结论")) score += 20.0;
        if (classification.contains("调整建议")) score += 20.0;
        return Math.round(Math.min(100.0, score) * 100.0) / 100.0;
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private static void emitProgress(
            BlackboardProgressCallback callback, BlackboardProgressEvent event) {
        if (callback != null) {
            callback.onProgress(event);
        }
    }
}
