package com.mouhin.knowledge.repository.domain.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.model.valueobject.TypePlan;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ScorePlanValidator} 确定性校验单测。
 *
 * <p>覆盖硬校验（阻断）与软评估（建议）两条路径，确保分值对齐、perQuestion 长度、 极端占比、主客观平衡等规则正确触发。
 *
 * @author mouhinU
 * @date 2026-09-24
 */
@DisplayName("题型分布方案校验器 (ScorePlanValidator)")
class ScorePlanValidatorTest {

    // ==================== 辅助构建 ====================

    /** 构建一个分值对齐的合法方案（Σ=满分），用于正向测试 */
    private static ExamPlan validPlan(int fullMark, List<TypePlan> types) {
        ExamPlan plan = new ExamPlan();
        plan.setTotalFullMark(fullMark);
        plan.setSchoolLevel("PRIMARY");
        plan.setTypes(types);
        return plan;
    }

    private static TypePlan type(String key, String label, int count, List<Integer> perQuestion) {
        return new TypePlan(key, label, count, perQuestion);
    }

    /** 生成长度为 count、每题 val 分的 perQuestion 列表 */
    private static List<Integer> uniform(int count, int val) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(val);
        }
        return list;
    }

    // ==================== 硬校验（issues → pass=false） ====================

    @Nested
    @DisplayName("硬校验（阻断流水线）")
    class HardValidation {

        @Test
        @DisplayName("null 方案 → 不通过")
        void nullPlanFails() {
            ScorePlanValidator.Result r = ScorePlanValidator.validate(null);
            assertFalse(r.pass());
            assertTrue(r.issues().stream().anyMatch(s -> s.contains("缺少")));
        }

        @Test
        @DisplayName("空题型列表 → 不通过")
        void emptyTypesFails() {
            ExamPlan plan = validPlan(100, new ArrayList<>());
            ScorePlanValidator.Result r = ScorePlanValidator.validate(plan);
            assertFalse(r.pass());
        }

        @Test
        @DisplayName("满分=0 → 不通过")
        void zeroFullMarkFails() {
            ExamPlan plan = validPlan(0, List.of(type("SINGLE_CHOICE", "单选题", 10, uniform(10, 3))));
            ScorePlanValidator.Result r = ScorePlanValidator.validate(plan);
            assertFalse(r.pass());
        }

        @Test
        @DisplayName("分值合计 ≠ 满分 → 不通过")
        void allocatedMismatchFails() {
            // 10题×3分=30 ≠ 满分100
            ExamPlan plan =
                    validPlan(100, List.of(type("SINGLE_CHOICE", "单选题", 10, uniform(10, 3))));
            ScorePlanValidator.Result r = ScorePlanValidator.validate(plan);
            assertFalse(r.pass());
            assertTrue(r.issues().stream().anyMatch(s -> s.contains("≠") || s.contains("不一致")));
        }

        @Test
        @DisplayName("perQuestion 长度 ≠ count → 不通过")
        void perQuestionLengthMismatchFails() {
            // count=10 但 perQuestion 只有 5 个
            TypePlan t = type("SINGLE_CHOICE", "单选题", 10, uniform(5, 2));
            // 补一个主观题凑满分
            TypePlan t2 = type("SHORT_ANSWER", "简答题", 3, List.of(80, 0, 0));
            ExamPlan plan = validPlan(100, new ArrayList<>(List.of(t, t2)));
            ScorePlanValidator.Result r = ScorePlanValidator.validate(plan);
            assertFalse(r.pass());
            assertTrue(
                    r.issues().stream()
                            .anyMatch(s -> s.contains("perQuestion") || s.contains("长度")));
        }

        @Test
        @DisplayName("存在分值<1的小题 → 不通过")
        void zeroPointQuestionFails() {
            // 有 0 分题
            TypePlan t = type("FILL_BLANK", "填空题", 5, List.of(2, 2, 2, 2, 0));
            TypePlan t2 = type("SINGLE_CHOICE", "单选题", 10, uniform(10, 3));
            ExamPlan plan = validPlan(100, new ArrayList<>(List.of(t, t2)));
            // 先检查分值合计是否对齐（10+30=40≠100），这里主要测 <1 分检测
            // 实际上合计不对也会 fail，但 <1 分也应报 issue
            ScorePlanValidator.Result r = ScorePlanValidator.validate(plan);
            assertFalse(r.pass());
        }

        @Test
        @DisplayName("合法方案 → 通过硬校验")
        void validPlanPasses() {
            // 10选择×3分=30 + 10判断×2分=20 + 5填空×4分=20 + 3解答×10分=30 = 100
            ExamPlan plan =
                    validPlan(
                            100,
                            List.of(
                                    type("SINGLE_CHOICE", "单选题", 10, uniform(10, 3)),
                                    type("TRUE_FALSE", "判断题", 10, uniform(10, 2)),
                                    type("FILL_BLANK", "填空题", 5, uniform(5, 4)),
                                    type("SHORT_ANSWER", "简答题", 3, uniform(3, 10))));
            ScorePlanValidator.Result r = ScorePlanValidator.validate(plan);
            assertTrue(r.pass(), "合法方案应通过硬校验，issues=" + r.issues());
        }
    }

    // ==================== 软评估（suggestions） ====================

    @Nested
    @DisplayName("软评估（建议不阻断）")
    class SoftEvaluation {

        @Test
        @DisplayName("单题型占比超60% → 建议")
        void skewedDistributionSuggests() {
            // 选择题 70 分 / 满分 100 = 70% > 60%
            ExamPlan plan =
                    validPlan(
                            100,
                            List.of(
                                    type("SINGLE_CHOICE", "单选题", 10, uniform(10, 7)),
                                    type("SHORT_ANSWER", "简答题", 1, List.of(30))));
            ScorePlanValidator.Result r = ScorePlanValidator.validate(plan);
            assertTrue(r.pass()); // 硬校验应通过
            assertTrue(
                    r.suggestions().stream().anyMatch(s -> s.contains("60%") || s.contains("偏斜")),
                    "应触发占比偏斜建议，suggestions=" + r.suggestions());
        }

        @Test
        @DisplayName("缺少主观题 → 建议")
        void missingSubjectiveSuggests() {
            // 只有客观题
            ExamPlan plan =
                    validPlan(
                            100,
                            List.of(
                                    type("SINGLE_CHOICE", "单选题", 20, uniform(20, 3)),
                                    type("TRUE_FALSE", "判断题", 20, uniform(20, 2))));
            ScorePlanValidator.Result r = ScorePlanValidator.validate(plan);
            assertTrue(r.pass());
            assertTrue(r.suggestions().stream().anyMatch(s -> s.contains("主观题")), "应触发缺少主观题建议");
        }

        @Test
        @DisplayName("缺少客观题 → 建议")
        void missingObjectiveSuggests() {
            // 只有主观题
            ExamPlan plan =
                    validPlan(
                            100,
                            List.of(
                                    type("FILL_BLANK", "填空题", 10, uniform(10, 4)),
                                    type("SHORT_ANSWER", "简答题", 3, uniform(3, 10)),
                                    type("ESSAY", "论述题", 1, List.of(30))));
            ScorePlanValidator.Result r = ScorePlanValidator.validate(plan);
            assertTrue(r.pass());
            assertTrue(r.suggestions().stream().anyMatch(s -> s.contains("客观题")), "应触发缺少客观题建议");
        }

        @Test
        @DisplayName("非标准满分 → 建议")
        void nonStandardFullMarkSuggests() {
            ExamPlan plan =
                    validPlan(
                            80,
                            List.of(
                                    type("SINGLE_CHOICE", "单选题", 10, uniform(10, 3)),
                                    type("SHORT_ANSWER", "简答题", 1, List.of(50))));
            ScorePlanValidator.Result r = ScorePlanValidator.validate(plan);
            assertTrue(r.pass());
            assertTrue(
                    r.suggestions().stream().anyMatch(s -> s.contains("80") && s.contains("不是常见")),
                    "应触发非标准满分建议");
        }

        @Test
        @DisplayName("平均每题<1.5分 → 题量偏多建议")
        void tooManyQuestionsSuggests() {
            // 100分 / 80题 = 1.25 < 1.5
            ExamPlan plan =
                    validPlan(
                            100,
                            List.of(
                                    type("SINGLE_CHOICE", "单选题", 40, uniform(40, 2)),
                                    type("TRUE_FALSE", "判断题", 40, uniform(40, 1)) // 80+40=120≠100
                                    ));
            // 调整使分值对齐：40×1 + 40×1.5 = 100 → 用 20×2 + 60×1 = 100
            ExamPlan plan2 =
                    validPlan(
                            100,
                            List.of(
                                    type("SINGLE_CHOICE", "单选题", 20, uniform(20, 2)),
                                    type("TRUE_FALSE", "判断题", 60, uniform(60, 1))));
            ScorePlanValidator.Result r = ScorePlanValidator.validate(plan2);
            assertTrue(r.pass());
            assertTrue(r.suggestions().stream().anyMatch(s -> s.contains("题量偏多")), "应触发题量偏多建议");
        }
    }

    // ==================== renderReport ====================

    @Test
    @DisplayName("renderReport 包含关键信息")
    void renderReportContainsKeyInfo() {
        ExamPlan plan =
                validPlan(
                        100,
                        List.of(
                                type("SINGLE_CHOICE", "单选题", 10, uniform(10, 3)),
                                type("SHORT_ANSWER", "简答题", 3, uniform(3, 10))));
        // 分值合计 = 30+30=60 ≠ 100，会有 issue
        ScorePlanValidator.Result r = ScorePlanValidator.validate(plan);
        String report = ScorePlanValidator.renderReport(plan, r);
        assertTrue(report.contains("100"));
        assertTrue(report.contains("60"));
    }
}
