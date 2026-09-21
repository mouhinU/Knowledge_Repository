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
 * 答案生成 Agent
 *
 * <p>为已生成的试卷创建标准答案、评分要点和赋分规则。
 *
 * <p>读取：examPaper（试卷内容）、question（考试主题）、keyFindings（知识点） 写入：answerKey（标准答案与评分标准）
 *
 * @author Knowledge-Repository
 * @date 2026-09-14
 */
@Component("answerKeyGeneratorAgent")
public class AnswerKeyGeneratorAgent implements BlackboardAgent {

    private static final Logger logger = LoggerFactory.getLogger(AnswerKeyGeneratorAgent.class);

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

        logger.info("[AnswerKeyGenerator] 开始生成答案与评分标准");

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
            logger.error("[AnswerKeyGenerator] LLM 返回空答案");
            answerKey = "> 答案生成失败，请重试。";
        }

        blackboard.setAnswerKey(answerKey);
        emitProgress(
                progressCallback,
                BlackboardProgressEvent.agentCompleted("answer-generator", answerKey));
        logger.info("[AnswerKeyGenerator] 答案生成完成，长度：{} 字符", answerKey.length());
    }

    @Override
    public String getName() {
        return "AnswerKeyGenerator";
    }

    private void emitProgress(BlackboardProgressCallback callback, BlackboardProgressEvent event) {
        if (callback != null) {
            callback.onProgress(event);
        }
    }
}
