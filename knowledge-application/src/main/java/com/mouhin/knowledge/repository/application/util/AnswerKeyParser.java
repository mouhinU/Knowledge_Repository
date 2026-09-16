package com.mouhin.knowledge.repository.application.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标准答案与评分标准 Markdown 解析器
 * <p>
 * 将试卷生成阶段产出的「标准答案与评分标准」Markdown 解析为按题号组织的答案、
 * 解析（说明）与评分标准，供答题解析、错题本展示等场景复用。
 * 兼容分块格式（{@code ### 第N题 ... **解析：** ... **评分标准：**}）与行内格式
 * （{@code **1. 答案：B**}）。解析不出来的题目仅缺失对应字段，不影响其它题。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-16
 */
public final class AnswerKeyParser {

    private static final Logger logger = LoggerFactory.getLogger(AnswerKeyParser.class);

    /**
     * 题号标记（标题）：第1题 / 第 1 题
     */
    private static final Pattern QNUM_HEADER = Pattern.compile("第\\s*(\\d{1,3})\\s*题");

    /**
     * 题号标记（行首）：**1. 或 1. 或 1、
     */
    private static final Pattern QNUM_INLINE = Pattern.compile("^\\*{0,2}(\\d{1,3})\\s*[.、．]\\s*");

    /**
     * 答案标记
     */
    private static final Pattern ANSWER_MARK = Pattern.compile(
            "\\*{0,2}(?:标准答案|参考答案|正确答案|答案)\\s*[：:]\\s*(.*)$");

    /**
     * 解析标记
     */
    private static final Pattern ANALYSIS_MARK = Pattern.compile(
            "\\*{0,2}(?:解析|分析|说明)\\s*[：:]\\s*(.*)$");

    /**
     * 评分标准标记
     */
    private static final Pattern CRITERIA_MARK = Pattern.compile(
            "\\*{0,2}(?:评分标准|给分标准|赋分标准|得分标准|评分细则)\\s*[：:]\\s*(.*)$");

    /**
     * 通用「加粗标签」标记（如 **题目：** / **知识点：**），命中则切换到无归属状态
     */
    private static final Pattern OTHER_LABEL_MARK = Pattern.compile("^\\*{1,2}[^*：:]{1,10}[：:]");

    /**
     * 标题行（# / ## / ###）
     */
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s");

    private AnswerKeyParser() {
    }

    /**
     * 解析标准答案 Markdown，返回题号 → 答案/解析/评分标准。
     *
     * @param answerKey 「标准答案与评分标准」Markdown
     * @return 题号到 {@link QuestionKey} 的映射，输入为空时返回空 Map
     */
    public static Map<Integer, QuestionKey> parse(String answerKey) {
        Map<Integer, QuestionKey> result = new HashMap<>();
        if (answerKey == null || answerKey.isBlank()) {
            return result;
        }

        String[] lines = answerKey.split("\\n");
        Integer currentQ = null;
        Section section = Section.NONE;

        StringBuilder analysisBuf = new StringBuilder();
        StringBuilder criteriaBuf = new StringBuilder();
        String answerValue = null;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }

            // 标题行：重新定位题号并结束当前收集
            if (HEADING.matcher(line).find()) {
                Matcher hq = QNUM_HEADER.matcher(line);
                if (hq.find()) {
                    flush(result, currentQ, answerValue, analysisBuf, criteriaBuf);
                    currentQ = Integer.parseInt(hq.group(1));
                    answerValue = null;
                    analysisBuf.setLength(0);
                    criteriaBuf.setLength(0);
                }
                section = Section.NONE;
                continue;
            }

            // 行内题号（**1. ...**）：更新 currentQ
            Matcher inlineQ = QNUM_INLINE.matcher(line);
            if (inlineQ.find()) {
                int detected = Integer.parseInt(inlineQ.group(1));
                if (detected != (currentQ == null ? -1 : currentQ)) {
                    flush(result, currentQ, answerValue, analysisBuf, criteriaBuf);
                    currentQ = detected;
                    answerValue = null;
                    analysisBuf.setLength(0);
                    criteriaBuf.setLength(0);
                }
            }

            // 答案标记行
            Matcher am = ANSWER_MARK.matcher(line);
            if (am.find()) {
                section = Section.ANSWER;
                answerValue = cleanInline(am.group(1));
                continue;
            }
            // 解析标记行
            Matcher ym = ANALYSIS_MARK.matcher(line);
            if (ym.find()) {
                section = Section.ANALYSIS;
                String inline = cleanInline(ym.group(1));
                if (!inline.isEmpty()) {
                    appendSegment(analysisBuf, inline);
                }
                continue;
            }
            // 评分标准标记行
            Matcher cm = CRITERIA_MARK.matcher(line);
            if (cm.find()) {
                section = Section.CRITERIA;
                String inline = cleanInline(cm.group(1));
                if (!inline.isEmpty()) {
                    appendSegment(criteriaBuf, inline);
                }
                continue;
            }

            // 其它加粗标签行（**题目：** / **知识点：** 等）：结束当前收集
            if (OTHER_LABEL_MARK.matcher(line).find()) {
                section = Section.NONE;
                continue;
            }

            // 表格行 / 水平分隔线：结束当前收集，避免污染
            if (line.startsWith("|") || isHorizontalRule(line)) {
                section = Section.NONE;
                continue;
            }

            // 普通内容行：归入当前收集区
            String contentLine = stripBullet(line);
            if (section == Section.ANALYSIS) {
                appendSegment(analysisBuf, contentLine);
            } else if (section == Section.CRITERIA) {
                appendSegment(criteriaBuf, contentLine);
            }
        }

        flush(result, currentQ, answerValue, analysisBuf, criteriaBuf);

        logger.debug("解析标准答案与评分标准：共 {} 道题", result.size());
        return result;
    }

    private static void flush(Map<Integer, QuestionKey> result, Integer q, String answer,
                              StringBuilder analysis, StringBuilder criteria) {
        if (q == null) {
            return;
        }
        String a = answer != null ? answer : "";
        String an = analysis.toString().trim();
        String cr = criteria.toString().trim();
        if (a.isEmpty() && an.isEmpty() && cr.isEmpty()) {
            return;
        }
        // 已存在则补全空缺字段，不覆盖已有内容
        QuestionKey existing = result.get(q);
        if (existing == null) {
            result.put(q, new QuestionKey(emptyToNull(a), emptyToNull(an), emptyToNull(cr)));
        } else {
            result.put(q, new QuestionKey(
                    existing.answer() != null ? existing.answer() : emptyToNull(a),
                    existing.analysis() != null ? existing.analysis() : emptyToNull(an),
                    existing.scoringCriteria() != null ? existing.scoringCriteria() : emptyToNull(cr)));
        }
    }

    private static void appendSegment(StringBuilder buf, String segment) {
        if (segment == null || segment.isEmpty()) {
            return;
        }
        if (buf.length() > 0) {
            buf.append("\n");
        }
        buf.append(segment);
    }

    private static String cleanInline(String value) {
        if (value == null) {
            return "";
        }
        String v = value.replaceAll("\\*+", "").trim();
        return isHorizontalRule(v) ? "" : v;
    }

    private static String stripBullet(String line) {
        String v = line.replaceAll("\\*+", "").trim();
        if (v.startsWith("- ") || v.startsWith("* ") || v.startsWith("· ")) {
            v = v.substring(2).trim();
        }
        return v;
    }

    private static boolean isHorizontalRule(String line) {
        return line != null && line.trim().matches("^[-*_]{3,}$");
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    /**
     * 便捷方法：仅取每道题的标准答案（题号 → 答案），兼容旧调用。
     *
     * @param answerKey 标准答案 Markdown
     * @return 题号到答案的映射
     */
    public static Map<Integer, String> parseAnswers(String answerKey) {
        Map<Integer, String> answers = new HashMap<>();
        for (Map.Entry<Integer, QuestionKey> e : parse(answerKey).entrySet()) {
            if (e.getValue().answer() != null && !e.getValue().answer().isBlank()) {
                answers.put(e.getKey(), e.getValue().answer());
            }
        }
        return answers;
    }

    /**
     * 集合内容块
     */
    private enum Section {
        NONE, ANSWER, ANALYSIS, CRITERIA
    }

    /**
     * 单题的答案、解析与评分标准
     *
     * @param answer          标准答案（可能为空）
     * @param analysis        解析 / 说明（可能为空）
     * @param scoringCriteria 评分标准（可能为空）
     */
    public record QuestionKey(String answer, String analysis, String scoringCriteria) {
    }
}
