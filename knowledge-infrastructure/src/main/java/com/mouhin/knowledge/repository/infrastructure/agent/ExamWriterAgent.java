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
 * 试卷编写 Agent
 * <p>
 * 基于知识点分析结果和考试配置（难度、题型分布），生成完整的考试试卷。
 * </p>
 * <p>
 * 读取：keyFindings（知识点摘要）、question（考试主题）、examDifficulty、examQuestionConfig
 * 写入：examPaper（试卷 Markdown 内容）
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-14
 */
@Component("examWriterAgent")
public class ExamWriterAgent implements BlackboardAgent {

    private static final Logger logger = LoggerFactory.getLogger(ExamWriterAgent.class);

    /** 有效试卷的最小字符长度阈值：低于此值视为模型未真正产出内容（截断/思考耗尽）。 */
    private static final int MIN_VALID_EXAM_LENGTH = 200;

    private static final String SYSTEM_PROMPT = """
            你是一位资深教育考试专家，擅长根据知识内容编写高质量的考试试卷。
            
            出题原则：
            1. 题目必须基于提供的知识点，不得超出知识范围
            2. 题目表述清晰准确，避免歧义
            3. 选择题的干扰项要合理，不能过于明显
            4. 题目难度要符合指定要求
            5. 知识点覆盖要均匀，不重复考查同一知识点
            6. 题目序号连续编排，分值标注清晰
            
            输出格式要求：
            - 使用 Markdown 格式
            - 试卷标题后紧跟一行考试时长信息，格式为：**考试时间：XX分钟**
              根据题目数量、难度和科目特性合理设定时长（一般 30~120 分钟）
            - 必须在试卷开头标注总分，格式为：**满分：XX分**，且 XX 必须严格等于【分值分配方案】中给定的"本卷满分"
            - 按题型分节（一、单选题 / 二、多选题 / ...）
            - 每题都要标注分值，格式为中文括号 "（X分）"，该分值必须严格取自【分值分配方案】中对应题型的小题分值
            - 分值 "（X分）" 必须放在题干文字的最末尾、任何选项（A./B./C./D.）之前，例如："**1.** 下列关于……的说法正确的是（3分）"
            - 严禁把分值放在选项后面或写进选项文本里（如 "D. 选项内容（3分）" 是错误的）；也不要单独把分值另起一行放在选项之后
            - 所有题目分值之和必须等于试卷满分，不得自行增减分值
            - 若为合卷（方案含合卷说明），按科目分节组织大题（如"第一部分 物理""第二部分 化学"），并保证各科目部分分值符合其占比
            - 选择题选项用 A. B. C. D. 格式
            - 只输出试卷部分，不要输出答案
            """;

    private final BlackboardAgentStreamer agentStreamer;

    public ExamWriterAgent(BlackboardAgentStreamer agentStreamer) {
        this.agentStreamer = agentStreamer;
    }

    @Override
    public void execute(BlackboardState blackboard, BlackboardProgressCallback progressCallback) {
        String findings = blackboard.getKeyFindings();
        String topic = blackboard.getQuestion();
        String difficulty = blackboard.getExamDifficulty();
        String questionConfig = blackboard.getExamQuestionConfig();
        String reviewFeedback = blackboard.getExamReviewFeedback();
        String scoringScheme = blackboard.getScoringScheme();

        boolean isRetry = reviewFeedback != null && !reviewFeedback.isBlank();
        logger.info("[ExamWriter] 开始{}编写试卷，主题：{}，难度：{}", isRetry ? "改进" : "", topic, difficulty);

        blackboard.advanceTo(BlackboardPhase.WRITING);

        String materials = findings != null && findings.length() > 500
                ? findings.substring(0, 500) + "..." : findings;
        emitProgress(progressCallback, BlackboardProgressEvent.agentStartedWithMaterials(
                "exam-writer", isRetry ? "正在根据审核意见改进试卷..." : "正在根据知识点编写试卷...", materials));

        if (findings == null || findings.isBlank() || findings.contains("未找到")) {
            String fallback = "# " + topic + " 考试试卷\n\n> 提示：知识库中未找到足够的相关内容，试卷基于 AI 通用知识生成。\n\n";
            blackboard.setExamPaper(fallback);
            emitProgress(progressCallback, BlackboardProgressEvent.agentCompleted(
                    "exam-writer", fallback));
            logger.warn("[ExamWriter] 知识点不足，使用回退方案");
            return;
        }

        String difficultyDesc = switch (difficulty) {
            case "EASY" -> "简单（侧重基础概念和记忆）";
            case "HARD" -> "困难（侧重综合分析和应用）";
            default -> "中等（侧重理解和简单应用）";
        };

        String scoringSection = (scoringScheme != null && !scoringScheme.isBlank())
                ? "\n\n【分值分配方案（严格遵守）】\n" + scoringScheme : "";

        String userPrompt;
        if (isRetry) {
            userPrompt = String.format("""
                    考试主题：%s
                    难度要求：%s
                    题型分布：%s
                    %s
                    以下是相关知识点：
                    
                    %s
                    
                    【上一轮审核意见】
                    %s
                    
                    请根据以上审核意见中的改进建议，重新编写一份高质量的考试试卷。
                    重点解决审核中指出的问题，保持优点，修正不足。
                    每道题的分值必须严格按照分值分配方案执行。只输出试卷，不要输出答案。
                    """, topic, difficultyDesc, questionConfig, scoringSection, findings, reviewFeedback);
        } else {
            userPrompt = String.format("""
                    考试主题：%s
                    难度要求：%s
                    题型分布：%s
                    %s
                    以下是相关知识点：
                    
                    %s
                    
                    请根据以上知识点和要求，编写一份完整的考试试卷。
                    每道题的分值必须严格按照分值分配方案执行。只输出试卷，不要输出答案。
                    """, topic, difficultyDesc, questionConfig, scoringSection, findings);
        }

        String examPaper = agentStreamer.stream("exam-writer", SYSTEM_PROMPT, userPrompt, progressCallback);

        // 快速失败：空或异常简短的试卷说明模型未真正产出内容（多为推理模型思考链耗尽
        // token 预算）。此前会写成占位文本让流水线空转多轮，现直接抛异常中止并回报错误。
        if (examPaper == null || examPaper.isBlank() || examPaper.length() < MIN_VALID_EXAM_LENGTH) {
            String msg = "试卷编写失败：模型未产出有效内容（可能是思考链耗尽 token 预算），请重试或调大流式 max-tokens";
            logger.error("[ExamWriter] LLM 返回空/过短试卷，长度：{}", examPaper == null ? 0 : examPaper.length());
            emitProgress(progressCallback, BlackboardProgressEvent.agentFailed("exam-writer", msg));
            throw new RuntimeException(msg);
        }

        blackboard.setExamPaper(examPaper);
        emitProgress(progressCallback, BlackboardProgressEvent.agentCompleted("exam-writer", examPaper));
        logger.info("[ExamWriter] 试卷编写完成，长度：{} 字符{}", examPaper.length(), isRetry ? "（改进轮次）" : "");
    }

    @Override
    public String getName() {
        return "ExamWriter";
    }

    private void emitProgress(BlackboardProgressCallback callback, BlackboardProgressEvent event) {
        if (callback != null) {
            callback.onProgress(event);
        }
    }
}
