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
 * 写手 Agent
 *
 * <p>负责根据研究员的发现撰写文章草稿。 读取：question + keyFindings 写入：draftArticle
 *
 * @author Knowledge-Repository
 * @date 2026-09-12
 */
@Component("writerAgent")
public class WriterAgent implements BlackboardAgent {

    private static final Logger logger = LoggerFactory.getLogger(WriterAgent.class);

    private static final String SYSTEM_PROMPT =
            """
            你是一个专业的技术文章写手。你的任务是根据研究员提供的知识发现，撰写一篇结构清晰、内容准确的文章。

            写作要求：
            1. 文章结构：标题 → 引言 → 正文（分章节）→ 总结
            2. 基于提供的知识片段撰写，不要编造不存在的信息
            3. 使用 Markdown 格式，合理使用标题层级、列表、代码块
            4. 语言流畅专业，适合技术人员阅读
            5. 在引用具体知识点时，标注来源文档
            6. 如果某些方面信息不足，在文章末尾注明"待补充"
            7. 文章长度适中（800-2000字），根据问题复杂度调整
            """;

    private final BlackboardAgentStreamer agentStreamer;

    public WriterAgent(BlackboardAgentStreamer agentStreamer) {
        this.agentStreamer = agentStreamer;
    }

    @Override
    public void execute(BlackboardState blackboard, BlackboardProgressCallback progressCallback) {
        logger.info("[Writer] 开始撰写文章");

        blackboard.advanceTo(BlackboardPhase.WRITING);

        String findings = blackboard.getKeyFindings();
        String materials =
                (findings != null && !findings.isBlank())
                        ? "研究员的关键发现：\n\n"
                                + (findings.length() > 500
                                        ? findings.substring(0, 500) + "..."
                                        : findings)
                        : "（无研究发现）";
        emitProgress(
                progressCallback,
                BlackboardProgressEvent.agentStartedWithMaterials(
                        "writer", "正在撰写文章...", materials));

        if (findings == null
                || findings.isBlank()
                || findings.contains("未找到")
                || findings.contains("不足以回答")) {
            String fallback =
                    "抱歉，知识库中没有足够的信息来生成文章。\n\n建议：\n1. 确认相关文档已上传并完成索引\n2. 尝试使用不同的关键词重新提问\n3. 检查知识库中是否包含与问题相关的内容";
            blackboard.setDraftArticle(fallback);
            emitProgress(
                    progressCallback, BlackboardProgressEvent.agentCompleted("writer", fallback));
            logger.warn("[Writer] 无有效研究发现，跳过 LLM 调用");
            return;
        }

        String userPrompt =
                String.format(
                        """
                用户的问题：%s

                研究员的分析结果：

                %s

                请根据以上信息撰写一篇文章来回答用户的问题。
                """,
                        blackboard.getQuestion(), findings);

        String draft = agentStreamer.stream("writer", SYSTEM_PROMPT, userPrompt, progressCallback);

        blackboard.setDraftArticle(draft);
        emitProgress(progressCallback, BlackboardProgressEvent.agentCompleted("writer", draft));
        logger.info("[Writer] 文章草稿完成，长度：{} 字符", draft.length());
    }

    private void emitProgress(BlackboardProgressCallback callback, BlackboardProgressEvent event) {
        if (callback != null) {
            callback.onProgress(event);
        }
    }

    @Override
    public String getName() {
        return "Writer";
    }
}
