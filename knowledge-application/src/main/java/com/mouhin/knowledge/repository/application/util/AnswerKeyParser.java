package com.mouhin.knowledge.repository.application.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * 标准答案与评分标准 Markdown 解析器
 *
 * <p>将试卷生成阶段产出的「标准答案与评分标准」Markdown 解析为按题号组织的答案、 解析（说明）与评分标准，供答题解析、错题本展示等场景复用。 兼容分块格式（{@code ### 第N题
 * ... **解析：** ... **评分标准：**}）与行内格式 （{@code **1. 答案：B**}）。解析不出来的题目仅缺失对应字段，不影响其它题。
 *
 * @author Knowledge-Repository
 * @date 2026-09-16
 */
@Slf4j
public final class AnswerKeyParser {

    /**
     * 题号标记（标题）：仅「以第N题开头」的标题才算一道题，如 {@code ### 第5题 …} / {@code 第 5 题}。
     *
     * <p>用 {@code ^#{0,6}\s*} 把 {@code 第N题} 锚定到标题<b>起始处</b>（可带 markdown 井号）。大题分节标题 形如 {@code ##
     * 五、看图写话（第32题，共10分）}：其以枚举序号「五、」开头、{@code 第N题} 只是括号内的
     * 题号区间/索引，并不位于起始，故不匹配、不再被误当作一道新题，从而避免全局题序错位（历史缺陷：语文卷 末题因区间/索引标题产生幻影题序而漂移到 seq=33、真实题标准答案落空）。
     */
    private static final Pattern QNUM_HEADER = Pattern.compile("^#{0,6}\\s*第\\s*(\\d{1,3})\\s*题");

    /** 题号标记（行首）：**1. 或 1. 或 1、 */
    private static final Pattern QNUM_INLINE = Pattern.compile("^\\*{0,2}(\\d{1,3})\\s*[.、．]\\s*");

    /** 答案标记 */
    private static final Pattern ANSWER_MARK =
            Pattern.compile("\\*{0,2}(?:标准答案|参考答案|正确答案|答案)\\s*[：:]\\s*(.*)$");

    /** 解析标记 */
    private static final Pattern ANALYSIS_MARK =
            Pattern.compile("\\*{0,2}(?:解析|分析|说明|理由)\\s*[：:]\\s*(.*)$");

    /** 评分标准标记 */
    private static final Pattern CRITERIA_MARK =
            Pattern.compile("\\*{0,2}(?:评分标准|给分标准|赋分标准|得分标准|评分细则)\\s*[：:]\\s*(.*)$");

    /** 通用「加粗标签」标记（如 **题目：** / **知识点：**），命中则切换到无归属状态 */
    private static final Pattern OTHER_LABEL_MARK = Pattern.compile("^\\*{1,2}[^*：:]{1,10}[：:]");

    /** 标题行（# / ## / ###） */
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s");

    /** 行首「分值」括注（含"分"字），用于从行内答案中剥离，如 （2分） / （共35分） */
    private static final Pattern INLINE_SCORE_PAREN =
            Pattern.compile("^[（(【][^）)】]*分[^）)】]*[）)】]\\s*");

    /** 行内答案前可能残留的「答案：」类标签，剥离后只留答案本体 */
    private static final Pattern ANSWER_LABEL_PREFIX =
            Pattern.compile("^(?:标准答案|参考答案|正确答案|答案)\\s*[：:]\\s*");

    /**
     * 整行仅为「引导标签」（如 {@code 示例} / {@code 参考答案} / {@code 答案}，可带冒号与 markdown 加粗星号），
     * 其后无任何答案本体。命中时应视为「答案正文在后续未标记行」，从而开启 ANSWER 收集区接收后文， 避免把题号行尾的裸标签（如 {@code
     * **32.（10分）示例：**}）当作非空行内答案而丢弃紧随其后的答案正文。
     */
    private static final Pattern BARE_LABEL_ONLY =
            Pattern.compile(
                    "^\\*{0,2}\\s*(?:示例|例题|参考答案|标准答案|正确答案|答案|解答|解析|分析|说明|理由)\\s*[：:]?\\s*\\*{0,2}$");

    private AnswerKeyParser() {}

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
        int seq = 0; // 全局题序计数器（与试卷题目 questionIndex 对齐）
        Integer currentSeq = null; // 当前正在收集的全局题序，null 表示尚未进入任何题
        Section section = Section.NONE;

