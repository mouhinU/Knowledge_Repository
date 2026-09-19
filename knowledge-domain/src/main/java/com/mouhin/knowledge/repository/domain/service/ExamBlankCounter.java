package com.mouhin.knowledge.repository.domain.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 填空空数统计器（domain 层，纯逻辑无技术依赖）。
 * <p>
 * 统一「一道题里到底有几个需要填写的空」的判定口径：连续 2 个及以上的半角下划线 {@code __}、
 * 全角下划线 {@code ＿＿}，以及空括号（中文 {@code （ ）} 或英文 {@code ( )}）各计为一个待填空。
 * 出卷切分落库（{@code kb_exam_question.blank_count}）、学生端题面 JSON（questionsJson.blankCount）
 * 与考试端渲染共用本类，杜绝「后端一套、前端另一套」导致题干与填空数对不上。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-19
 */
public final class ExamBlankCounter {

    private ExamBlankCounter() {
    }

    /**
     * 空位识别：半角 / 全角连续下划线，或中英文空括号。
     */
    private static final Pattern BLANK = Pattern.compile("_{2,}|＿{2,}|\\(\\s*\\)|（\\s*）");

    /**
     * 供前后端保持一致的空位切分正则字符串（用于把题干按空位切段、内联生成输入框）。
     */
    public static String blankSplitRegex() {
        return "(_{2,}|＿{2,}|\\(\\s*\\)|（\\s*）)";
    }

    /**
     * 统计文本中的待填空总数。
     *
     * @param text 题干 / 文本内容
     * @return 命中的空位数量；无空位返回 0，入参为空返回 0
     */
    public static int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        Matcher matcher = BLANK.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
