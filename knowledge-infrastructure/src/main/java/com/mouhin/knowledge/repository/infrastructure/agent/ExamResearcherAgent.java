package com.mouhin.knowledge.repository.infrastructure.agent;

import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.model.valueobject.SearchResult;
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

import java.util.List;

/**
 * 出卷研究员 Agent
 * <p>
 * 负责从知识库检索结果中提取与考试主题相关的关键知识点，
 * 为后续出题提供结构化的知识基础。
 * </p>
 * <p>
 * 读取：knowledgeChunks（向量检索结果）、question（考试主题）
 * 写入：keyFindings（结构化的知识点摘要）
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-14
 */
@Component("examResearcherAgent")
public class ExamResearcherAgent implements BlackboardAgent {

    private static final Logger logger = LoggerFactory.getLogger(ExamResearcherAgent.class);

    private static final String SYSTEM_PROMPT = """
            你是一个教育知识库研究员。你的任务是根据考试主题，从知识库检索结果中提取和组织关键知识点。

            要求：
            1. 仔细阅读每个知识片段，提取与考试主题直接相关的知识点
            2. 按知识领域或主题分类组织，形成结构化的知识点清单
            3. 标注每条知识点的来源文档
            4. 评估每个知识点适合出什么类型的题目（概念题、应用题、分析题等）
            5. 如果知识点不足以覆盖考试主题，明确指出缺失的知识领域
            6. 保持客观准确，不要添加自己的推测

            输出格式：使用 Markdown 格式，按知识领域分节组织。
            """;

    private final ChatModel chatModel;

    public ExamResearcherAgent(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public void execute(BlackboardState blackboard, BlackboardProgressCallback progressCallback) {
        List<SearchResult> chunks = blackboard.getKnowledgeChunks();
        logger.info("[ExamResearcher] 开始分析 {} 个知识片段，考试主题：{}", chunks.size(), blackboard.getQuestion());

        blackboard.advanceTo(BlackboardPhase.RESEARCH);

        String materials = buildMaterialsSummary(chunks);
        emitProgress(progressCallback, BlackboardProgressEvent.agentStartedWithMaterials(
                "exam-researcher", "正在分析 " + chunks.size() + " 个知识片段...", materials));

        if (chunks.isEmpty()) {
            blackboard.setKeyFindings("知识库中未找到与考试主题相关的内容。");
            emitProgress(progressCallback, BlackboardProgressEvent.agentCompleted(
                    "exam-researcher", "知识库中未找到相关内容。"));
            logger.warn("[ExamResearcher] 知识库无相关结果");
            return;
        }

        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            SearchResult chunk = chunks.get(i);
            contextBuilder.append("【片段 ").append(i + 1).append("】");
            if (chunk.getDocumentName() != null && !chunk.getDocumentName().isEmpty()) {
                contextBuilder.append("（来源：").append(chunk.getDocumentName());
                if (chunk.getPageNumber() != null) {
                    contextBuilder.append(" 第").append(chunk.getPageNumber()).append("页");
                }
                contextBuilder.append("，相关度：").append(String.format("%.2f", chunk.getScore()));
                contextBuilder.append("）");
            }
            contextBuilder.append("\n");
            contextBuilder.append(chunk.getText()).append("\n\n");
        }

        String userPrompt = String.format("""
                考试主题：%s

                以下是从知识库中检索到的相关片段：

                %s
                请提取和组织与考试主题相关的关键知识点，为出题提供知识基础。
                """, blackboard.getQuestion(), contextBuilder);

        ChatRequest request = ChatRequest.builder()
                .messages(
                        SystemMessage.from(SYSTEM_PROMPT),
                        UserMessage.from(userPrompt)
                )
                .build();

        ChatResponse response = chatModel.chat(request);
        String findings = response.aiMessage().text();

        if (findings == null || findings.isBlank()) {
            logger.warn("[ExamResearcher] LLM 返回空结果，回退使用原始知识片段");
            findings = buildFallbackFindings(chunks);
        }

        blackboard.setKeyFindings(findings);
        emitProgress(progressCallback, BlackboardProgressEvent.agentCompleted("exam-researcher", findings));
        logger.info("[ExamResearcher] 知识点分析完成，长度：{} 字符", findings.length());
    }

    private String buildMaterialsSummary(List<SearchResult> chunks) {
        if (chunks.isEmpty()) {
            return "（无检索结果）";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("共检索到 ").append(chunks.size()).append(" 个知识片段：\n\n");
        for (int i = 0; i < chunks.size(); i++) {
            SearchResult chunk = chunks.get(i);
            sb.append("【片段 ").append(i + 1).append("】");
            if (chunk.getDocumentName() != null && !chunk.getDocumentName().isEmpty()) {
                sb.append("来源：").append(chunk.getDocumentName());
                if (chunk.getPageNumber() != null) {
                    sb.append(" 第").append(chunk.getPageNumber()).append("页");
                }
                sb.append("，相关度：").append(String.format("%.2f", chunk.getScore()));
            }
            sb.append("\n");
            String text = chunk.getText();
            if (text != null) {
                sb.append(text.length() > 200 ? text.substring(0, 200) + "..." : text);
            }
            sb.append("\n\n");
        }
        return sb.toString();
    }

    private String buildFallbackFindings(List<SearchResult> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是从知识库中检索到的原始内容（LLM 分析失败，直接使用原文）：\n\n");
        for (int i = 0; i < chunks.size(); i++) {
            SearchResult chunk = chunks.get(i);
            sb.append("【片段 ").append(i + 1).append("】");
            if (chunk.getDocumentName() != null && !chunk.getDocumentName().isEmpty()) {
                sb.append("（来源：").append(chunk.getDocumentName());
                if (chunk.getPageNumber() != null) {
                    sb.append(" 第").append(chunk.getPageNumber()).append("页");
                }
                sb.append("）");
            }
            sb.append("\n");
            if (chunk.getText() != null) {
                sb.append(chunk.getText());
            }
            sb.append("\n\n");
        }
        return sb.toString();
    }

    private void emitProgress(BlackboardProgressCallback callback, BlackboardProgressEvent event) {
        if (callback != null) {
            callback.onProgress(event);
        }
    }

    @Override
    public String getName() {
        return "ExamResearcher";
    }
}
