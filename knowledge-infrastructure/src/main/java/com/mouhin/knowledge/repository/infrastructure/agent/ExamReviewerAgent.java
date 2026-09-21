package com.mouhin.knowledge.repository.infrastructure.agent;

import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.service.BlackboardAgent;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import com.mouhin.knowledge.repository.domain.service.ExamMetaQuestionDetector;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 试卷审核 Agent
 *
 * <p>审核试卷质量，从知识准确性、题目表述、知识点覆盖度、题型合理性等维度评估。
 *
 * <p>读取：examPaper（试卷内容）、answerKey（答案）、keyFindings（知识点）、question（主题）
 * 写入：examReviewFeedback（审核反馈）、qualityScore（质量评分 0-100）
 *
 * @author Knowledge-Repository
 * @date 2026-09-14
 */
@Component("examReviewerAgent")
@Slf4j
public class ExamReviewerAgent implements BlackboardAgent {

    /** 出处/位置类记忆题命中时的质量分上限（须严格低于外层流水线的通过阈值 80，以驱动打回重写）。 */
    private static final int DETECTOR_SCORE_CAP = 70;

    private static final String SYSTEM_PROMPT =
            """
            你是一位资深的教育评估专家，负责审核考试试卷的质量。

            请从以下 6 个维度审核试卷，每个维度独立打分（0-100）：
            1. 知识准确性（权重 25%%）：题目和答案是否与知识点一致，有无事实错误
            2. 题目表述（权重 15%%）：题目是否清晰无歧义，选项是否合理
            3. 知识点覆盖（权重 25%%）：是否均匀覆盖了主要知识点，有无遗漏
            4. 题型合理性（权重 15%%）：各题型的设置是否恰当，题量是否合理；发现「出处/位置类」记忆题（考查某知识点在第几单元/第几课/第几页/哪一章/出自哪篇哪一段等教材编排位置）须判为缺陷并在改进建议中逐条列出、要求替换为就内容实质（字音字形、词义语法、内容理解、阅读表达等）设问
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

    private final BlackboardAgentStreamer agentStreamer;

    public ExamReviewerAgent(BlackboardAgentStreamer agentStreamer) {
        this.agentStreamer = agentStreamer;
    }

    @Override
    public void execute(BlackboardState blackboard, BlackboardProgressCallback progressCallback) {
        String examPaper = blackboard.getExamPaper();
        String answerKey = blackboard.getAnswerKey();
        String findings = blackboard.getKeyFindings();

        log.info("[ExamReviewer] 开始审核试卷");

        blackboard.advanceTo(BlackboardPhase.REVIEWING);

        String materials = buildMaterialsPreview(examPaper, answerKey);
        emitProgress(
                progressCallback,
                BlackboardProgressEvent.agentStartedWithMaterials(
                        "exam-reviewer", "正在审核试卷质量...", materials));

        if (examPaper == null || examPaper.isBlank()) {
            blackboard.setExamReviewFeedback("试卷为空，无法审核。");
            blackboard.setQualityScore(0);
            emitProgress(
                    progressCallback,
                    BlackboardProgressEvent.agentCompleted("exam-reviewer", "试卷为空，无法审核。"));
            return;
        }

        String knowledgeContext =
                (findings != null && !findings.contains("未找到")) ? "\n\n参考知识点：\n" + findings : "";

        String userPrompt =
                String.format(
                        """
                考试主题：%s

                【试卷内容】
                %s

                【标准答案】
                %s
                %s

