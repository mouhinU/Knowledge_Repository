package com.mouhin.knowledge.repository.application.executor.examgeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 分布方案评估收敛逻辑单测。
 *
 * <p>验证结构化评估的收敛机制：extractFailingDimensions 维度提取、 hasEvaluationSuggestions 基于 overallVerdict
 * 的判定、以及"连续两轮 FAIL 维度相同→已收敛"的循环终止策略。
 *
 * @author mouhinU
 * @date 2026-09-24
 */
@DisplayName("分布方案评估收敛逻辑 (ExamDistributionConvergence)")
class ExamDistributionConvergenceTest {

    // ==================== 辅助构建 ====================

    private static ExamPlan planWithVerdict(String verdict, List<String> notes) {
        ExamPlan plan = new ExamPlan();
        plan.setTotalFullMark(100);
        plan.setOverallVerdict(verdict);
        plan.setEvaluationNotes(notes != null ? notes : new ArrayList<>());
        return plan;
    }

    /** 通过反射调用 private static extractFailingDimensions */
    @SuppressWarnings("unchecked")
    private static List<String> invokeExtractFailing(ExamPlan plan) throws Exception {
        Method m =
                ExamGenerationSupport.class.getDeclaredMethod(
                        "extractFailingDimensions", ExamPlan.class);
        m.setAccessible(true);
        return (List<String>) m.invoke(null, plan);
    }

    /** 通过反射调用 private static hasEvaluationSuggestions */
    private static boolean invokeHasSuggestions(ExamPlan plan) throws Exception {
        Method m =
                ExamGenerationSupport.class.getDeclaredMethod(
                        "hasEvaluationSuggestions", ExamPlan.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, plan);
    }

    // ==================== extractFailingDimensions ====================

    @Nested
    @DisplayName("extractFailingDimensions · 维度提取")
    class ExtractFailing {

        @Test
        @DisplayName("空 notes → 空列表")
        void emptyNotesReturnsEmpty() throws Exception {
            ExamPlan plan = planWithVerdict("FAIL", new ArrayList<>());
            assertTrue(invokeExtractFailing(plan).isEmpty());
        }

        @Test
        @DisplayName("null notes → 空列表")
        void nullNotesReturnsEmpty() throws Exception {
            ExamPlan plan = planWithVerdict("FAIL", null);
            assertTrue(invokeExtractFailing(plan).isEmpty());
        }

        @Test
        @DisplayName("带冒号的维度备注 → 提取维度名并排序")
        void extractsDimensionNamesSorted() throws Exception {
            ExamPlan plan =
                    planWithVerdict(
                            "FAIL",
                            List.of("总题量：35道超出一年级上限30", "题型占比：选择题占50%超过45%", "单题分值：每题3分偏高"));
            List<String> failing = invokeExtractFailing(plan);
            assertEquals(3, failing.size());
            // 排序后：单题分值 < 总题量 < 题型占比
            assertEquals("单题分值", failing.get(0));
            assertEquals("总题量", failing.get(1));
            assertEquals("题型占比", failing.get(2));
        }

        @Test
        @DisplayName("无冒号的备注 → 跳过")
        void notesWithoutColonSkipped() throws Exception {
            ExamPlan plan = planWithVerdict("FAIL", List.of("总题量：35道超出上限", "建议减少题量（无冒号前缀）"));
            List<String> failing = invokeExtractFailing(plan);
            assertEquals(1, failing.size());
            assertEquals("总题量", failing.get(0));
        }

        @Test
        @DisplayName("PASS 方案（notes 只有'合理'占位）→ 空列表")
        void passPlanReturnsEmpty() throws Exception {
            ExamPlan plan = planWithVerdict("PASS", List.of("题型分布合理，可直接出题"));
            List<String> failing = invokeExtractFailing(plan);
            // "题型分布合理，可直接出题" 不含冒号 → 跳过
            assertTrue(failing.isEmpty());
        }
    }

    // ==================== hasEvaluationSuggestions ====================

    @Nested
    @DisplayName("hasEvaluationSuggestions · overallVerdict 判定")
    class HasSuggestions {

        @Test
        @DisplayName("overallVerdict=PASS → false（无需改进）")
        void passVerdictReturnsFalse() throws Exception {
            ExamPlan plan = planWithVerdict("PASS", List.of("题型分布合理，可直接出题"));
            assertFalse(invokeHasSuggestions(plan));
        }

