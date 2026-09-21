package com.mouhin.knowledge.repository.infrastructure.agent;

import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.service.BlackboardAgent;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 查重去重 Agent
 *
 * <p>分析试卷内容的原创性，检查是否存在重复考查同一知识点或高度相似的题目。
 *
 * <p>读取：examPaper（试卷内容）、answerKey（答案）、question（主题） 写入：deduplicationReport（查重报告）
 *
 * @author Knowledge-Repository
 * @date 2026-09-14
 */
@Component("examDeduplicatorAgent")
public class ExamDeduplicatorAgent implements BlackboardAgent {

    private static final Logger logger = LoggerFactory.getLogger(ExamDeduplicatorAgent.class);

    private static final String SYSTEM_PROMPT =
            """
            你是一位试卷查重专家，负责检查试卷中是否存在重复或高度相似的题目。

            检查维度：
            1. 知识点重复：不同题目是否考查了完全相同的知识点
            2. 题目相似：是否有题目在表述或考查角度上高度相似
            3. 选项重叠：选择题的选项之间是否有不合理的内容重叠
            4. 答案一致：不同题目的答案是否实质上相同

            输出格式（严格按以下 Markdown 结构）：
            ## 查重结果
            （列出发现的重复或相似题目对，说明重复原因）

            ## 重复率评估
            （给出整体重复率百分比估计）

            ## 建议
            （如有重复，给出替换或修改建议）
            """;

    private final BlackboardAgentStreamer agentStreamer;

    public ExamDeduplicatorAgent(BlackboardAgentStreamer agentStreamer) {
        this.agentStreamer = agentStreamer;
    }

    @Override
    public void execute(BlackboardState blackboard, BlackboardProgressCallback progressCallback) {
        String examPaper = blackboard.getExamPaper();
        String answerKey = blackboard.getAnswerKey();

        logger.info("[ExamDeduplicator] 开始查重分析");

        blackboard.advanceTo(BlackboardPhase.DEDUPLICATING);

        String materials =
                examPaper != null && examPaper.length() > 400
                        ? examPaper.substring(0, 400) + "..."
                        : examPaper;
        emitProgress(
                progressCallback,
                BlackboardProgressEvent.agentStartedWithMaterials(
                        "exam-deduplicator", "正在检查试卷重复内容...", materials));

        if (examPaper == null || examPaper.isBlank()) {
            blackboard.setDeduplicationReport("试卷为空，无法进行查重。");
            emitProgress(
                    progressCallback,
                    BlackboardProgressEvent.agentCompleted("exam-deduplicator", "试卷为空，无法查重。"));
            return;
        }

        String answerContext =
                (answerKey != null && !answerKey.contains("失败")) ? "\n\n【标准答案】\n" + answerKey : "";

        String userPrompt =
                String.format(
                        """
                考试主题：%s

                【试卷内容】
                %s
                %s

                请检查试卷中是否存在重复或高度相似的题目。
                """,
                        blackboard.getQuestion(), examPaper, answerContext);

        String report =
                agentStreamer.stream(
                        "exam-deduplicator", SYSTEM_PROMPT, userPrompt, progressCallback);

        if (report == null || report.isBlank()) {
            logger.error("[ExamDeduplicator] LLM 返回空查重报告");
            report = "## 查重结果\n未发现明显重复。\n\n## 重复率评估\n约 0%\n\n## 建议\n无需调整。";
        }

        blackboard.setDeduplicationReport(report);
        emitProgress(
                progressCallback,
                BlackboardProgressEvent.agentCompleted("exam-deduplicator", report));
        logger.info("[ExamDeduplicator] 查重完成，长度：{} 字符", report.length());
    }

    @Override
    public String getName() {
        return "ExamDeduplicator";
    }

    private void emitProgress(BlackboardProgressCallback callback, BlackboardProgressEvent event) {
        if (callback != null) {
            callback.onProgress(event);
        }
    }
}
