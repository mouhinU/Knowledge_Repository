package com.mouhin.knowledge.repository.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.model.valueobject.TypePlan;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 出卷契约校验器单测：锁定发布前硬关口的 PASS / FAIL 判定 —— 空卷、null 方案降级、题数 / 分值 / 题型分布对齐、 印刷题号唯一、答案规范（单选恰 1 字母 / 多选 ≥
 * 2 字母 / 判断判词）与选择题选项缺失。
 *
 * @author mouhinU
 * @date 2026-09-24 18:16:00
 */
@DisplayName("出卷契约校验器 (ExamContractValidator)")
class ExamContractValidatorTest {

    private ExamQuestion question(
            Integer number, String type, String answer, String optionsJson, Integer maxScore) {
        ExamQuestion q = new ExamQuestion();
        q.setQuestionNumber(number);
        q.setQuestionType(type);
        q.setCorrectAnswer(answer);
        q.setOptionsJson(optionsJson);
        q.setMaxScore(maxScore);
        q.setStem("测试题干" + number);
        return q;
    }

    private ExamPlan plan(int fullMark, TypePlan... types) {
        ExamPlan p = new ExamPlan();
        p.setTotalFullMark(fullMark);
        p.setTypes(List.of(types));
        return p;
    }

    @Test
    @DisplayName("合法卷子 + 对齐方案 → PASS，报告渲染为 ✅ 通过")
    void validPaperWithAlignedPlanPasses() {
        List<ExamQuestion> questions =
                List.of(
                        question(1, "SINGLE_CHOICE", "B", "[{\"key\":\"A\",\"value\":\"x\"}]", 3),
                        question(2, "MULTI_CHOICE", "AC", "[{\"key\":\"A\",\"value\":\"x\"}]", 3),
                        question(3, "TRUE_FALSE", "正确", null, 2));
        ExamPlan p =
                plan(
                        8,
                        new TypePlan("SINGLE_CHOICE", "单选题", 1, List.of(3)),
                        new TypePlan("MULTI_CHOICE", "多选题", 1, List.of(3)),
                        new TypePlan("TRUE_FALSE", "判断题", 1, List.of(2)));

        ExamContractValidator.Result result = ExamContractValidator.validate(questions, p);

        assertTrue(result.pass(), "应通过，实际 issues=" + result.issues());
        assertTrue(ExamContractValidator.renderReport(result).contains("✅"));
    }

    @Test
    @DisplayName("null 方案降级：跳过题数 / 分值 / 分布对齐，仅校验编号与答案规范")
    void nullPlanSkipsAlignmentChecks() {
        List<ExamQuestion> questions = List.of(question(7, "SHORT_ANSWER", "任意答案正文", null, 100));

        ExamContractValidator.Result result = ExamContractValidator.validate(questions, null);

        assertTrue(result.pass(), "null 方案下应仅做局部校验，实际 issues=" + result.issues());
    }

    @Test
    @DisplayName("切分结果为空 / null → FAIL 并给出「无任何题目」问题项")
    void emptyQuestionsFail() {
        ExamContractValidator.Result nullResult = ExamContractValidator.validate(null, null);
        ExamContractValidator.Result emptyResult = ExamContractValidator.validate(List.of(), null);

        assertFalse(nullResult.pass());
        assertTrue(nullResult.issues().get(0).contains("空"));
        assertFalse(emptyResult.pass());
        assertTrue(
                ExamContractValidator.renderReport(emptyResult).contains("❌"), "未通过报告应含 ❌ 与问题清单");
    }

