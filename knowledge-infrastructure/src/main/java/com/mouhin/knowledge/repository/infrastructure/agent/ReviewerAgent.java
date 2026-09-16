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
 * 审核员 Agent
 * <p>
 * 负责审核文章草稿的质量，给出修改建议和评分，并生成最终文章。
 * 读取：question + keyFindings + draftArticle
 * 写入：reviewFeedback + qualityScore + finalArticle
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-12
 */
@Component("reviewerAgent")
public class ReviewerAgent implements BlackboardAgent {

    private static final Logger logger = LoggerFactory.getLogger(ReviewerAgent.class);

    private static final String SYSTEM_PROMPT = """
            你是一个严格的文章审核编辑。你的任务是审核文章草稿的质量，并提供修改后的最终版本。
            
            审核维度：
            1. 准确性：内容是否与知识片段一致，有无编造或曲解
            2. 完整性：是否充分回答了用户的问题
            3. 逻辑性：文章结构是否清晰，论述是否连贯
            4. 可读性：语言是否流畅，格式是否规范
            
            输出要求：
            先输出审核意见（以 "## 审核意见" 开头），包含各维度评价和改进建议。
            然后输出 "## 质量评分"，给出 0-100 的整数分数。
            最后输出 "## 最终文章"，给出修改后的完整文章（Markdown 格式）。
            
            如果文章质量已经足够好（80分以上），最终文章可以保持原样或做微调。
            """;

    private final ChatModel chatModel;

    public ReviewerAgent(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public void execute(BlackboardState blackboard, BlackboardProgressCallback progressCallback) {
        logger.info("[Reviewer] 开始审核文章");

        blackboard.advanceTo(BlackboardPhase.REVIEWING);

        String draft = blackboard.getDraftArticle();
        String materials = (draft != null && !draft.isBlank())
                ? "写手的文章草稿：\n\n" + (draft.length() > 500 ? draft.substring(0, 500) + "..." : draft)
                : "（无草稿）";
        emitProgress(progressCallback, BlackboardProgressEvent.agentStartedWithMaterials(
                "reviewer", "正在审核文章质量...", materials));

        if (draft == null || draft.isBlank()) {
            blackboard.setFinalArticle("无法生成文章：草稿为空。");
            blackboard.setQualityScore(0);
            emitProgress(progressCallback, BlackboardProgressEvent.agentCompleted(
                    "reviewer", "无法生成文章：草稿为空。"));
            logger.warn("[Reviewer] 无草稿，跳过审核");
            return;
        }

        // 如果草稿是"信息不足"模板，直接传递为最终文章
        if (draft.contains("知识库中没有足够的信息")) {
            blackboard.setFinalArticle(draft);
            blackboard.setReviewFeedback("知识库检索结果为空，无法进行质量审核。");
            blackboard.setQualityScore(0);
            blackboard.advanceTo(BlackboardPhase.COMPLETED);
            emitProgress(progressCallback, BlackboardProgressEvent.agentCompleted(
                    "reviewer", "知识库检索结果为空，无法进行质量审核。"));
            logger.info("[Reviewer] 信息不足模板，跳过 LLM 审核");
            return;
        }

        String userPrompt = String.format("""
                        用户的问题：%s
                        
                        研究员的知识发现：
                        
                        %s
                        
                        写手的文章草稿：
                        
                        %s
                        
                        请审核这篇文章并输出最终版本。
                        """, blackboard.getQuestion(),
                blackboard.getKeyFindings(),
                draft);

        ChatRequest request = ChatRequest.builder()
                .messages(
                        SystemMessage.from(SYSTEM_PROMPT),
                        UserMessage.from(userPrompt)
                )
                .build();

        ChatResponse response = chatModel.chat(request);
        String reviewOutput = response.aiMessage().text();

        // 解析审核结果
        blackboard.setReviewFeedback(extractSection(reviewOutput, "审核意见"));
        blackboard.setQualityScore(extractScore(reviewOutput));
        blackboard.setFinalArticle(extractSection(reviewOutput, "最终文章"));

        // 如果无法解析最终文章，使用整个输出
        if (blackboard.getFinalArticle() == null || blackboard.getFinalArticle().isBlank()) {
            blackboard.setFinalArticle(reviewOutput);
        }

        blackboard.advanceTo(BlackboardPhase.COMPLETED);
        emitProgress(progressCallback, BlackboardProgressEvent.agentCompleted("reviewer", reviewOutput));
        logger.info("[Reviewer] 审核完成，质量评分：{}", blackboard.getQualityScore());
    }

    private void emitProgress(BlackboardProgressCallback callback, BlackboardProgressEvent event) {
        if (callback != null) {
            callback.onProgress(event);
        }
    }

    /**
     * 从 Markdown 文本中提取指定标题下的内容
     */
    private String extractSection(String text, String sectionTitle) {
        if (text == null) {
            return null;
        }
        String marker = "## " + sectionTitle;
        int start = text.indexOf(marker);
        if (start < 0) {
            return null;
        }
        start = text.indexOf('\n', start);
        if (start < 0) {
            return null;
        }
        start += 1;

        // 找到下一个 ## 标题或文本结尾
        int end = text.length();
        int nextHeader = text.indexOf("\n## ", start);
        if (nextHeader > start) {
            end = nextHeader;
        }

        return text.substring(start, end).trim();
    }

    /**
     * 从审核输出中提取质量评分
     */
    private int extractScore(String text) {
        if (text == null) {
            return 0;
        }
        String scoreSection = extractSection(text, "质量评分");
        if (scoreSection == null) {
            return 0;
        }
        // 提取数字
        StringBuilder numBuilder = new StringBuilder();
        for (char c : scoreSection.toCharArray()) {
            if (Character.isDigit(c)) {
                numBuilder.append(c);
            } else if (numBuilder.length() > 0) {
                break;
            }
        }
        if (numBuilder.length() > 0) {
            try {
                int score = Integer.parseInt(numBuilder.toString());
                return Math.min(100, Math.max(0, score));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    @Override
    public String getName() {
        return "Reviewer";
    }
}
