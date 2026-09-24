package com.mouhin.knowledge.repository.infrastructure.agent;

import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.service.BlackboardAgent;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 答案生成 Agent
 *
 * <p>为已生成的试卷创建标准答案、评分要点和赋分规则。
 *
 * <p>读取：examPaper（试卷内容）、question（考试主题）、keyFindings（知识点） 写入：answerKey（标准答案与评分标准）
 *
 * @author mouhinU
 * @date 2026-09-14
 */
@Component("answerKeyGeneratorAgent")
@Slf4j
public class AnswerKeyGeneratorAgent implements BlackboardAgent {

    private static final String SYSTEM_PROMPT =
            """
            你是一位考试答案编写专家，负责为试卷生成标准答案和评分标准。

            要求：
            1. 答案必须准确无误，基于提供的知识点
            2. 选择题直接给出正确选项
            3. 判断题给出"正确"或"错误"，并简要说明理由（禁止使用 √/× 符号）
            4. 填空题给出精确的填空内容
            5. 简答题给出要点答案（关键得分点）
            6. 论述题给出详细的参考答案和评分细则
            7. 每题标注分值分配
            8. 多选题答案用纯字母无分隔拼接（如 AC、ABD），不加逗号或空格
            9. 题号与试卷完全一致且全局连续（试卷第 1 题对应答案第 1 题），禁止分节重新起号

            输出格式：
            - 使用 Markdown 格式
            - 按题型分节，与试卷结构对应
            - 每题答案前标注题号，分值 "（X分）" 紧跟题号/答案行，不要写进选项文本里
            - 选择题的逐选项给分只写在"评分标准"中（如：选B得3分），不要把它追加到选项标签后面
            - 主观题列出评分要点（如：提到 XX 得 2 分）
            """;

    private final BlackboardAgentStreamer agentStreamer;

    public AnswerKeyGeneratorAgent(BlackboardAgentStreamer agentStreamer) {
        this.agentStreamer = agentStreamer;
    }

    @Override
    public void execute(BlackboardState blackboard, BlackboardProgressCallback progressCallback) {
        String examPaper = blackboard.getExamPaper();
        String findings = blackboard.getKeyFindings();

        log.info("[AnswerKeyGenerator] 开始生成答案与评分标准");

        blackboard.advanceTo(BlackboardPhase.ANSWER_GENERATING);

        String materials =
                examPaper != null && examPaper.length() > 500
                        ? examPaper.substring(0, 500) + "..."
                        : examPaper;
        emitProgress(
                progressCallback,
                BlackboardProgressEvent.agentStartedWithMaterials(
                        "answer-generator", "正在为试卷生成标准答案...", materials));

        if (examPaper == null || examPaper.isBlank()) {
            blackboard.setAnswerKey("试卷为空，无法生成答案。");
            emitProgress(
                    progressCallback,
                    BlackboardProgressEvent.agentCompleted("answer-generator", "试卷为空，无法生成答案。"));
            return;
        }

        String knowledgeContext =
                (findings != null && !findings.contains("未找到")) ? "\n\n参考知识点：\n" + findings : "";

        String userPrompt =
                String.format(
                        """
                考试主题：%s

                以下是需要生成答案的试卷：

                %s
                %s

                请为每道题生成标准答案和评分标准。
                """,
                        blackboard.getQuestion(), examPaper, knowledgeContext);

        String answerKey =
                agentStreamer.stream(
                        "answer-generator", SYSTEM_PROMPT, userPrompt, progressCallback);

        if (answerKey == null || answerKey.isBlank()) {
            log.error("[AnswerKeyGenerator] LLM 返回空答案");
            answerKey = "> 答案生成失败，请重试。";
        }

        blackboard.setAnswerKey(answerKey);
        Double agentScore = calculateAnswerScore(examPaper, answerKey);
        emitProgress(
                progressCallback,
                BlackboardProgressEvent.agentCompleted("answer-generator", answerKey, agentScore));
        log.info("[AnswerKeyGenerator] 答案生成完成，长度：{} 字符，得分：{}", answerKey.length(), agentScore);
    }

    @Override
    public String getName() {
        return "AnswerKeyGenerator";
    }

    /**
     * 计算答案生成 Agent 的启发式得分（0-100，保留两位小数）
     *
     * <p>评分依据：答案长度与试卷长度的比值（覆盖率）+ 答案结构完整性
     */
    private Double calculateAnswerScore(String examPaper, String answerKey) {
        double score = 0.0;
        // 覆盖率贡献 70 分（答案长度/试卷长度 * 70，上限 70）
        double ratio = (double) answerKey.length() / examPaper.length();
        score += Math.min(70.0, ratio * 70.0);
        // 结构完整性贡献 30 分（有题型分节/评分标准等）
        if (answerKey.contains("##") || answerKey.contains("一、")) score += 10.0;
        if (answerKey.contains("评分标准") || answerKey.contains("评分要点")) score += 10.0;
        if (answerKey.contains("答案") || answerKey.contains("答：")) score += 10.0;
        return Math.round(Math.min(100.0, score) * 100.0) / 100.0;
    }

    private void emitProgress(BlackboardProgressCallback callback, BlackboardProgressEvent event) {
        if (callback != null) {
            callback.onProgress(event);
        }
    }
}
