package com.mouhin.knowledge.repository.application.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 试卷 Markdown 解析器
 * <p>
 * 将 AI 生成的 Markdown 格式试卷解析为结构化 JSON，供在线做题 UI 使用。
 * 支持的题型：单选题、多选题、判断题、填空题、简答题、论述题。
 * 能处理选项在不同行或同一行等多种 AI 输出格式。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
public final class ExamPaperParser {

    private static final Logger logger = LoggerFactory.getLogger(ExamPaperParser.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 题型标题映射 */
    private static final Map<String, String> SECTION_TYPE_MAP = new LinkedHashMap<>();

    static {
        SECTION_TYPE_MAP.put("单选题", "SINGLE_CHOICE");
        SECTION_TYPE_MAP.put("多选题", "MULTI_CHOICE");
        SECTION_TYPE_MAP.put("判断题", "TRUE_FALSE");
        SECTION_TYPE_MAP.put("填空题", "FILL_BLANK");
        SECTION_TYPE_MAP.put("简答题", "SHORT_ANSWER");
        SECTION_TYPE_MAP.put("论述题", "ESSAY");
    }

    /** 匹配大题标题 */
    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "#{1,3}\\s*[一二三四五六七八九十]+[、.．]\\s*(单选题|多选题|判断题|填空题|简答题|论述题)"
                    + "[^（(]*[（(]([^）)]+)[）)]");

    /** 匹配分值：每题5分 */
    private static final Pattern SCORE_PATTERN = Pattern.compile("每题\\s*(\\d+)\\s*分");

    /** 匹配单个选项：A. xxx 或 A、xxx 或 **A.** xxx */
    private static final Pattern SINGLE_OPTION_PATTERN = Pattern.compile(
            "([A-Da-d])\\s*[.、．]\\s*");

