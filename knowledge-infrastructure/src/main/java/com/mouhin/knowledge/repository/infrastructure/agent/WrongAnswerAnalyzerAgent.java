package com.mouhin.knowledge.repository.infrastructure.agent;

import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.service.BlackboardAgent;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 错题模式分析 Agent
 *
 * <p>分析学生错题数据，识别薄弱知识点、错误模式，并生成个性化复习建议。 可用于考后复盘或自适应学习场景。
 *
 * <p>读取：examPaper（试卷内容）、answerKey（标准答案）、question（主题/考试描述） 写入：wrongAnswerAnalysis（错题分析报告）
 *
 * <p>注意：当前版本由编排层将学生答题数据拼入 blackboard.question 或 examPaper 传入， 后续可引入独立的 StudentAnswerGateway 从持久层加载。
 *
 * @author mouhinU
 * @date 2026-09-28
 */
@Component("wrongAnswerAnalyzerAgent")
@Slf4j
public class WrongAnswerAnalyzerAgent implements BlackboardAgent {

    private static final String SYSTEM_PROMPT =
            """
            你是一位教育诊断专家，擅长分析学生的错题模式并给出个性化改进建议。

            任务：
            1. 根据提供的试卷、标准答案和学生作答，识别答错的题目
            2. 对每道错题进行分类：
               - 知识盲点：完全不了解相关知识点
               - 理解偏差：对概念有误解
               - 粗心失误：知道方法但计算/审题出错
               - 能力不足：需要更高层次的思维但未能完成
            3. 归纳薄弱知识点（按出现频次排序）
            4. 分析错误模式（是否集中在某类题型、某个知识领域、某种认知层级）
            5. 给出个性化复习建议和推荐练习方向

            输出格式（严格按以下 Markdown 结构）：
            ## 错题清单
            | 题号 | 考查知识点 | 错误类型 | 学生作答摘要 | 正确答案 |

            ## 薄弱知识点
            1. [知识点] — 错误 X 次
            2. ...

            ## 错误模式分析
            （按题型/知识领域/认知层级的错误分布和规律）

            ## 个性化复习建议
            1. 重点复习：...
            2. 推荐练习：...
            3. 学习策略：...
            """;

    private final BlackboardAgentStreamer agentStreamer;

    public WrongAnswerAnalyzerAgent(BlackboardAgentStreamer agentStreamer) {
        this.agentStreamer = agentStreamer;
    }

    @Override
    public void execute(BlackboardState blackboard, BlackboardProgressCallback progressCallback) {
        String examPaper = blackboard.getExamPaper();
        String answerKey = blackboard.getAnswerKey();

        log.info("[WrongAnswerAnalyzer] 开始错题模式分析");

        blackboard.advanceTo(BlackboardPhase.WRONG_ANSWER_ANALYZING);

        emitProgress(
                progressCallback,
                BlackboardProgressEvent.agentStartedWithMaterials(
                        "wrong-answer-analyzer", "正在分析错题模式和薄弱知识点...", truncate(examPaper, 400)));

        if (examPaper == null || examPaper.isBlank()) {
            blackboard.setWrongAnswerAnalysis("试卷为空，无法进行错题分析。");
            emitProgress(
                    progressCallback,
                    BlackboardProgressEvent.agentCompleted("wrong-answer-analyzer", "试卷为空"));
            return;
        }

        String userPrompt =
                String.format(
                        """
                考试主题：%s

                【试卷内容】
                %s

                【标准答案】
                %s

                【学生作答与考试信息】
                %s

                请分析学生的错题模式，识别薄弱知识点，并给出个性化复习建议。
                """,
                        blackboard.getQuestion(),
                        examPaper,
                        answerKey != null ? answerKey : "（标准答案未提供）",
                        blackboard.getQuestion());

        String analysis =
                agentStreamer.stream(
                        "wrong-answer-analyzer", SYSTEM_PROMPT, userPrompt, progressCallback);

        if (analysis == null || analysis.isBlank()) {
            log.error("[WrongAnswerAnalyzer] LLM 返回空分析结果");
            analysis = "## 错题清单\n分析异常\n\n## 薄弱知识点\n无法评估\n\n## 错误模式分析\n分析失败\n\n## 个性化复习建议\n请重试";
        }

        blackboard.setWrongAnswerAnalysis(analysis);
        Double agentScore = calculateAnalysisScore(analysis);
        emitProgress(
                progressCallback,
                BlackboardProgressEvent.agentCompleted(
                        "wrong-answer-analyzer", analysis, agentScore));
        log.info("[WrongAnswerAnalyzer] 错题分析完成，长度：{} 字符", analysis.length());
    }

    @Override
    public String getName() {
        return "WrongAnswerAnalyzer";
    }

    /** 启发式评分：结构完整性（四部分各 20 分）+ 长度（上限 20 分） */
    private Double calculateAnalysisScore(String analysis) {
        double score = Math.min(20.0, analysis.length() / 100.0 * 2.0);
        if (analysis.contains("错题清单")) score += 20.0;
        if (analysis.contains("薄弱知识点")) score += 20.0;
        if (analysis.contains("错误模式分析")) score += 20.0;
        if (analysis.contains("复习建议")) score += 20.0;
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