        @Test
        @DisplayName("overallVerdict=FAIL → true（需要改进）")
        void failVerdictReturnsTrue() throws Exception {
            ExamPlan plan = planWithVerdict("FAIL", List.of("总题量：35道超出上限30"));
            assertTrue(invokeHasSuggestions(plan));
        }

        @Test
        @DisplayName("overallVerdict=null + 合理占位 → false（回退兼容）")
        void nullVerdictWithPlaceholderReturnsFalse() throws Exception {
            ExamPlan plan = planWithVerdict(null, List.of("题型分布合理，可直接出题"));
            assertFalse(invokeHasSuggestions(plan));
        }

        @Test
        @DisplayName("overallVerdict=null + 有建议 → true（回退兼容）")
        void nullVerdictWithSuggestionsReturnsTrue() throws Exception {
            ExamPlan plan = planWithVerdict(null, List.of("选择题偏多，建议减少"));
            assertTrue(invokeHasSuggestions(plan));
        }

        @Test
        @DisplayName("overallVerdict=null + 空 notes → false")
        void nullVerdictEmptyNotesReturnsFalse() throws Exception {
            ExamPlan plan = planWithVerdict(null, new ArrayList<>());
            assertFalse(invokeHasSuggestions(plan));
        }
    }

    // ==================== 收敛策略模拟 ====================

    @Nested
    @DisplayName("收敛策略 · 循环终止条件")
    class ConvergenceSimulation {

        /**
         * 模拟 generateDistributionWithLoop 的收敛判定逻辑。
         *
         * <p>返回实际执行的轮次数。
         */
        private int simulateLoop(List<ExamPlan> roundPlans, int maxRounds) {
            List<String> prevFailing = null;
            for (int round = 1; round <= maxRounds; round++) {
                ExamPlan plan = roundPlans.get(round - 1);
                boolean evalPass = "PASS".equalsIgnoreCase(plan.getOverallVerdict());

                if (evalPass) {
                    return round; // 接受
                }

                // 提取 FAIL 维度
                List<String> currentFailing = new ArrayList<>();
                for (String note : plan.getEvaluationNotes()) {
                    int idx = note.indexOf('：');
                    if (idx > 0) {
                        currentFailing.add(note.substring(0, idx).trim());
                    }
                }
                currentFailing.sort(String::compareTo);

                // 连续两轮 FAIL 维度相同 → 收敛
                if (prevFailing != null
                        && !prevFailing.isEmpty()
                        && prevFailing.equals(currentFailing)) {
                    return round; // 收敛停止
                }

                prevFailing = currentFailing;
            }
            return maxRounds; // 达最大轮次
        }

        @Test
        @DisplayName("第1轮 PASS → 1轮接受")
        void firstRoundPass() {
            List<ExamPlan> plans = List.of(planWithVerdict("PASS", List.of("题型分布合理，可直接出题")));
            assertEquals(1, simulateLoop(plans, 5));
        }

        @Test
        @DisplayName("第1轮 FAIL → 第2轮 PASS → 2轮接受")
        void secondRoundPass() {
            List<ExamPlan> plans =
                    List.of(
                            planWithVerdict("FAIL", List.of("总题量：35道超出上限30")),
                            planWithVerdict("PASS", List.of("题型分布合理，可直接出题")));
            assertEquals(2, simulateLoop(plans, 5));
        }

        @Test
        @DisplayName("连续两轮 FAIL 维度相同 → 收敛停止（不振荡）")
        void sameFailingDimensionsConverge() {
            List<ExamPlan> plans =
                    List.of(
                            planWithVerdict("FAIL", List.of("总题量：35道超出上限30", "题型占比：选择题占50%")),
                            // 第2轮调整了具体数值，但 FAIL 维度名相同
                            planWithVerdict(
                                    "FAIL", List.of("总题量：32道仍超出上限30", "题型占比：选择题占48%仍超45%")));
            assertEquals(2, simulateLoop(plans, 5));
        }

        @Test
        @DisplayName("连续两轮 FAIL 维度不同 → 继续改进")
        void differentFailingDimensionsContinue() {
            List<ExamPlan> plans =
                    List.of(
                            planWithVerdict("FAIL", List.of("总题量：35道超出上限30")),
                            // 总题量修好了，但新问题出现
                            planWithVerdict("FAIL", List.of("题型占比：选择题占50%超45%")),
                            // 最终通过
                            planWithVerdict("PASS", List.of("题型分布合理，可直接出题")));
            assertEquals(3, simulateLoop(plans, 5));
        }

