package com.mouhin.knowledge.repository.application.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 试卷 Markdown 解析器切分单测（锁定「题型分布方案 label 带括注」场景的类型识别与选项抽取）。
 *
 * <p>复现一年级语文期末卷：试卷标题「我会选（单选题）」经 {@code extractSectionName} 剥离为「我会选」， 而方案 label 为「我会选（单选题）」。回归点：二者须按
 * baseName 对齐命中 SINGLE_CHOICE， 且把内联 A/B/C/D 选项抽进 options，避免整卷塌成 SHORT_ANSWER 导致校对页「只有答案字母、没有选项可判」。
 * 另验证无方案时靠标题括注「（单选题）」的关键词回落也能识别单选。
 *
 * @author mouhinU
 * @date 2026-09-20
 */
@DisplayName("试卷切分题型/选项 (ExamPaperParser)")
class ExamPaperParserTest {

    private static final String PAPER =
            """
            # 一年级语文期末试卷

            **考试时间：60分钟**

            **满分：100分**

            ## 一、我会选（单选题）（共2题，每题2分，共4分）

            **1.** 下列哪个是单韵母？（2分）
            A. b
            B. a
            C. m
            D. f

            **2.** 下列哪个是声母？（2分）
            A. o
            B. i
            C. d
            D. ü

            ## 二、我会判（判断题）（共1题，每题2分，共2分）

            **3.** “a”是单韵母。（2分）

            ## 三、我会填（填空题）（共1题，每题3分，共3分）

            **4.** “口”有（ ）画。（3分）

            ## 四、看图写话与写句子（简答题）（共1题，每题7分，共7分）

            **5.** 看图写话：图上画着蓝天、白云、小鸟。请写一句话。（7分）
            """;

    private static final String PLAN_JSON =
            """
            {"schoolLevel":"PRIMARY","totalFullMark":100,"combined":false,"subjects":["语文"],"types":[
            {"key":"SINGLE_CHOICE","label":"我会选（单选题）","count":2,"perQuestion":[2,2],"reason":"x"},
            {"key":"TRUE_FALSE","label":"我会判（判断题）","count":1,"perQuestion":[2],"reason":"x"},
            {"key":"FILL_BLANK","label":"我会填（填空题）","count":1,"perQuestion":[3],"reason":"x"},
            {"key":"SHORT_ANSWER","label":"看图写话与写句子（简答题）","count":1,"perQuestion":[7],"reason":"x"}
            ]}""";

    private Map<Integer, Map<String, Object>> byNumber(List<Map<String, Object>> qs) {
        Map<Integer, Map<String, Object>> m = new java.util.LinkedHashMap<>();
        for (Map<String, Object> q : qs) {
            m.put(((Number) q.get("number")).intValue(), q);
        }
        return m;
    }

    @Test
    @DisplayName("带方案：我会选命中SINGLE_CHOICE且抽出4个选项")
    void resolvesChoiceTypeAndOptionsWithPlan() {
        ExamPlan plan = ExamPaperParser.readPlan(PLAN_JSON);
        assertNotNull(plan);
        Map<Integer, Map<String, Object>> qs = byNumber(ExamPaperParser.parse(PAPER, plan));

        assertEquals("SINGLE_CHOICE", qs.get(1).get("type"));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> opts = (List<Map<String, String>>) qs.get(1).get("options");
        assertNotNull(opts, "选项不应为空");
        assertEquals(4, opts.size());
        assertEquals("A", opts.get(0).get("key"));
        assertEquals("b", opts.get(0).get("value"));
        assertEquals("f", opts.get(3).get("value"));
        // 题干剥离选项与分值标记
        assertEquals("下列哪个是单韵母？", qs.get(1).get("content"));
    }

    @Test
    @DisplayName("带方案：判断/填空/看图分别命中TRUE_FALSE/FILL_BLANK/SHORT_ANSWER")
    void resolvesOtherSectionTypesWithPlan() {
        ExamPlan plan = ExamPaperParser.readPlan(PLAN_JSON);
        Map<Integer, Map<String, Object>> qs = byNumber(ExamPaperParser.parse(PAPER, plan));
        assertEquals("TRUE_FALSE", qs.get(3).get("type"));
        assertEquals("FILL_BLANK", qs.get(4).get("type"));
        assertEquals(1, qs.get(4).get("blankCount"));
        assertEquals("SHORT_ANSWER", qs.get(5).get("type"));
    }

    @Test
    @DisplayName("无方案：靠标题括注关键词回落仍识别单选/判断/填空")
    void resolvesTypesByHeadingAnnotationWithoutPlan() {
        Map<Integer, Map<String, Object>> qs = byNumber(ExamPaperParser.parse(PAPER, null));
        assertEquals("SINGLE_CHOICE", qs.get(1).get("type"));
        assertFalse(((List<?>) qs.get(1).get("options")).isEmpty(), "无方案也应抽出选项");
        assertEquals("TRUE_FALSE", qs.get(3).get("type"));
        assertEquals("FILL_BLANK", qs.get(4).get("type"));
    }

    @Test
    @DisplayName("无方案：选项值内联分值括注被剥离且行内 A/B/C/D 正确切分（ReDoS 加固回归）")
    void stripsScoreParenInsideInlineOptionValue() {
        String paper =
                """
                ## 一、我会选（单选题）（共1题，每题2分，共2分）

                **1.** 选出正确的一项。（2分）
                A. 苹果（1分） B. 香蕉 C. 橘子 D. 葡萄
                """;
        Map<Integer, Map<String, Object>> qs = byNumber(ExamPaperParser.parse(paper, null));
        assertEquals("SINGLE_CHOICE", qs.get(1).get("type"));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> opts = (List<Map<String, String>>) qs.get(1).get("options");
        assertNotNull(opts, "行内选项应被抽出");
        assertEquals(4, opts.size());
        assertEquals("A", opts.get(0).get("key"));
        assertEquals("苹果", opts.get(0).get("value"), "选项内联分值括注应被剥离");
        assertEquals("葡萄", opts.get(3).get("value"));
    }
}
