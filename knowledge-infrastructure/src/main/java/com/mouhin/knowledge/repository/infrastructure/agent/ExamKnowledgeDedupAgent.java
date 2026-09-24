package com.mouhin.knowledge.repository.infrastructure.agent;

import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.service.BlackboardAgent;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 考点去重 Agent（试卷编写后、答案生成前执行）
 *
 * <p>对编写 Agent 产出的试卷进行"考点级去重"：提取每题考查的知识点， 识别考查相同或高度相似考点的题目对，保留首次出现的题目，删除后续重复题，
 * 并为每个被删除的题目生成一道替补新题（同题型、同难度、同分值、不同考点）， 最终输出考点不重复的完整修订试卷。
 *
 * <p>读取：examPaper（试卷内容）、question（考试主题） 写入：examPaper（去重后的修订试卷）
 *
 * <p>特性开关：{@code exam.knowledge-dedup.enabled}（默认开启）
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Component("examKnowledgeDedupAgent")
@Slf4j
public class ExamKnowledgeDedupAgent implements BlackboardAgent {

    private static final String AGENT_NAME = "exam-kp-dedup";

    private static final String SYSTEM_PROMPT =
            """
            你是命题质检与考点去重专家。你的任务是审查一份试卷，找出考查相同或高度相似考点的题目，删除重复的，并生成替补新题。

            ## 工作步骤

            1. **提取考点**：逐题阅读，为每道题标注其考查的核心知识点（用简洁短语表示，如"勾股定理的直接应用""一元二次方程的求根公式"）。
            2. **识别重复**：比较所有题目的考点标注，找出考查相同或高度相似知识点的题目对。"高度相似"指解题所需的核心概念和思维路径基本一致，即使题目表述不同。
            3. **保留与删除**：对于每组重复题，保留最先出现的那道（按题号），标记其余为"重复题"待删除。
            4. **生成替补**：为每道被删除的重复题，生成一道替补新题。替补题必须满足：
               - 题型与原题完全相同（单选/多选/判断/填空/简答/论述）
               - 分值与原题完全相同
               - 难度与原题相当
               - 考查的知识点必须与保留的题目不同（这是核心要求）
               - 新题的考点应在原试卷知识点范围内
            5. **重组试卷**：输出完整的修订试卷，包含所有保留题和替补新题，按原格式排列，题号全局连续。

            ## 输出要求

            - 先输出一个简要的"去重报告"（用 HTML 注释包裹，不会显示给用户），格式：
              <!-- 去重报告：共检查 N 题，发现 M 组重复考点，删除 K 题并替补 K 题 -->
              <!-- 重复详情：第 X 题与第 Y 题考查相同考点"..."，保留第 X 题，替换第 Y 题 -->
            - 然后输出完整的修订试卷（Markdown 格式），结构与原试卷完全一致
            - 修订试卷必须包含试卷标题、满分、考试时间等所有原有信息
            - 如果未发现任何重复考点，直接输出原试卷内容（不做任何修改）
            """;

    private final BlackboardAgentStreamer agentStreamer;

    public ExamKnowledgeDedupAgent(BlackboardAgentStreamer agentStreamer) {
        this.agentStreamer = agentStreamer;
    }

