package com.mouhin.knowledge.repository.application.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mouhin.knowledge.repository.application.util.ExamPaperParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 试卷内容渲染 Agent
 * <p>
 * 在考生点击「开始考试」后运行，负责把试卷内容加工成前端可稳定渲染的结构化题目。
 * 采用确定性代码处理（非大模型调用），保证开考路径快速且可复现：
 * <ol>
 *     <li>questionsJson 缺失或为空时，回退到从试卷 Markdown 重新解析；</li>
 *     <li>为每道题补齐缺失的题号 index、题型 type、分值 maxScore；</li>
 *     <li>清理题目内容中的 Markdown 语法残留（加粗符号、尾部空白等）；</li>
 *     <li>修复选择题选项：若 options 缺失或不足 2 项，尝试从题目正文中重新抽取；</li>
 *     <li>统一选项结构为 {key, value}，丢弃空白选项。</li>
 * </ol>
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@Component("examContentRenderAgent")
public class ExamContentRenderAgent {

    private static final Logger logger = LoggerFactory.getLogger(ExamContentRenderAgent.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 默认每题分值（解析不到时使用）
     */
    private static final int DEFAULT_MAX_SCORE = 5;

    /**
     * 渲染试卷为前端可用的结构化题目 JSON
     *
     * @param examPaper     试卷 Markdown（用于回退解析与选项修复）
     * @param questionsJson 已存的题目 JSON（可能为 null / "[]"）
     * @return 渲染就绪的题目 JSON 字符串
     */
    public String render(String examPaper, String questionsJson) {
        List<Map<String, Object>> questions = parseQuestions(questionsJson);

        // 1. questionsJson 缺失或为空 → 从 Markdown 回退解析
        if (questions.isEmpty() && examPaper != null && !examPaper.isBlank()) {
            logger.info("渲染 Agent：questionsJson 为空，回退到 Markdown 解析");
            questions = ExamPaperParser.parse(examPaper);
        }

        // 2. 逐题规范化
        List<Map<String, Object>> rendered = new ArrayList<>(questions.size());
        int index = 0;
        for (Map<String, Object> raw : questions) {
            index++;
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("index", toInt(raw.get("index"), index));
            String type = asString(raw.get("type"));
            q.put("type", type);
            q.put("maxScore", toInt(raw.get("maxScore"), DEFAULT_MAX_SCORE));

            String content = cleanContent(asString(raw.get("content")));
            List<Map<String, Object>> options = normalizeOptions(raw.get("options"));

            // 选择题选项缺失 → 尝试从正文尾部抽取内联选项修复
            if (isChoice(type) && options.size() < 2) {
                OptionSplit split = extractOptionsFromContent(content);
                if (split.options().size() >= 2) {
                    content = cleanContent(split.content());
                    options = split.options();
                    logger.info("渲染 Agent：第 {} 题选项缺失，已从正文修复 {} 个选项", index, options.size());
                }
            }

            q.put("content", content);
            if (isChoice(type)) {
                q.put("options", options);
            }
            // 透传其它已有字段（如 answer 提示等），不覆盖已处理项
            raw.forEach((k, v) -> q.putIfAbsent(k, v));

            rendered.add(q);
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(rendered);
        } catch (Exception e) {
            logger.error("渲染 Agent：序列化失败，回退为 []", e);
            return "[]";
        }
    }

    // ==================== 内部方法 ====================

    private List<Map<String, Object>> parseQuestions(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            logger.warn("渲染 Agent：questionsJson 解析失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 统一选项结构为 {key, value}，过滤空白项
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeOptions(Object rawOptions) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!(rawOptions instanceof List<?> list)) {
            return result;
        }
        for (Object item : list) {
            Map<String, Object> option = new LinkedHashMap<>();
            if (item instanceof Map<?, ?> map) {
                Object keyObj = map.get("key");
                Object valueObj = map.get("value");
                // 兼容只有 value 或纯字符串项
                String key = keyObj != null ? keyObj.toString().trim().toUpperCase() : "";
                String value = valueObj != null ? cleanContent(valueObj.toString()) : "";
                if (key.isEmpty() && !value.isEmpty()) {
                    key = nextOptionKey(result.size());
                }
                if (!value.isEmpty()) {
                    option.put("key", key);
                    option.put("value", value);
                    result.add(option);
                }
            } else if (item != null) {
                String value = cleanContent(item.toString());
                if (!value.isEmpty()) {
                    option.put("key", nextOptionKey(result.size()));
                    option.put("value", value);
                    result.add(option);
                }
            }
        }
        return result;
    }

    /**
     * 从题目正文中抽取内联选项（形如 "A. xx B. xx C. xx"）
     */
    private OptionSplit extractOptionsFromContent(String content) {
        List<Map<String, Object>> options = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return new OptionSplit(content, options);
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "([A-Da-d])\\s*[.、．]\\s*(.*?)(?=\\s+[A-Da-d]\\s*[.、．]|$)");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        int firstStart = -1;
        while (matcher.find()) {
            if (firstStart < 0) {
                firstStart = matcher.start();
            }
            String key = matcher.group(1).toUpperCase();
            String value = cleanContent(matcher.group(2));
            if (!value.isEmpty()) {
                Map<String, Object> option = new LinkedHashMap<>();
                option.put("key", key);
                option.put("value", value);
                options.add(option);
            }
        }
        String head = firstStart >= 0 ? content.substring(0, firstStart) : content;
        return new OptionSplit(head, options);
    }

    private String nextOptionKey(int size) {
        return String.valueOf((char) ('A' + size));
    }

    private boolean isChoice(String type) {
        return "SINGLE_CHOICE".equals(type) || "MULTI_CHOICE".equals(type);
    }

    private String cleanContent(String content) {
        if (content == null) {
            return "";
        }
        return content
                .replaceAll("(?m)^\\s*[-*_]{3,}\\s*$", "")
                .replaceAll("\\s*[-*_]{3,}\\s*$", "")
                .replaceAll("\\*{1,2}", "")
                .replaceAll("\\s+$", "")
                .trim();
    }

    private String asString(Object o) {
        return o == null ? "" : o.toString();
    }

    private int toInt(Object o, int fallback) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * 正文与抽取出的选项的拆分结果
     */
    private record OptionSplit(String content, List<Map<String, Object>> options) {
    }
}