        StringBuilder answerBuf = new StringBuilder();
        StringBuilder analysisBuf = new StringBuilder();
        StringBuilder criteriaBuf = new StringBuilder();

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }

            // 判断本行是否开启一道新题，并（若行内带答案）提取行内答案本体
            boolean newQuestion = false;
            String inlineAnswer = null;
            boolean answerContinues = false; // 行内题号后无答案本体 → 答案正文在后续未标记行
            boolean isHeading = HEADING.matcher(line).find();

            if (isHeading) {
                // 标题行：仅 "第N题" 形式的标题才算一道题；大题标题（一、选择题）不计
                Matcher hq = QNUM_HEADER.matcher(line);
                if (hq.find()) {
                    newQuestion = true;
                }
            } else {
                Matcher inlineQ = QNUM_INLINE.matcher(line);
                if (inlineQ.find()) {
                    newQuestion = true;
                    inlineAnswer = extractInlineAnswer(line.substring(inlineQ.end()));
                    // 行内题号之后无答案本体（如 **29.（7分）** 行为空、或 **21.（10分）答案：** 值为空）
                    // → 答案正文写在紧随其后的未标记行，开启 ANSWER 收集区接收
                    answerContinues = inlineAnswer == null || inlineAnswer.isEmpty();
                }
            }

            if (newQuestion) {
                flush(result, currentSeq, answerBuf.toString(), analysisBuf, criteriaBuf);
                currentSeq = ++seq;
                answerBuf.setLength(0);
                analysisBuf.setLength(0);
                criteriaBuf.setLength(0);
                if (inlineAnswer != null && !inlineAnswer.isEmpty()) {
                    appendSegment(answerBuf, inlineAnswer);
                    section = Section.NONE;
                } else {
                    section = answerContinues ? Section.ANSWER : Section.NONE;
                }
                continue;
            }

            // 以下均为「当前题」内的收集行

            // 答案标记行（覆盖行内答案；值为空时保持，切换收集区以接收后续正文行）
            Matcher am = ANSWER_MARK.matcher(line);
            if (am.find()) {
                String labeled = cleanInline(am.group(1));
                if (!labeled.isEmpty()) {
                    answerBuf.setLength(0);
                    appendSegment(answerBuf, labeled);
                }
                section = Section.ANSWER;
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

            // 大题标题等非题号标题行（如 "## 五、看图写话（共11分）"）：结束当前收集，
            // 避免把下一大题标题并入上一题的答案 / 解析 / 评分标准。
            if (isHeading) {
                section = Section.NONE;
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
            } else if (section == Section.ANSWER) {
                appendSegment(answerBuf, contentLine);
            }
        }

        flush(result, currentSeq, answerBuf.toString(), analysisBuf, criteriaBuf);

        log.debug("解析标准答案与评分标准：共 {} 道题（按全局题序）", result.size());
        return result;
    }

    /**
     * 从行内题号标记之后的文本里提取答案本体。
     *
     * <p>处理形如 "（2分）** B " / "（共35分）** 7；8" / "答案：B**" 的残留： 先剥离行首「分值」括注，再去掉 markdown 加粗与行尾换行反斜杠，
     * 最后剥离可能残留的「答案：」标签，返回纯答案串（可能为空）。
     */
    private static String extractInlineAnswer(String rest) {
        if (rest == null) {
            return null;
        }
        String s = rest.replaceAll("^\\*+", "").trim();
        // 剥离行首分值括注（含"分"字）
        Matcher pm = INLINE_SCORE_PAREN.matcher(s);
        if (pm.find()) {
            s = s.substring(pm.end()).trim();
        }
        // 去掉 markdown 加粗与行尾续行反斜杠
        s = s.replace("*", "").trim();
        s = s.replaceFirst("\\\\\\s*$", "").trim();
        // 剥离可能残留的「答案：」类标签
        Matcher lm = ANSWER_LABEL_PREFIX.matcher(s);
        if (lm.find()) {
            s = s.substring(lm.end()).trim();
        }
        if (s.isEmpty() || isHorizontalRule(s)) {
            return null;
        }
        // 行内仅剩「引导标签」（如「示例」「答案」）而无答案本体 → 视为空，令答案正文由后续行收集
        if (BARE_LABEL_ONLY.matcher(s).matches()) {
            return null;
        }
        return s;
    }

    private static void flush(
            Map<Integer, QuestionKey> result,
            Integer seq,
            String answer,
            StringBuilder analysis,
            StringBuilder criteria) {
        if (seq == null) {
            return;
        }
        String a = answer != null ? answer : "";
        String an = analysis.toString().trim();
        String cr = criteria.toString().trim();
        if (a.isEmpty() && an.isEmpty() && cr.isEmpty()) {
            return;
        }
        // 全局题序唯一，直接落位；如重复则以补全空缺的方式合并，不覆盖已有内容
        QuestionKey existing = result.get(seq);
        if (existing == null) {
            result.put(seq, new QuestionKey(emptyToNull(a), emptyToNull(an), emptyToNull(cr)));
        } else {
            result.put(
                    seq,
                    new QuestionKey(
                            existing.answer() != null ? existing.answer() : emptyToNull(a),
                            existing.analysis() != null ? existing.analysis() : emptyToNull(an),
                            existing.scoringCriteria() != null
                                    ? existing.scoringCriteria()
                                    : emptyToNull(cr)));
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

    /** 集合内容块 */
    private enum Section {
        NONE,
        ANSWER,
        ANALYSIS,
        CRITERIA
    }

    /**
     * 单题的答案、解析与评分标准
     *
     * @param answer 标准答案（可能为空）
     * @param analysis 解析 / 说明（可能为空）
     * @param scoringCriteria 评分标准（可能为空）
     */
    public record QuestionKey(String answer, String analysis, String scoringCriteria) {}
}