    @Override
    public void execute(BlackboardState blackboard, BlackboardProgressCallback progressCallback) {
        String examPaper = blackboard.getExamPaper();

        log.info("[ExamKpDedup] 开始考点去重分析");

        blackboard.advanceTo(BlackboardPhase.WRITING);

        String materials =
                examPaper != null && examPaper.length() > 400
                        ? examPaper.substring(0, 400) + "..."
                        : examPaper;
        emitProgress(
                progressCallback,
                BlackboardProgressEvent.agentStartedWithMaterials(
                        AGENT_NAME, "正在检查并去除重复考点的试题...", materials));

        if (examPaper == null || examPaper.isBlank()) {
            log.warn("[ExamKpDedup] 试卷为空，跳过去重");
            emitProgress(
                    progressCallback,
                    BlackboardProgressEvent.agentCompleted(AGENT_NAME, "试卷为空，跳过去重。"));
            return;
        }

        String userPrompt =
                String.format(
                        """
                考试主题/科目：%s

                【当前试卷内容】
                %s

                请执行考点去重：提取每题考点 → 识别重复 → 删除重复题并生成替补 → 输出修订后的完整试卷。
                如果没有重复考点，直接输出原试卷。
                """,
                        blackboard.getQuestion(), examPaper);

        String revisedPaper =
                agentStreamer.stream(AGENT_NAME, SYSTEM_PROMPT, userPrompt, progressCallback);

        if (revisedPaper == null || revisedPaper.isBlank()) {
            log.warn("[ExamKpDedup] LLM 返回空结果，保留原试卷");
            emitProgress(
                    progressCallback,
                    BlackboardProgressEvent.agentCompleted(AGENT_NAME, "去重失败（LLM 返回空），保留原试卷。"));
            return;
        }

        // 提取去重报告（HTML 注释部分）
        String dedupSummary = extractDedupSummary(revisedPaper);

        // 从修订试卷中移除 HTML 注释（不写入最终试卷）
        String cleanPaper = revisedPaper.replaceAll("<!--.*?-->", "").stripLeading();
        // 清理可能留下的多余空行
        cleanPaper = cleanPaper.replaceAll("\\n{3,}", "\n\n");

        // 将去重报告写入 Blackboard，供 ExamHistory 持久化审计
        blackboard.setDeduplicationReport(dedupSummary);

        // 判断是否有实质性修改
        boolean hasChanges = detectChanges(examPaper, cleanPaper, dedupSummary);

        if (hasChanges) {
            // 加固快检：修订卷题量不能少于原卷（LLM 可能删题未补题）
            int origCount = countQuestions(examPaper);
            int revCount = countQuestions(cleanPaper);
            if (revCount < origCount) {
                log.warn(
                        "[ExamKpDedup] 修订卷题量 {} 少于原卷 {}，LLM 可能删题未补，回退原卷。{}",
                        revCount,
                        origCount,
                        dedupSummary);
                emitProgress(
                        progressCallback,
                        BlackboardProgressEvent.agentCompleted(
                                AGENT_NAME,
                                "去重检测到修订卷题量减少（"
                                        + origCount
                                        + " → "
                                        + revCount
                                        + "），为安全起见保留原卷。"
                                        + dedupSummary));
                return;
            }

            blackboard.setExamPaper(cleanPaper);
            log.info(
                    "[ExamKpDedup] 考点去重完成，修订试卷长度 {} → {} 字符，题量 {} → {}。{}",
                    examPaper.length(),
                    cleanPaper.length(),
                    origCount,
                    revCount,
                    dedupSummary);
        } else {
            log.info("[ExamKpDedup] 未发现重复考点，保留原试卷。{}", dedupSummary);
        }

        String resultText =
                hasChanges ? "考点去重完成。" + dedupSummary + "\n修订试卷已更新。" : "未发现重复考点，试卷无需修改。";
        emitProgress(
                progressCallback, BlackboardProgressEvent.agentCompleted(AGENT_NAME, resultText));
    }

    /** 从 LLM 输出中提取所有 HTML 注释里的去重报告摘要。 */
    private String extractDedupSummary(String revisedPaper) {
        StringBuilder summary = new StringBuilder();
        // 捕获所有 HTML 注释（LLM 可能输出多条：去重报告 + 重复详情）
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("<!--([\\s\\S]*?)-->").matcher(revisedPaper);
        while (m.find()) {
            String content = m.group(1).trim();
            if (!content.isEmpty()) {
                if (summary.length() > 0) {
                    summary.append(" ");
                }
                summary.append(content);
            }
        }
        return summary.length() > 0 ? summary.toString() : "去重报告未提取到";
    }

    /**
     * 检测修订试卷与原试卷是否有实质性差异。
     *
     * <p>三重检测：① 长度差异超过 5%；② 题号数量变化；③ 去重报告明确记载了替换操作。 任一条件满足即视为有修改。
     */
    private boolean detectChanges(String original, String revised, String dedupSummary) {
        if (original == null || revised == null) return true;
        int origLen = original.length();
        int revLen = revised.length();
        // 长度差异超过 5%
        if (Math.abs(revLen - origLen) > origLen * 0.05) {
            return true;
        }
        // 题号数量变化
        int origCount = countQuestions(original);
        int revCount = countQuestions(revised);
        if (origCount != revCount) {
            return true;
        }
        // 去重报告明确记载了替换/删除操作（即使长度和题量不变，说明 LLM 做了等量替换）
        if (dedupSummary != null && !dedupSummary.isEmpty()) {
            // 匹配 "删除 K 题" 或 "替换" 且 K > 0
            java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile("删除\\s*(\\d+)\\s*题").matcher(dedupSummary);
            if (m.find()) {
                try {
                    int deleted = Integer.parseInt(m.group(1));
                    if (deleted > 0) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    // ignore
                }
            }
            // 也检查 "发现 M 组重复" 且 M > 0
            java.util.regex.Matcher m2 =
                    java.util.regex.Pattern.compile("发现\\s*(\\d+)\\s*组重复").matcher(dedupSummary);
            if (m2.find()) {
                try {
                    int dupGroups = Integer.parseInt(m2.group(1));
                    if (dupGroups > 0) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    // ignore
                }
            }
        }
        return false;
    }

    /** 粗略统计试卷中的题目数量（按 "**N.**" 或换行后 "N. " 模式匹配）。 */
    private int countQuestions(String paper) {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("\\*\\*\\d+\\.\\*\\*|\\n\\d+\\.\\s").matcher(paper);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    @Override
    public String getName() {
        return "ExamKnowledgeDedup";
    }

    private void emitProgress(BlackboardProgressCallback callback, BlackboardProgressEvent event) {
        if (callback != null) {
            callback.onProgress(event);
        }
    }
}
