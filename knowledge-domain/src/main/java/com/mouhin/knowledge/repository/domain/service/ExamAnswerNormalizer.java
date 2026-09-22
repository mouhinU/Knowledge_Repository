package com.mouhin.knowledge.repository.domain.service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 客观题标准答案规范化器（domain 层，纯逻辑无技术依赖）
 *
 * <p>AI 出卷的标准答案常把「结论 + 解释」写在同一行，例如判断题 {@code 正确。理由：……}、 单选题 {@code
 * B【解析】因为……}。若直接把这些整串当作答案，契约校验与客观题自动比对都会误判。 本工具统一负责：从答案串中剥离解释标记、只保留客观题的「答案头部」，并把判断题 / 选择题答案
 * 归一为可比对的规范形式。出卷契约校验（{@link ExamContractValidator}）与评分比对（app 层）共用本类， 避免两处判定口径分叉。
 *
 * @author mouhinU
 * @date 2026-09-18
 */
public final class ExamAnswerNormalizer {

    private ExamAnswerNormalizer() {}

    /** 解释性标记：出现在答案串中时，标记及其之后的内容视为「解析 / 理由」，不属于答案本体。 覆盖【解析】/【分析】/【解答】括号式与「解析：」「理由：」等带冒号写法。 */
    private static final Pattern EXPLANATION_MARKER =
            Pattern.compile(
                    "(?:【\\s*(?:答案)?(?:解析|分析|解答|说明|理由)\\s*】"
                            + "|(?:答案解释|参考解析|本题解析|解析|分析|解答|说明|理由|为什么)\\s*[：:])");

    /** 答案本体尾部残留的分隔 / 标点，予以剥离。 */
    private static final Pattern TRAILING_PUNCT = Pattern.compile("[\\s。.，,、；;：:（）()【】*＊]+$");

    /** 判断题「正确」同义记号（归一到 TRUE）。 */
    private static final Set<String> TRUE_TOKENS =
            Set.of("正确", "对", "是", "√", "✓", "T", "TRUE", "YES", "Y");

    /** 判断题「错误」同义记号（归一到 FALSE）。 */
    private static final Set<String> FALSE_TOKENS =
            Set.of("错误", "错", "否", "×", "✗", "✕", "X", "F", "FALSE", "NO", "N");

    /**
     * 提取答案头部：截断到第一个解释标记之前，并剥离尾随标点 / 空白。
     *
     * @param raw 原始答案串（可能含内联解释）
     * @return 仅含答案本体的串；入参为 null 时返回 null
     */
    public static String answerHead(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        Matcher m = EXPLANATION_MARKER.matcher(s);
        if (m.find()) {
            s = s.substring(0, m.start());
        }
        s = TRAILING_PUNCT.matcher(s).replaceAll("");
        return s.trim();
    }

    /**
     * 提取答案串中第一个解释标记之后的解析正文，供「结论+内联解释」整串拆分时归入解析字段。
     *
     * @param raw 原始答案串
     * @return 解释正文（已剥离标记与首尾空白）；无解释标记或标记后无正文时返回 {@code null}
     */
    public static String explanationTail(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        Matcher m = EXPLANATION_MARKER.matcher(s);
        if (!m.find()) {
            return null;
        }
        String tail = s.substring(m.end()).replaceFirst("^\\s*[：:]\\s*", "").trim();
        return tail.isEmpty() ? null : tail;
    }

    /**
     * 判断题答案归一：返回 {@code "TRUE"} / {@code "FALSE"}，无法判定返回 {@code null}。 采用「答案头部以某个记号开头」判定，兼容 {@code
     * 正确（√）}、{@code 对。} 等带尾注写法。
     *
     * @param raw 原始答案串
     * @return TRUE / FALSE / null
     */
    public static String trueFalseToken(String raw) {
        String headStr = answerHead(raw);
        if (headStr == null) {
            return null;
        }
        String head = toHalfWidth(headStr).toUpperCase().trim();
        if (head.isEmpty()) {
            return null;
        }
        for (String token : TRUE_TOKENS) {
            if (head.startsWith(token)) {
                return "TRUE";
            }
        }
        for (String token : FALSE_TOKENS) {
            if (head.startsWith(token)) {
                return "FALSE";
            }
        }
        return null;
    }

    /**
     * 选择题答案归一：仅取答案头部中的 A~Z 选项字母，按首次出现顺序去重。 单选判定「恰 1 个」、多选判定「≥ 2 个」均基于此结果。上界取 A~Z 以兼容 5 选及以上题型，
     * 且与评分侧归一口径一致；解释文字中的字母不会污染（已由 {@link #answerHead(String)} 按标记截断）。
     *
     * @param raw 原始答案串
     * @return 去重后的选项字母串（可能为空串）
     */
    public static String choiceLetters(String raw) {
        String headStr = answerHead(raw);
        if (headStr == null) {
            return "";
        }
        String head = toHalfWidth(headStr).toUpperCase();
        Set<Character> ordered = new LinkedHashSet<>();
        for (int i = 0; i < head.length(); i++) {
            char c = head.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                ordered.add(c);
            }
        }
        StringBuilder sb = new StringBuilder(ordered.size());
        for (Character c : ordered) {
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * 全角字符 → 半角（覆盖常见全角 ASCII 0xFF01-0xFF5E 与全角空格 0x3000）。
     *
     * @param s 输入串
     * @return 半角化后的串；入参为 null 时返回 null
     */
    public static String toHalfWidth(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\uFF01' && c <= '\uFF5E') {
                sb.append((char) (c - 0xFEE0));
            } else if (c == '\u3000') {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