                请审核试卷质量。
                """,
                        blackboard.getQuestion(), examPaper, answerKey, knowledgeContext);

        String reviewOutput =
                agentStreamer.stream("exam-reviewer", SYSTEM_PROMPT, userPrompt, progressCallback);

        if (reviewOutput == null || reviewOutput.isBlank()) {
            log.error("[ExamReviewer] LLM 返回空审核结果");
            reviewOutput =
                    "## 审核意见\n审核过程异常，请重试。\n\n## 评分明细\n- 知识准确性：0\n- 题目表述：0\n- 知识点覆盖：0\n- 题型合理性：0\n- 难度适当性：0\n- 格式规范性：0\n\n## 质量评分\n0\n\n## 改进建议\n无";
        }

        blackboard.setExamReviewFeedback(reviewOutput);
        blackboard.setQualityScore(extractScore(reviewOutput));
        blackboard.setExamScoreDetail(
                buildScoreDetailJson(reviewOutput, blackboard.getQualityScore()));

        // —— 确定性后处理：出处/位置类记忆题扫描（不依赖大模型的硬兜底）——
        applyDeterministicMetaRecallCheck(blackboard, examPaper);

        emitProgress(
                progressCallback,
                BlackboardProgressEvent.agentCompleted(
                        "exam-reviewer", blackboard.getExamReviewFeedback()));
        log.info("[ExamReviewer] 审核完成，评分：{}", blackboard.getQualityScore());
    }

    /**
     * 出处/位置类记忆题的确定性复核：命中即把分数压到 {@link #DETECTOR_SCORE_CAP}（低于通过阈值）， 驱动外层流水线的 writer
     * 打回重写，并把命中题目与改写要求追加进审核反馈。已有更低分则不抬升。
     *
     * @param blackboard 黑板状态
     * @param examPaper 试卷 Markdown（LLM 刚产出的正文）
     */
    private void applyDeterministicMetaRecallCheck(BlackboardState blackboard, String examPaper) {
        List<String> metaHits = ExamMetaQuestionDetector.scanMarkdown(examPaper);
        if (metaHits.isEmpty()) {
            return;
        }
        String feedback =
                blackboard.getExamReviewFeedback() == null
                        ? ""
                        : blackboard.getExamReviewFeedback();
        StringBuilder block = new StringBuilder();
        block.append("\n\n## ⚠️ 确定性复核：出处/位置类记忆题（必须修正）\n");
        block.append("检测到 ")
                .append(metaHits.size())
                .append(" 道只考查教材编排位置（第几单元/第几课/第几页/哪一章/出自哪篇）的记忆题，")
                .append("不符合「考查内容理解与运用」的命题要求，请逐一改写为就知识实质设问：\n");
        for (String hit : metaHits) {
            block.append("- ").append(hit).append('\n');
        }
        blackboard.setExamReviewFeedback(feedback + block);

        int current = blackboard.getQualityScore();
        if (current > DETECTOR_SCORE_CAP) {
            blackboard.setQualityScore(DETECTOR_SCORE_CAP);
        }
        log.warn(
                "[ExamReviewer] 确定性复核命中 {} 道出处/位置类题目，质量分 {}→{}（触发打回重写）",
                metaHits.size(),
                current,
                blackboard.getQualityScore());
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

    /** 六维度权重，顺序：知识准确性 / 题目表述 / 知识点覆盖 / 题型合理性 / 难度适当性 / 格式规范性 */
    private static final double[] WEIGHTS = {0.25, 0.15, 0.25, 0.15, 0.10, 0.10};

    /** 六维度名称，顺序与 WEIGHTS 一致 */
    private static final String[] DIMENSIONS = {
        "知识准确性", "题目表述", "知识点覆盖", "题型合理性", "难度适当性", "格式规范性"
    };

    private int extractScore(String reviewOutput) {
        // javabugs:S2259：LLM 返回 null / 空白时，后续 indexOf / substring 会 NPE；
        //   回退 0 分并告警，交由上层按质量分不足处理（比崩溃更易恢复）。
        if (reviewOutput == null) {
            log.warn("[ExamReviewer] reviewOutput 为空，回退质量分 0");
            return 0;
        }
        try {
            int[] dims = parseDimensionScores(reviewOutput);
            if (dims != null) {
                double weighted = 0;
                for (int i = 0; i < dims.length; i++) {
                    weighted += dims[i] * WEIGHTS[i];
                }
                int result = (int) Math.min(100, Math.max(0, Math.round(weighted)));
                log.info(
                        "[ExamReviewer] 维度评分：准确性={}, 表述={}, 覆盖={}, 题型={}, 难度={}, 格式={}, 加权总分={}",
                        dims[0],
                        dims[1],
                        dims[2],
                        dims[3],
                        dims[4],
                        dims[5],
                        result);
                return result;
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
            log.warn("[ExamReviewer] 评分解析异常: {}", e.getMessage());
        }
        return 70;
    }

    /**
     * 解析"评分明细"中的六个维度分数。
     *
     * @return 长度为 6 的分数数组（顺序与 {@link #DIMENSIONS} 一致）；任一维度缺失则返回 {@code null}
     */
    private int[] parseDimensionScores(String reviewOutput) {
        if (reviewOutput == null) {
            return null;
        }
        int detailIdx = reviewOutput.indexOf("## 评分明细");
        if (detailIdx < 0) {
            return null;
        }
        String detailSection = reviewOutput.substring(detailIdx);
        int nextSection = detailSection.indexOf("\n##", 2);
        if (nextSection > 0) {
            detailSection = detailSection.substring(0, nextSection);
        }
        int[] dims = new int[DIMENSIONS.length];
        for (int i = 0; i < DIMENSIONS.length; i++) {
            dims[i] = extractDimensionScore(detailSection, DIMENSIONS[i]);
            if (dims[i] < 0) {
                return null;
            }
        }
        return dims;
    }

    /**
     * 构造六维度评分明细 JSON（键：accuracy/wording/coverage/typeReasonable/difficulty/format + total）。
     *
     * @return JSON 字符串；无法解析全部维度时返回 {@code null}
     */
    private String buildScoreDetailJson(String reviewOutput, int total) {
        int[] dims = parseDimensionScores(reviewOutput);
        if (dims == null) {
            return null;
        }
        return String.format(
                "{\"accuracy\":%d,\"wording\":%d,\"coverage\":%d,\"typeReasonable\":%d,\"difficulty\":%d,\"format\":%d,\"total\":%d}",
                dims[0], dims[1], dims[2], dims[3], dims[4], dims[5], total);
    }

    /**
     * 从评分明细段落中提取指定维度的分数
     *
     * @param section 评分明细文本
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
