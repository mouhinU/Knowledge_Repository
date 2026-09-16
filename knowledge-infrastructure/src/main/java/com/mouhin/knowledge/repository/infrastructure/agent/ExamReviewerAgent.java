package com.mouhin.knowledge.repository.infrastructure.agent;

import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.service.BlackboardAgent;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 试卷审核 Agent
 * <p>
 * 审核试卷质量，从知识准确性、题目表述、知识点覆盖度、题型合理性等维度评估。
 * </p>
 * <p>
 * 读取：examPaper（试卷内容）、answerKey（答案）、keyFindings（知识点）、question（主题）
 * 写入：examReviewFeedback（审核反馈）、qualityScore（质量评分 0-100）
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-14
 */
@Component("examReviewerAgent")
public class ExamReviewerAgent implements BlackboardAgent {

    private static final Logger logger = LoggerFactory.getLogger(ExamReviewerAgent.class);

    private static final String SYSTEM_PROMPT = """
            你是一位资深的教育评估专家，负责审核考试试卷的质量。
            
            请从以下 6 个维度审核试卷，每个维度独立打分（0-100）：
            1. 知识准确性（权重 25%%）：题目和答案是否与知识点一致，有无事实错误
            2. 题目表述（权重 15%%）：题目是否清晰无歧义，选项是否合理
            3. 知识点覆盖（权重 25%%）：是否均匀覆盖了主要知识点，有无遗漏
            4. 题型合理性（权重 15%%）：各题型的设置是否恰当，题量是否合理
            5. 难度适当性（权重 10%%）：难度是否符合要求，梯度是否合理
            6. 格式规范性（权重 10%%）：排版、编号、分值标注是否规范
            
            输出格式（严格按以下 Markdown 结构）：
            ## 审核意见
            （详细的审核意见，按维度逐条分析）
            
            ## 评分明细
            - 知识准确性：X
            - 题目表述：X
            - 知识点覆盖：X
            - 题型合理性：X
            - 难度适当性：X
            - 格式规范性：X
            
            ## 质量评分
            （加权总分，一个 0-100 的整数，计算方式：知识准确性×0.25 + 题目表述×0.15 + 知识点覆盖×0.25 + 题型合理性×0.15 + 难度适当性×0.10 + 格式规范性×0.10）
            
            ## 改进建议
            （如有需要改进的地方，列出具体建议）
            """;

    private final ChatModel chatModel;