    @Test
    @DisplayName("题数 / 分值合计与方案不齐 → FAIL，问题文案含具体差值")
    void misalignedCountsAndScoresFail() {
        List<ExamQuestion> questions =
                List.of(question(1, "SINGLE_CHOICE", "B", "[{\"key\":\"A\"}]", 3));
        ExamPlan p = plan(100, new TypePlan("SINGLE_CHOICE", "单选题", 5, List.of(20)));

        ExamContractValidator.Result result = ExamContractValidator.validate(questions, p);

        assertFalse(result.pass());
        assertTrue(
                result.issues().stream().anyMatch(s -> s.contains("切分题数 1 ≠ 方案总题量 5")),
                "应报题数不齐，实际 " + result.issues());
        assertTrue(
                result.issues().stream().anyMatch(s -> s.contains("题目分值合计 3 ≠ 方案满分 100")),
                "应报分值不齐，实际 " + result.issues());
        assertTrue(
                result.issues().stream().anyMatch(s -> s.contains("切分数量 1 ≠ 方案题量 5")),
                "应报题型分布不齐，实际 " + result.issues());
    }

    @Test
    @DisplayName("印刷题号缺失或重复 → FAIL")
    void missingOrDuplicateQuestionNumbersFail() {
        ExamContractValidator.Result missing =
                ExamContractValidator.validate(
                        List.of(
                                question(null, "SHORT_ANSWER", "答案", null, 5),
                                question(2, "SHORT_ANSWER", "答案", null, 5)),
                        null);
        ExamContractValidator.Result duplicated =
                ExamContractValidator.validate(
                        List.of(
                                question(1, "SHORT_ANSWER", "答案", null, 5),
                                question(1, "SHORT_ANSWER", "答案", null, 5)),
                        null);

        assertFalse(missing.pass());
        assertTrue(missing.issues().stream().anyMatch(s -> s.contains("缺失印刷题号")));
        assertFalse(duplicated.pass());
        assertTrue(duplicated.issues().stream().anyMatch(s -> s.contains("印刷题号重复")));
    }

    @Test
    @DisplayName("答案规范：单选多字母 / 多选单字母 / 判断非判词 / 答案缺失 / 选择题无选项均 FAIL")
    void answerSpecViolationsFail() {
        List<ExamQuestion> bad =
                List.of(
                        question(1, "SINGLE_CHOICE", "AB", "[{\"key\":\"A\"}]", 3),
                        question(2, "MULTI_CHOICE", "A", "[{\"key\":\"A\"}]", 3),
                        question(3, "TRUE_FALSE", "也许吧", null, 2),
                        question(4, "SHORT_ANSWER", "  ", null, 2),
                        question(5, "SINGLE_CHOICE", "B", "[]", 2));

        ExamContractValidator.Result result = ExamContractValidator.validate(bad, null);

        assertFalse(result.pass());
        List<String> issues = result.issues();
        assertTrue(issues.stream().anyMatch(s -> s.contains("第 1 题") && s.contains("单选答案非法")));
        assertTrue(issues.stream().anyMatch(s -> s.contains("第 2 题") && s.contains("多选答案非法")));
        assertTrue(issues.stream().anyMatch(s -> s.contains("第 3 题") && s.contains("判断答案非法")));
        assertTrue(issues.stream().anyMatch(s -> s.contains("第 4 题") && s.contains("缺少标准答案")));
        assertTrue(issues.stream().anyMatch(s -> s.contains("第 5 题") && s.contains("选项缺失")));
        // 判断题不校验选项：不应为第 3 题报「选项缺失」
        assertFalse(
                issues.stream().anyMatch(s -> s.contains("第 3 题") && s.contains("选项缺失")),
                "判断题为隐式二选一，不应校验选项");
    }

    @Test
    @DisplayName("边界：方案 totalQuestions ≤ 0 / 满分 ≤ 0 时跳过对应对齐（不误报）")
    void nonPositivePlanTotalsSkipAlignment() {
        List<ExamQuestion> questions = List.of(question(1, "SHORT_ANSWER", "答案", null, 9));
        ExamPlan p = new ExamPlan();
        p.setTotalFullMark(0);
        p.setTypes(List.of(new TypePlan("SHORT_ANSWER", "简答题", 0, List.of())));

        ExamContractValidator.Result result = ExamContractValidator.validate(questions, p);

        assertTrue(result.pass(), "缺省/零值方案不应误报，实际 " + result.issues());
        assertEquals(1, questions.size());
    }
}