    /** 匹配考试时长：**考试时间：XX分钟** 或 考试时间：XX分钟 */
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "考试时间[：:]\\s*(\\d+)\\s*分钟?");

    private ExamPaperParser() {
    }

    /**
     * 从试卷 Markdown 中解析考试时长（分钟）
     *
     * @param examPaper 试卷 Markdown 内容
     * @return 时长（分钟），未找到返回 null
     */
    public static Integer parseDuration(String examPaper) {
        if (examPaper == null || examPaper.isBlank()) {
            return null;
        }
        Matcher matcher = DURATION_PATTERN.matcher(examPaper);
        if (matcher.find()) {
            int minutes = Integer.parseInt(matcher.group(1));
            if (minutes > 0 && minutes <= 600) {
                return minutes;
            }
        }
        return null;
    }

    /**
     * 解析试卷 Markdown 为结构化 JSON 字符串
     */
    public static String parseToJson(String examPaper) {
        List<Map<String, Object>> questions = parse(examPaper);
        try {
            return OBJECT_MAPPER.writeValueAsString(questions);
        } catch (JsonProcessingException e) {
            logger.error("序列化题目 JSON 失败", e);
            return "[]";
        }
    }

    /**
     * 解析试卷 Markdown 为结构化题目列表
     */
    public static List<Map<String, Object>> parse(String examPaper) {
        List<Map<String, Object>> questions = new ArrayList<>();
        if (examPaper == null || examPaper.isBlank()) {
            return questions;
        }

        String[] lines = examPaper.split("\\n");
        String currentType = null;
        int currentScore = 5;
        int globalIndex = 0;

        int i = 0;
        while (i < lines.length) {
            String line = lines[i].trim();

            // 检测大题标题
            Matcher sectionMatcher = SECTION_PATTERN.matcher(line);
            if (sectionMatcher.find()) {
                String typeName = sectionMatcher.group(1);
                currentType = SECTION_TYPE_MAP.get(typeName);
                String scoreInfo = sectionMatcher.group(2);
                Matcher scoreMatcher = SCORE_PATTERN.matcher(scoreInfo);
                if (scoreMatcher.find()) {
                    currentScore = Integer.parseInt(scoreMatcher.group(1));
                }
                i++;
                continue;
            }

            // 检测题目开始
            if (currentType != null && isQuestionStart(line)) {
                globalIndex++;
                Map<String, Object> question = new LinkedHashMap<>();
                question.put("index", globalIndex);
                question.put("type", currentType);
                question.put("maxScore", currentScore);

                // 收集题目内容行和选项行
                StringBuilder contentBuilder = new StringBuilder();
                StringBuilder rawOptionsBuilder = new StringBuilder();

                // 处理第一行：移除题号
                String firstLine = removeQuestionNumber(line);

                // 检查第一行是否内联了选项（如 "题目内容 A. xxx B. xxx"）
                String extractedContent = extractContentBeforeOptions(firstLine);
                String inlineOptions = extractInlineOptions(firstLine);

                contentBuilder.append(extractedContent);
                if (!inlineOptions.isEmpty()) {
                    rawOptionsBuilder.append(inlineOptions).append("\n");
                }

                i++;
                boolean inOptions = !inlineOptions.isEmpty();
                while (i < lines.length) {
                    String nextLine = lines[i].trim();
                    if (nextLine.isEmpty()) {
                        i++;
                        continue;
                    }
                    // 遇到下一个题目或大题标题，停止
                    if (isQuestionStart(nextLine) || SECTION_PATTERN.matcher(nextLine).find()) {
                        break;
                    }

                    // 遇到水平分隔线（--- / *** / ___），视为题目边界，停止收集
                    if (isHorizontalRule(nextLine)) {
                        i++;
                        break;
                    }

                    // 检测是否是选项行（以 A/B/C/D 开头）
                    if (startsOption(nextLine)) {
                        inOptions = true;
                        rawOptionsBuilder.append(nextLine).append("\n");
                    } else if (!inOptions) {
                        contentBuilder.append("\n").append(nextLine);
                    } else {
                        // 已经进入选项区域后又遇到非选项行，可能是子内容
                        rawOptionsBuilder.append(nextLine).append("\n");
                    }
                    i++;
                }

                question.put("content", cleanContent(contentBuilder.toString()));

                // 解析选项
                if ("SINGLE_CHOICE".equals(currentType) || "MULTI_CHOICE".equals(currentType)) {
                    List<Map<String, String>> options = parseOptions(rawOptionsBuilder.toString());
                    question.put("options", options);
                }

                questions.add(question);
                continue;
            }

            i++;
        }

        return questions;
    }

    /**
     * 判断一行是否是新题目的开始
     */
    private static boolean isQuestionStart(String line) {
        return line.matches("^\\*{0,2}\\d+[.、．]\\s*.+");
    }

    /**
     * 判断一行是否为水平分隔线（--- / *** / ___）
     */
    private static boolean isHorizontalRule(String line) {
        return line != null && line.trim().matches("^[-*_]{3,}$");
    }

    /**
     * 判断一行是否以选项标记开头
     */
    private static boolean startsOption(String line) {
        // 去掉 markdown 加粗标记后检查
        String cleaned = line.replaceAll("^\\*{0,2}", "").trim();
        return SINGLE_OPTION_PATTERN.matcher(cleaned).lookingAt();
    }

    /**
     * 移除题目行中的序号
     */
    private static String removeQuestionNumber(String line) {
        return line.replaceFirst("^\\*{0,2}\\d+[.、．]\\s*\\*{0,2}", "").trim();
    }

    /**
     * 提取题目内容中选项之前的部分
     */
    private static String extractContentBeforeOptions(String line) {
        // 查找第一个选项标记（A. B. C. D. 等）
        String cleaned = line.replaceAll("\\*{0,2}", "");
        Matcher matcher = SINGLE_OPTION_PATTERN.matcher(cleaned);
        if (matcher.find()) {
            String before = cleaned.substring(0, matcher.start()).trim();
            return before.isEmpty() ? cleaned : before;
        }
        return line;
    }

    /**
     * 提取行内选项部分（如 "A. xxx B. xxx C. xxx"）
     */
    private static String extractInlineOptions(String line) {
        String cleaned = line.replaceAll("\\*{0,2}", "");
        Matcher matcher = SINGLE_OPTION_PATTERN.matcher(cleaned);
        if (matcher.find()) {
            return cleaned.substring(matcher.start());
        }
        return "";
    }

    /**
     * 清理题目内容（移除尾部的选项标记和 markdown 格式符号）
     */
    private static String cleanContent(String content) {
        return content
                .replaceAll("\\*{1,2}", "")
                .replaceAll("\\s+$", "")
                .trim();
    }

    /**
     * 解析选项文本为选项列表
     * <p>
     * 能处理以下格式：
     * <ul>
     *     <li>每个选项一行：A. xxx\nB. xxx</li>
     *     <li>所有选项在一行：A. xxx B. xxx C. xxx D. xxx</li>
     *     <li>选项间有多个空格：A. xxx    B. xxx</li>
     * </ul>
     * </p>
     */
    private static List<Map<String, String>> parseOptions(String optionsText) {
        List<Map<String, String>> options = new ArrayList<>();
        if (optionsText == null || optionsText.isBlank()) {
            return options;
        }

        // 将所有文本合并为一行，统一分隔符
        String normalized = optionsText
                .replaceAll("\\n", " ")
                .replaceAll("\\*{1,2}", "")
                .replaceAll("\u3000", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // 使用正则匹配所有选项标记及其内容
        // 匹配模式：选项字母 + 分隔符 + 内容（直到下一个选项标记或结尾）
        Pattern optionPattern = Pattern.compile(
                "([A-Da-d])\\s*[.、．]\\s*(.*?)(?=\\s+[A-Da-d]\\s*[.、．]|$)");
        Matcher matcher = optionPattern.matcher(normalized);

        while (matcher.find()) {
            String key = matcher.group(1).toUpperCase();
            String value = matcher.group(2).trim();
            if (!value.isEmpty()) {
                Map<String, String> option = new LinkedHashMap<>();
                option.put("key", key);
                option.put("value", value);
                options.add(option);
            }
        }

        // 如果没有通过正则解析出选项，尝试按行解析
        if (options.isEmpty()) {
            String[] optLines = optionsText.split("\\n");
            for (String line : optLines) {
                String trimmed = line.trim().replaceAll("\\*{1,2}", "");
                Matcher lineMatcher = SINGLE_OPTION_PATTERN.matcher(trimmed);
                if (lineMatcher.find()) {
                    String key = lineMatcher.group(1).toUpperCase();
                    String value = trimmed.substring(lineMatcher.end()).trim();
                    if (!value.isEmpty()) {
                        Map<String, String> option = new LinkedHashMap<>();
                        option.put("key", key);
                        option.put("value", value);
                        options.add(option);
                    }
                }
            }
        }

        return options;
    }
}
