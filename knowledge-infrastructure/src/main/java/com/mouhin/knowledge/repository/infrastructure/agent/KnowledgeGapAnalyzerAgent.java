package com.mouhin.knowledge.repository.infrastructure.agent;

import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.model.valueobject.SearchResult;
import com.mouhin.knowledge.repository.domain.service.BlackboardAgent;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 知识缺口分析 Agent
 *
 * <p>分析试卷中每道题是否有知识库依据，标记"无依据生成"（可能是幻觉）的题目。 在试卷编写完成后、答案生成前运行，为后续审核提供可信度参考。
 *
 * <p>读取：examPaper（试卷内容）、knowledgeChunks（知识库片段）、question（主题） 写入：knowledgeGapReport（缺口分析报告）
 *
 * @author mouhinU
 * @date 2026-09-28
 */
@Component("knowledgeGapAnalyzerAgent")
@Slf4j
public class KnowledgeGapAnalyzerAgent implements BlackboardAgent {

    private static final String SYSTEM_PROMPT =
            """
            你是一位知识可信度审核专家，负责检查试卷中的每道题是否能从知识库中找到依据。

            任务：
            1. 逐一分析试卷中的每道题目，检查其考查的知识点是否在提供的知识片段中有明确依据
            2. 对于找不到知识库依据的题目，标记为"⚠️ 无依据"并说明理由
            3. 对于有依据的题目，标记为"✅ 有依据"并指出对应的知识片段编号
            4. 统计整体知识覆盖率（有依据题数 / 总题数）
            5. 如果覆盖率低于 70%，给出警告和补充建议

            输出格式（严格按以下 Markdown 结构）：
            ## 逐题核查
            | 题号 | 考查知识点 | 依据状态 | 对应片段 |
            |------|-----------|---------|---------|
            | 1    | ...       | ✅/⚠️   | #N / 无 |

            ## 覆盖率统计
            有依据：X / Y 题（Z%）

            ## 风险提示
            （覆盖率低于 70% 时给出警告，否则写"知识覆盖率达标，无显著幻觉风险"）

            ## 补充建议
            （如有无依据题目，建议补充哪些方面的知识；否则写"无需补充"）
            """;

    private final BlackboardAgentStreamer agentStreamer;

    public KnowledgeGapAnalyzerAgent(BlackboardAgentStreamer agentStreamer) {
        this.agentStreamer = agentStreamer;
    }

    @Override
    public void execute(BlackboardState blackboard, BlackboardProgressCallback progressCallback) {
        String examPaper = blackboard.getExamPaper();
        List<SearchResult> chunks = blackboard.getKnowledgeChunks();

        log.info("[KnowledgeGap] 开始知识缺口分析，知识片段数：{}", chunks != null ? chunks.size() : 0);

        blackboard.advanceTo(BlackboardPhase.KNOWLEDGE_GAP_ANALYZING);

        emitProgress(
                progressCallback,
                BlackboardProgressEvent.agentStartedWithMaterials(
                        "knowledge-gap-analyzer", "正在核查每道题的知识库依据...", truncate(examPaper, 400)));

        if (examPaper == null || examPaper.isBlank()) {
            blackboard.setKnowledgeGapReport("试卷为空，无法进行知识缺口分析。");
            emitProgress(
                    progressCallback,
                    BlackboardProgressEvent.agentCompleted("knowledge-gap-analyzer", "试卷为空"));
            return;
        }

        String knowledgeContext = buildKnowledgeContext(chunks);
        String userPrompt =
                String.format(
                        """
                考试主题：%s

                【知识库片段】
                %s

                【试卷内容】
                %s

                请逐题核查试卷中每道题是否有知识库依据，并输出分析报告。
                """,
                        blackboard.getQuestion(), knowledgeContext, examPaper);

        String report =
                agentStreamer.stream(
                        "knowledge-gap-analyzer", SYSTEM_PROMPT, userPrompt, progressCallback);

        if (report == null || report.isBlank()) {
            log.error("[KnowledgeGap] LLM 返回空分析结果");
            report = "## 逐题核查\n分析过程异常\n\n## 覆盖率统计\n无法评估\n\n## 风险提示\n分析失败\n\n## 补充建议\n请重试";
        }

        blackboard.setKnowledgeGapReport(report);
        Double agentScore = calculateGapScore(report);
        emitProgress(
                progressCallback,
                BlackboardProgressEvent.agentCompleted(
                        "knowledge-gap-analyzer", report, agentScore));
        log.info("[KnowledgeGap] 知识缺口分析完成，长度：{} 字符", report.length());
    }

    @Override
    public String getName() {
        return "KnowledgeGapAnalyzer";
    }

    private String buildKnowledgeContext(List<SearchResult> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "（知识库中未检索到相关内容）";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            SearchResult chunk = chunks.get(i);
            sb.append("【片段 #").append(i + 1).append("】");
            if (chunk.getDocumentName() != null) {
                sb.append("（来源：").append(chunk.getDocumentName()).append("）");
            }
            sb.append("\n").append(chunk.getText()).append("\n\n");
        }
        return sb.toString();
    }

    /** 启发式评分：结构完整性（四部分各 20 分）+ 长度（上限 20 分） */
    private Double calculateGapScore(String report) {
        double score = Math.min(20.0, report.length() / 100.0 * 2.0);
        if (report.contains("逐题核查")) score += 20.0;
        if (report.contains("覆盖率统计")) score += 20.0;
        if (report.contains("风险提示")) score += 20.0;
        if (report.contains("补充建议")) score += 20.0;
        return Math.round(Math.min(100.0, score) * 100.0) / 100.0;
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private static void emitProgress(
            BlackboardProgressCallback callback, BlackboardProgressEvent event) {
        if (callback != null) {
            callback.onProgress(event);
        }
    }
}
