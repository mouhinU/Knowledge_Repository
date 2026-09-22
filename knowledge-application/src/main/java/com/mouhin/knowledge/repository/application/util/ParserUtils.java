package com.mouhin.knowledge.repository.application.util;

import java.util.regex.Pattern;

/**
 * 试题解析共享正则表达式与工具方法。
 *
 * <p>把 {@link ExamPaperParser}（试卷 Markdown 解析）与 {@code
 * ...application.agent.ExamContentRenderAgent}（LLM 输出渲染） 中重复出现的选项切分模式集中在此，避免字符串常量在两处各自维护、修复 ReDoS
 * 时容易漏改一侧。
 *
 * @author mouhinU
 * @date 2026-09-22
 */
public final class ParserUtils {

    /**
     * 行内选项切分模式：{@code A. xxx B. xxx C. xxx D. xxx}，允许中英文点号与中文顿号作为分隔。
     *
     * <p>组 1 = 选项字母（大写归一化由调用方处理），组 2 = 选项值（不含下一选项的前导空白）。 所有 {@code \s} 均使用所有格量词以避免 {@code (.*?)} 与
     * lookahead 之间的空白归属歧义引发多项式回溯。
     */
    public static final Pattern OPTION_SPLIT_PATTERN =
            Pattern.compile("([A-Da-d])\\s*+[.、．]\\s*+(.*?)(?=\\s++[A-Da-d]\\s*+[.、．]|$)");

    private ParserUtils() {}
}
