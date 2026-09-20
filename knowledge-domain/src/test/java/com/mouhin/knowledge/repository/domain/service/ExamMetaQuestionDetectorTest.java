package com.mouhin.knowledge.repository.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ExamMetaQuestionDetector} 确定性检测单测。
 * <p>
 * 锁定「出处/位置类」记忆题的正负样本边界：命中序数 / 疑问 + 教材编排位置名词（单元 / 课 / 页 /
 * 章 / 节）；刻意不含「段 / 篇」以免误报阅读理解的短文段落引用。覆盖空白排版（第（ ）单元）、
 * 出自哪篇、课本第几页等变体，并验证 markdown 逐题扫描只取题号行且去重。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-20
 */
@DisplayName("出处/位置类记忆题检测 (ExamMetaQuestionDetector)")
class ExamMetaQuestionDetectorTest {

    @Test
    @DisplayName("第几单元 / 第（ ）单元 / 第五页 判为命中")
    void detectsOrdinalLocation() {
        assertTrue(ExamMetaQuestionDetector.isMetaRecall("《小小的船》是第几单元的课文？"));
        assertTrue(ExamMetaQuestionDetector.isMetaRecall("《对韵歌》是第（ ）单元的课文。"));
        assertTrue(ExamMetaQuestionDetector.isMetaRecall("这一生字出现在第5页。"));
    }

    @Test
    @DisplayName("哪一课 / 哪个单元 / 出自哪篇 判为命中")
    void detectsInterrogativeLocation() {
        assertTrue(ExamMetaQuestionDetector.isMetaRecall("《升国旗》是哪一课的课文？"));
        assertTrue(ExamMetaQuestionDetector.isMetaRecall("这首诗属于哪个单元？"));
        assertTrue(ExamMetaQuestionDetector.isMetaRecall("这段话出自哪篇课文？"));
    }

    @Test
    @DisplayName("内容实质题不误判（含 段/篇 的阅读理解引用）")
    void doesNotFlagContentQuestions() {
        assertFalse(ExamMetaQuestionDetector.isMetaRecall("下列哪个是单韵母？"));
        assertFalse(ExamMetaQuestionDetector.isMetaRecall("《静夜思》的作者是谁？"));
        assertFalse(ExamMetaQuestionDetector.isMetaRecall("短文中第三段运用了什么修辞手法？"));
        assertFalse(ExamMetaQuestionDetector.isMetaRecall("请把这篇文章分为三段并概括段意。"));
    }

    @Test
    @DisplayName("null / 空 / 纯空白安全降级为 false")
    void nullSafe() {
        assertFalse(ExamMetaQuestionDetector.isMetaRecall(null));
        assertFalse(ExamMetaQuestionDetector.isMetaRecall(""));
        assertFalse(ExamMetaQuestionDetector.isMetaRecall("   "));
    }

    @Test
    @DisplayName("扫描 markdown 仅取题号行命中并按序去重")
    void scansMarkdownQuestionLines() {
        String md = """
                # 一年级语文期末试卷

                ## 一、我会选（单选题）

                **6.** 《小小的船》是第几单元的课文？（2分）
                A. 第一单元
                B. 第六单元

                **1.** 下列哪个是单韵母？（2分）
                A. b
                B. a

                第几页这句话只是卷首说明，不带题号，不应被扫描。
                **16.** 《小小的船》是第几单元的课文？（2分）
                """;
        List<String> hits = ExamMetaQuestionDetector.scanMarkdown(md);
        assertEquals(2, hits.size(), "第6、16题命中，题号1与无题号说明行不命中");
        assertTrue(hits.get(0).contains("《小小的船》是第几单元"));
    }
}
