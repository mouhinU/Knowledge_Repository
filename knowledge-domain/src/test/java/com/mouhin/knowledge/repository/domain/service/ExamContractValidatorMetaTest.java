package com.mouhin.knowledge.repository.domain.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ExamContractValidator} 出处/位置类记忆题确定性门禁单测。
 *
 * <p>回归新增的内容复核规则：切分行中若题干命中出处/位置类记忆题，校验须不通过并产出带题号与建议语的 issue（从而置
 * VALIDATION_FAILED、强制人工改写后方可发布）；同时验证正常卷仍通过，规则不产生误伤。 plan 传 null 以聚焦逐题内容校验，规避题数 / 分值对齐维度。
 *
 * @author Knowledge-Repository
 * @date 2026-09-20
 */
@DisplayName("出卷契约校验·出处/位置类门禁 (ExamContractValidator)")
class ExamContractValidatorMetaTest {

    private ExamQuestion q(int num, String type, String stem, String answer, String optionsJson) {
        ExamQuestion e = new ExamQuestion();
        e.setQuestionNumber(num);
        e.setQuestionType(type);
        e.setStem(stem);
        e.setCorrectAnswer(answer);
        e.setOptionsJson(optionsJson);
        e.setMaxScore(2);
        return e;
    }

    @Test
    @DisplayName("正常卷（无出处题）通过校验")
    void passesCleanPaper() {
        List<ExamQuestion> qs =
                List.of(
                        q(
                                1,
                                "SINGLE_CHOICE",
                                "下列哪个是单韵母？",
                                "B",
                                "[{\"key\":\"A\",\"value\":\"b\"},{\"key\":\"B\",\"value\":\"a\"}]"),
                        q(2, "FILL_BLANK", "“口”有（ ）画。", "3画", null));
        ExamContractValidator.Result r = ExamContractValidator.validate(qs, null);
        assertTrue(r.pass(), "干净卷应通过，实际 issues=" + r.issues());
    }

    @Test
    @DisplayName("含第几单元题干时不通过并给出题号+建议")
    void flagsMetaRecallQuestion() {
        List<ExamQuestion> qs =
                List.of(
                        q(
                                1,
                                "SINGLE_CHOICE",
                                "下列哪个是单韵母？",
                                "B",
                                "[{\"key\":\"A\",\"value\":\"b\"},{\"key\":\"B\",\"value\":\"a\"}]"),
                        q(
                                2,
                                "SINGLE_CHOICE",
                                "《小小的船》是第几单元的课文？",
                                "B",
                                "[{\"key\":\"A\",\"value\":\"第一单元\"},{\"key\":\"B\",\"value\":\"第七单元\"}]"));
        ExamContractValidator.Result r = ExamContractValidator.validate(qs, null);
        assertFalse(r.pass());
        boolean hasAdvisory =
                r.issues().stream()
                        .anyMatch(
                                i ->
                                        i.contains("第 2 题")
                                                && i.contains(ExamMetaQuestionDetector.ADVISORY));
        assertTrue(hasAdvisory, "应产出第2题出处题 issue，实际=" + r.issues());
    }
}
