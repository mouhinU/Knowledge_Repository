package com.mouhin.knowledge.repository.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ExamBlankCounter} 回归测试。
 *
 * <p>锁定「两种空全部相加」口径：连续下划线空（半角 {@code __}/{@code ＿＿}）与空括号 （{@code （ ）}/{@code ( )}）各自命中后累加，而非旧版「任一类先达
 * 2 个即短路返回」。 该口径同时供出卷切分落库 {@code blank_count}、学生端 questionsJson.blankCount 与考试端
 * 内联渲染共用，是本类存在的唯一目的，故重点回归混合题面（如下划线空与括号空并存）。
 *
 * @author mouhinU
 * @date 2026-09-19
 */
@DisplayName("ExamBlankCounter 填空空数统计（两空相加）")
class ExamBlankCounterTest {

    @Test
    @DisplayName("下划线空与括号空并存时全部累加（不再短路）")
    void countsBothUnderscoreAndBracket() {
        // 2 处下划线空 + 3 处中文空括号 = 5
        String text = "边长是____厘米的正方形，周长是____分米；面积是（  ）平方分米，" + "合（  ）平方厘米，周长合（  ）米。";
        assertEquals(5, ExamBlankCounter.count(text));
    }

    @Test
    @DisplayName("全角下划线连续 2 个计为一空")
    void fullWidthUnderscoreCounts() {
        assertEquals(2, ExamBlankCounter.count("３＋２＝＿＿＿，５－１＝＿＿＿"));
    }

    @Test
    @DisplayName("中英文空括号混合计数")
    void mixedBracketStylesCount() {
        // 英文 ( ) 1 个 + 中文 （ ） 2 个 = 3
        assertEquals(3, ExamBlankCounter.count("第一空(   )，第二空（   ），第三空（ ）"));
    }

    @Test
    @DisplayName("单个下划线不算空（需连续 2 个及以上）")
    void singleUnderscoreIsNotBlank() {
        assertEquals(0, ExamBlankCounter.count("a_b_c"));
    }

    @Test
    @DisplayName("括号内含内容不算空")
    void bracketWithContentIsNotBlank() {
        assertEquals(0, ExamBlankCounter.count("（答案）和(6)都不是空"));
    }

    @Test
    @DisplayName("无空位返回 0，null / 空串返回 0")
    void noBlankReturnsZero() {
        assertEquals(0, ExamBlankCounter.count("这是一句没有空的陈述句"));
        assertEquals(0, ExamBlankCounter.count(""));
        assertEquals(0, ExamBlankCounter.count(null));
    }
}