    public ExamReviewerAgent(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public void execute(BlackboardState blackboard, BlackboardProgressCallback progressCallback) {
        String examPaper = blackboard.getExamPaper();
        String answerKey = blackboard.getAnswerKey();
        String findings = blackboard.getKeyFindings();

        logger.info("[ExamReviewer] 开始审核试卷");

        blackboard.advanceTo(BlackboardPhase.REVIEWING);

        String materials = buildMaterialsPreview(examPaper, answerKey);
        emitProgress(progressCallback, BlackboardProgressEvent.agentStartedWithMaterials(
                "exam-reviewer", "正在审核试卷质量...", materials));

        if (examPaper == null || examPaper.isBlank()) {
            blackboard.setExamReviewFeedback("试卷为空，无法审核。");
            blackboard.setQualityScore(0);
            emitProgress(progressCallback, BlackboardProgressEvent.agentCompleted(
                    "exam-reviewer", "试卷为空，无法审核。"));
            return;
        }

        String knowledgeContext = (findings != null && !findings.contains("未找到"))
                ? "\n\n参考知识点：\n" + findings : "";

        String userPrompt = String.format("""
                考试主题：%s
                
                【试卷内容】
                %s
                
                【标准答案】
                %s
                %s
                
                请审核试卷质量。
                """, blackboard.getQuestion(), examPaper, answerKey, knowledgeContext);

        ChatRequest request = ChatRequest.builder()
                .messages(
                        SystemMessage.from(SYSTEM_PROMPT),
                        UserMessage.from(userPrompt)
                )
                .build();

        ChatResponse response = chatModel.chat(request);
        String reviewOutput = response.aiMessage().text();

        if (reviewOutput == null || reviewOutput.isBlank()) {
            logger.error("[ExamReviewer] LLM 返回空审核结果");
            reviewOutput = "## 审核意见\n审核过程异常，请重试。\n\n## 评分明细\n- 知识准确性：0\n- 题目表述：0\n- 知识点覆盖：0\n- 题型合理性：0\n- 难度适当性：0\n- 格式规范性：0\n\n## 质量评分\n0\n\n## 改进建议\n无";
        }

        blackboard.setExamReviewFeedback(reviewOutput);
        blackboard.setQualityScore(extractScore(reviewOutput));
        emitProgress(progressCallback, BlackboardProgressEvent.agentCompleted("exam-reviewer", reviewOutput));
        logger.info("[ExamReviewer] 审核完成，评分：{}", blackboard.getQualityScore());
    }

    private String buildMaterialsPreview(String examPaper, String answerKey) {
        StringBuilder sb = new StringBuilder();
        if (examPaper != null) {
            sb.append("【试卷预览】\n");
            sb.append(examPaper.length() > 300 ? examPaper.substring(0, 300) + "..." : examPaper);
            sb.append("\n\n");
        }
        if (answerKey != null) {
            sb.append("【答案预览】\n");
            sb.append(answerKey.length() > 200 ? answerKey.substring(0, 200) + "..." : answerKey);
        }
        return sb.toString();
    }

    private int extractScore(String reviewOutput) {
        try {
            // 优先尝试从评分明细中解析各维度分数并加权计算
            int detailIdx = reviewOutput.indexOf("## 评分明细");
            if (detailIdx >= 0) {
                String detailSection = reviewOutput.substring(detailIdx);
                // 截取到下一个 ## 之前
                int nextSection = detailSection.indexOf("\n##", 2);
                if (nextSection > 0) {
                    detailSection = detailSection.substring(0, nextSection);
                }

                int accuracy = extractDimensionScore(detailSection, "知识准确性");
                int wording = extractDimensionScore(detailSection, "题目表述");
                int coverage = extractDimensionScore(detailSection, "知识点覆盖");
                int typeReasonable = extractDimensionScore(detailSection, "题型合理性");
                int difficulty = extractDimensionScore(detailSection, "难度适当性");
                int format = extractDimensionScore(detailSection, "格式规范性");

                if (accuracy >= 0 && wording >= 0 && coverage >= 0
                        && typeReasonable >= 0 && difficulty >= 0 && format >= 0) {
                    int weighted = (int) Math.round(
                            accuracy * 0.25
                                    + wording * 0.15
                                    + coverage * 0.25
                                    + typeReasonable * 0.15
                                    + difficulty * 0.10
                                    + format * 0.10);
                    int result = Math.min(100, Math.max(0, weighted));
                    logger.info("[ExamReviewer] 维度评分：准确性={}, 表述={}, 覆盖={}, 题型={}, 难度={}, 格式={}, 加权总分={}",
                            accuracy, wording, coverage, typeReasonable, difficulty, format, result);
                    return result;
                }
            }

            // 回退：从质量评分中解析单一分数
            int idx = reviewOutput.indexOf("## 质量评分");
            if (idx >= 0) {
                String after = reviewOutput.substring(idx + "## 质量评分".length()).trim();
                StringBuilder num = new StringBuilder();
                for (char c : after.toCharArray()) {
                    if (Character.isDigit(c)) {
                        num.append(c);
                    } else if (num.length() > 0) {
                        break;
                    }
                }
                if (num.length() > 0) {
                    return Math.min(100, Math.max(0, Integer.parseInt(num.toString())));
                }
            }
        } catch (Exception e) {
            logger.warn("[ExamReviewer] 评分解析异常: {}", e.getMessage());
        }
        return 70;
    }

    /**
     * 从评分明细段落中提取指定维度的分数
     *
     * @param section   评分明细文本
     * @param dimension 维度名称
     * @return 分数（0-100），未找到时返回 -1
     */
    private int extractDimensionScore(String section, String dimension) {
        int idx = section.indexOf(dimension);
        if (idx < 0) {
            return -1;
        }
        String after = section.substring(idx + dimension.length());
        // 跳过冒号（中文或英文）
        int start = -1;
        for (int i = 0; i < after.length(); i++) {
            char c = after.charAt(i);
            if (c == '：' || c == ':') {
                start = i + 1;
                break;
            }
        }
        if (start < 0) {
            return -1;
        }
        StringBuilder num = new StringBuilder();
        for (int i = start; i < after.length(); i++) {
            char c = after.charAt(i);
            if (Character.isDigit(c)) {
                num.append(c);
            } else if (num.length() > 0) {
                break;
            }
        }
        if (num.length() > 0) {
            return Math.min(100, Math.max(0, Integer.parseInt(num.toString())));
        }
        return -1;
    }

    private void emitProgress(BlackboardProgressCallback callback, BlackboardProgressEvent event) {
        if (callback != null) {
            callback.onProgress(event);
        }
    }

    @Override
    public String getName() {
        return "ExamReviewer";
    }
}
