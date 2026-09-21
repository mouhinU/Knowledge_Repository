package com.mouhin.knowledge.repository.infrastructure.agent;

import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.service.BlackboardAgent;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 难度校准 Agent
 *
 * <p>分析试卷整体难度分布，与目标难度对比，给出调整建议。 如果难度偏差较大，会输出调整后的试卷内容。
 *
 * <p>读取：examPaper（试卷内容）、examDifficulty（目标难度）、question（主题） 写入：difficultyAssessment（难度评估报告）
 *
 * @author Knowledge-Repository
 * @date 2026-09-14
 */
@Component("examCalibratorAgent")
@Slf4j
public class ExamCalibratorAgent implements BlackboardAgent {

    private static final String SYSTEM_PROMPT =
            """
            你是一位考试难度校准专家，负责评估和调整试卷的难度分布。

            任务：
            1. 逐题分析试卷中每道题的认知层次（记忆/理解/应用/分析/综合）
            2. 评估整体难度分布是否与目标难度匹配
            3. 检查是否有过难或过易的题目
            4. 如果难度偏差较大，给出具体的调整建议

            输出格式（严格按以下 Markdown 结构）：
            ## 难度分析
            （逐题分析认知层次和难度）

            ## 难度分布
            （统计各认知层次的题目占比）

            ## 校准结论
            （总体评价：难度是否符合目标要求）

            ## 调整建议
            （如有需要调整的题目，给出具体建议）
            """;

    private final BlackboardAgentStreamer agentStreamer;

    public ExamCalibratorAgent(BlackboardAgentStreamer agentStreamer) {
        this.agentStreamer = agentStreamer;
    }

    @Override
    public void execute(BlackboardState blackboard, BlackboardProgressCallback progressCallback) {
        String examPaper = blackboard.getExamPaper();
        String targetDifficulty = blackboard.getExamDifficulty();

        log.info("[ExamCalibrator] 开始难度校准，目标难度：{}", targetDifficulty);

        blackboard.advanceTo(BlackboardPhase.CALIBRATING);

        String materials =
                examPaper != null && examPaper.length() > 400
                        ? examPaper.substring(0, 400) + "..."
                        : examPaper;
        emitProgress(
                progressCallback,
                BlackboardProgressEvent.agentStartedWithMaterials(
                        "exam-calibrator", "正在分析试卷难度分布...", materials));

        if (examPaper == null || examPaper.isBlank()) {
            blackboard.setDifficultyAssessment("试卷为空，无法进行难度校准。");
            emitProgress(
                    progressCallback,
                    BlackboardProgressEvent.agentCompleted("exam-calibrator", "试卷为空，无法校准。"));
            return;
        }

        String difficultyDesc =
                switch (targetDifficulty) {
                    case "EASY" -> "简单（以记忆和理解层次为主，约占 70%）";
                    case "HARD" -> "困难（以应用、分析和综合层次为主，约占 60%）";
                    default -> "中等（理解和应用层次为主，约占 60%）";
                };

        String userPrompt =
                String.format(
                        """
                考试主题：%s
                目标难度：%s

                【试卷内容】
                %s

                请分析试卷的难度分布，评估是否与目标难度匹配。
                """,
                        blackboard.getQuestion(), difficultyDesc, examPaper);

        String assessment =
                agentStreamer.stream(
                        "exam-calibrator", SYSTEM_PROMPT, userPrompt, progressCallback);

        if (assessment == null || assessment.isBlank()) {
            log.error("[ExamCalibrator] LLM 返回空评估结果");
            assessment = "## 难度分析\n分析过程异常，请重试。\n\n## 难度分布\n无\n\n## 校准结论\n无法评估\n\n## 调整建议\n无";
        }

        blackboard.setDifficultyAssessment(assessment);
        emitProgress(
                progressCallback,
                BlackboardProgressEvent.agentCompleted("exam-calibrator", assessment));
        log.info("[ExamCalibrator] 难度校准完成，长度：{} 字符", assessment.length());
    }

    @Override
    public String getName() {
        return "ExamCalibrator";
    }

    private void emitProgress(BlackboardProgressCallback callback, BlackboardProgressEvent event) {
        if (callback != null) {
            callback.onProgress(event);
        }
    }
}