        @Test
        @DisplayName("达到最大轮次仍 FAIL → 接受当前方案")
        void maxRoundsReached() {
            List<ExamPlan> plans =
                    List.of(
                            planWithVerdict("FAIL", List.of("总题量：35道超出上限")),
                            planWithVerdict("FAIL", List.of("题型占比：选择题占50%")),
                            planWithVerdict("FAIL", List.of("单题分值：每题5分偏高")));
            assertEquals(3, simulateLoop(plans, 3));
        }

        @Test
        @DisplayName("振荡场景：A→B→A 维度 → 第3轮因 B≠A 继续，直到 maxRounds")
        void oscillationScenario() {
            // 模拟用户遇到的振荡：第1轮说选择题多，第2轮说判断多，第3轮又说选择题多
            List<ExamPlan> plans =
                    List.of(
                            planWithVerdict("FAIL", List.of("总题量：选择题12道偏多")),
                            planWithVerdict("FAIL", List.of("客观主观比：判断题5道偏多")),
                            planWithVerdict("FAIL", List.of("总题量：选择题9道仍偏多")),
                            planWithVerdict("FAIL", List.of("客观主观比：判断题仍偏多")));
            // 维度名交替变化，不会触发"连续两轮相同"收敛
            // maxRounds=4 → 跑完4轮
            assertEquals(4, simulateLoop(plans, 4));
        }

        @Test
        @DisplayName("振荡场景但有收敛：A→B→B → 第3轮因 B=B 收敛")
        void oscillationThenConverge() {
            List<ExamPlan> plans =
                    List.of(
                            planWithVerdict("FAIL", List.of("总题量：选择题12道偏多")),
                            planWithVerdict("FAIL", List.of("客观主观比：判断题5道偏多")),
                            // 第3轮和第2轮 FAIL 维度相同 → 收敛
                            planWithVerdict("FAIL", List.of("客观主观比：判断题4道仍偏多")));
            assertEquals(3, simulateLoop(plans, 5));
        }

        @Test
        @DisplayName("一年级真实场景模拟：3轮收敛")
        void firstGradeRealWorldScenario() {
            // 模拟一年级出卷的真实评估收敛过程
            List<ExamPlan> plans =
                    List.of(
                            // 第1轮：多维度 FAIL
                            planWithVerdict(
                                    "FAIL",
                                    List.of(
                                            "总题量：35道超出低年级上限30",
                                            "单题分值：口算每题3分过高",
                                            "题型适配：口算用FILL_BLANK不妥")),
                            // 第2轮：部分修复，仍有问题
                            planWithVerdict("FAIL", List.of("总题量：32道仍超出上限30", "单题分值：解决问题每题9分过高")),
                            // 第3轮：维度名与第2轮不同（总题量修好了但单题分值仍 FAIL）
                            planWithVerdict("FAIL", List.of("单题分值：解决问题每题6分仍偏高")),
                            // 第4轮：全部通过
                            planWithVerdict("PASS", List.of("题型分布合理，可直接出题")));
            // 第1轮→第2轮：维度不同（3个→2个），继续
            // 第2轮→第3轮：维度不同（2个→1个），继续
            // 第3轮→第4轮：PASS，接受
            assertEquals(4, simulateLoop(plans, 5));
        }
    }

    // ==================== ExamPlan.overallVerdict 字段 ====================

    @Nested
    @DisplayName("ExamPlan.overallVerdict · 字段存取")
    class OverallVerdictField {

        @Test
        @DisplayName("默认 null")
        void defaultNull() {
            ExamPlan plan = new ExamPlan();
            assertEquals(null, plan.getOverallVerdict());
        }

        @Test
        @DisplayName("set/get 一致")
        void setGetConsistent() {
            ExamPlan plan = new ExamPlan();
            plan.setOverallVerdict("PASS");
            assertEquals("PASS", plan.getOverallVerdict());
        }

        @Test
        @DisplayName("大小写不敏感判定")
        void caseInsensitiveCheck() {
            ExamPlan plan = new ExamPlan();
            plan.setOverallVerdict("pass");
            assertTrue("PASS".equalsIgnoreCase(plan.getOverallVerdict()));
        }
    }
}
