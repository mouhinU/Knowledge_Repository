package com.mouhin.knowledge.repository.application.executor.wronganswer;

import com.mouhin.knowledge.repository.client.dto.WrongAnswerVO;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 错题总结生成执行器（app 层用例）
 *
 * <p>基于筛选后的错题构建提示词，调用大模型生成分析报告。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
@Slf4j
public class WrongAnswerSummaryQryExe {

    /** 提示词中最多列举的错题数量 */
    private static final int PROMPT_ITEM_LIMIT = 50;

    private static final String AI_SUMMARY_SYSTEM_PROMPT =
            """
            你是一位专业的教育分析师。请根据学生的错题信息进行分析总结，帮助教师了解学生的薄弱环节。

            请从以下维度进行分析：
            1. 错误原因分析（知识性错误、理解偏差、粗心等）
            2. 知识点薄弱领域
            3. 按题型分析表现差异
            4. 针对性的改进建议和学习方向

            请用中文输出，结构清晰，重点突出。
            """;

    private final WrongAnswerListQryExe wrongAnswerListQryExe;
    private final ChatModel chatModel;

    public WrongAnswerSummaryQryExe(
            WrongAnswerListQryExe wrongAnswerListQryExe, ChatModel chatModel) {
        this.wrongAnswerListQryExe = wrongAnswerListQryExe;
        this.chatModel = chatModel;
    }

    public String execute(Long studentId, String topic, String questionType) {
        List<WrongAnswerVO> wrongAnswers =
                wrongAnswerListQryExe.execute(studentId, topic, questionType);
        if (wrongAnswers.isEmpty()) {
            return "暂无错题数据，无法生成总结。";
        }

        // 构建 AI prompt
        StringBuilder prompt = new StringBuilder();
        prompt.append("以下是学生的错题信息（共 ").append(wrongAnswers.size()).append(" 题）：\n\n");

        int limit = Math.min(wrongAnswers.size(), PROMPT_ITEM_LIMIT);
        for (int i = 0; i < limit; i++) {
            WrongAnswerVO item = wrongAnswers.get(i);
            prompt.append("--- 第 ").append(i + 1).append(" 题 ---\n");
            prompt.append("考试主题：").append(item.getTopic()).append("\n");
            prompt.append("题型：").append(item.getQuestionType()).append("\n");
            prompt.append("题目：").append(truncate(item.getQuestionContent(), 200)).append("\n");
            prompt.append("满分：").append(item.getMaxScore()).append("\n");
            prompt.append("得分：").append(item.getEffectiveScore()).append("\n");
            prompt.append("学生答案：").append(truncate(item.getStudentAnswer(), 200)).append("\n");
            prompt.append("正确答案：").append(truncate(item.getCorrectAnswer(), 200)).append("\n");
            prompt.append("AI反馈：").append(truncate(item.getAiFeedback(), 150)).append("\n\n");
        }

        if (wrongAnswers.size() > PROMPT_ITEM_LIMIT) {
            prompt.append("（还有 ")
                    .append(wrongAnswers.size() - PROMPT_ITEM_LIMIT)
                    .append(" 题省略）\n\n");
        }

        // 统计信息
        Map<String, Long> typeStats =
                wrongAnswers.stream()
                        .collect(
                                Collectors.groupingBy(
                                        m ->
                                                m.getQuestionType() != null
                                                        ? m.getQuestionType()
                                                        : "UNKNOWN",
                                        Collectors.counting()));
        prompt.append("题型分布统计：\n");
        typeStats.forEach(
                (type, count) ->
                        prompt.append("- ").append(type).append("：").append(count).append("题\n"));

        prompt.append("\n请对这些错题进行全面分析总结。");

        try {
            ChatRequest request =
                    ChatRequest.builder()
                            .messages(
                                    SystemMessage.from(AI_SUMMARY_SYSTEM_PROMPT),
                                    UserMessage.from(prompt.toString()))
                            .build();

            ChatResponse response = chatModel.chat(request);
            String output = response.aiMessage().text();

            if (output == null || output.isBlank()) {
                return "AI 返回为空，请稍后重试。";
            }

            return output;
        } catch (Exception e) {
            log.error("AI 错题总结失败", e);
            return "AI 总结生成失败：" + e.getMessage();
        }
    }

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return "（无）";
        }
        return value.length() > maxLen ? value.substring(0, maxLen) + "..." : value;
    }
}
