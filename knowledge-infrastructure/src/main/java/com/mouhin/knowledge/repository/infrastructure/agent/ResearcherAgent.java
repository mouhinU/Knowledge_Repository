package com.mouhin.knowledge.repository.infrastructure.agent;

import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.model.valueobject.SearchResult;
import com.mouhin.knowledge.repository.domain.service.BlackboardAgent;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 研究员 Agent
 * <p>
 * 负责从知识库检索结果中提取和组织关键信息。
 * 读取：knowledgeChunks（向量检索结果）
 * 写入：keyFindings（结构化的研究发现）
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-12
 */
@Component("researcherAgent")
public class ResearcherAgent implements BlackboardAgent {

    private static final Logger logger = LoggerFactory.getLogger(ResearcherAgent.class);

    private static final String SYSTEM_PROMPT = """
            你是一个知识库研究员。你的任务是根据用户的问题，从知识库检索结果中提取和组织关键信息。

            要求：
            1. 仔细阅读每个知识片段，提取与问题直接相关的信息
            2. 按主题或逻辑关系组织信息，形成结构化的研究发现
            3. 标注每条信息的来源文档名称
            4. 如果不同文档的信息有冲突或互补，请指出
            5. 如果检索结果不足以回答问题，明确指出缺失的信息方向
            6. 保持客观，不要添加自己的推测

            输出格式：使用 Markdown 格式，按主题分节组织。
            """;

    private final ChatModel chatModel;

    public ResearcherAgent(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public void execute(BlackboardState blackboard, BlackboardProgressCallback progressCallback) {
        List<SearchResult> chunks = blackboard.getKnowledgeChunks();
        logger.info("[Researcher] 开始分析 {} 个知识片段", chunks.size());

        blackboard.advanceTo(BlackboardPhase.RESEARCH);

        // 构建物料摘要
        String materials = buildMaterialsSummary(chunks);
        emitProgress(progressCallback, BlackboardProgressEvent.agentStartedWithMaterials(
                "researcher", "正在分析 " + chunks.size() + " 个知识片段...", materials));

        if (chunks.isEmpty()) {
            blackboard.setKeyFindings("知识库中未找到与问题相关的内容。");
            emitProgress(progressCallback, BlackboardProgressEvent.agentCompleted(
                    "researcher", "知识库中未找到与问题相关的内容。"));
            logger.warn("[Researcher] 知识库无相关结果");
            return;
        }

        // 构建知识上下文
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
                用户问题：%s

                以下是从知识库中检索到的相关片段：

                %s
                请提取和组织与问题相关的关键信息。
                """, blackboard.getQuestion(), contextBuilder);

        ChatRequest request = ChatRequest.builder()
                .messages(
                        SystemMessage.from(SYSTEM_PROMPT),
                        UserMessage.from(userPrompt)
                )
                .build();

        ChatResponse response = chatModel.chat(request);
        String findings = response.aiMessage().text();

        // LLM 超时或异常可能返回空结果，回退使用原始知识片段
        if (findings == null || findings.isBlank()) {
            logger.warn("[Researcher] LLM 返回空结果，回退使用原始知识片段");
            findings = buildFallbackFindings(chunks);
        }

        blackboard.setKeyFindings(findings);
        emitProgress(progressCallback, BlackboardProgressEvent.agentCompleted("researcher", findings));
        logger.info("[Researcher] 研究发现生成完成，长度：{} 字符", findings.length());
    }

    /**
     * 构建检索物料摘要（展示给前端的输入信息）
     */
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
            // 截取前 200 字符作为预览
            String text = chunk.getText();
            if (text != null) {
                sb.append(text.length() > 200 ? text.substring(0, 200) + "..." : text);
            }
            sb.append("\n\n");
        }
        return sb.toString();
    }

    /**
     * LLM 返回空结果时，直接用原始知识片段作为研究发现
     */
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
        return "Researcher";
    }
}
