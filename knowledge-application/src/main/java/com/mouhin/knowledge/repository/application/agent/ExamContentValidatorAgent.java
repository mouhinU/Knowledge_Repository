package com.mouhin.knowledge.repository.application.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 试卷内容校验 Agent
 *
 * <p>在「试卷内容渲染 Agent」之后运行，对渲染就绪的结构化题目做确定性正确性校验， 阻断明显异常的试卷开考，并对可疑但可用的情况给出提示：
 *
 * <ol>
 *   <li>题目数量必须大于 0；
 *   <li>每道题必须有非空题干；
 *   <li>单选题 / 多选题必须有至少 2 个非空选项（阻断）；
 *   <li>题号必须连续且不重复；
 *   <li>题型必须为已知枚举；
 *   <li>各题分值之和应为正（为 0 时视为配置异常，阻断）；
 *   <li>题干重复检测（warning）。
 * </ol>
 *
 * 校验不通过（存在阻断性 error）时，调用方应阻止开考。
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@Component("examContentValidatorAgent")
public class ExamContentValidatorAgent {

    private static final Logger logger = LoggerFactory.getLogger(ExamContentValidatorAgent.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Set<String> KNOWN_TYPES =
            Set.of(
                    "SINGLE_CHOICE",
                    "MULTI_CHOICE",
                    "TRUE_FALSE",
                    "FILL_BLANK",
                    "SHORT_ANSWER",
                    "ESSAY");

    /**
     * 校验结构化题目 JSON
     *
     * @param questionsJson 渲染就绪的题目 JSON
     * @return 校验结果
     */
    public PaperValidationReport validate(String questionsJson) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int totalMaxScore = 0;

        List<Map<String, Object>> questions = parse(questionsJson);

        if (questions.isEmpty()) {
            errors.add("未解析到任何题目，试卷内容为空或格式异常");
            return new PaperValidationReport(false, 0, 0, errors, warnings);
        }

        Set<String> seenContent = new HashSet<>();
        int expectedIndex = 1;

        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> q = questions.get(i);
            int displayNo = i + 1;

            // 题号连续性
            Object indexObj = q.get("index");
            int idx = toInt(indexObj, -1);
            if (idx != expectedIndex) {
                errors.add("第 " + displayNo + " 题题号异常（应为 " + expectedIndex + "，实际 " + idx + "）");
            }
            expectedIndex++;

            // 题干非空
            String content = q.get("content") == null ? "" : q.get("content").toString().trim();
            if (content.isEmpty()) {
                errors.add("第 " + displayNo + " 题题干为空");
            } else if (!seenContent.add(content)) {
                warnings.add("第 " + displayNo + " 题题干与前面题目重复");
            }

            // 题型合法性
            String type = q.get("type") == null ? "" : q.get("type").toString();
            if (type.isEmpty()) {
                errors.add("第 " + displayNo + " 题缺少题型标记");
            } else if (!KNOWN_TYPES.contains(type)) {
                warnings.add("第 " + displayNo + " 题题型未知：" + type);
            }

            // 选择题选项
            if ("SINGLE_CHOICE".equals(type) || "MULTI_CHOICE".equals(type)) {
                int optionCount = validOptionCount(q.get("options"));
                if (optionCount < 2) {
                    errors.add("第 " + displayNo + " 题（选择题）有效选项不足 2 个（实际 " + optionCount + "）");
                }
            }

            // 分值
            int maxScore = toInt(q.get("maxScore"), 0);
            if (maxScore <= 0) {
                warnings.add("第 " + displayNo + " 题分值缺失或为 0");
            }
            totalMaxScore += maxScore;
        }

        // 总分校验
        if (totalMaxScore <= 0) {
            errors.add("试卷总分为 0，分值配置异常");
        }

        boolean pass = errors.isEmpty();
        logger.info(
                "校验 Agent：共 {} 题，pass={}，errors={}，warnings={}",
                questions.size(),
                pass,
                errors.size(),
                warnings.size());
        return new PaperValidationReport(pass, questions.size(), totalMaxScore, errors, warnings);
    }

    // ==================== 内部方法 ====================

    private List<Map<String, Object>> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            logger.warn("校验 Agent：questionsJson 解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private int validOptionCount(Object rawOptions) {
        if (!(rawOptions instanceof List<?> list)) {
            return 0;
        }
        int count = 0;
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Object value = map.get("value");
                if (value != null && !value.toString().trim().isEmpty()) {
                    count++;
                }
            } else if (item != null && !item.toString().trim().isEmpty()) {
                count++;
            }
        }
        return count;
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
}
