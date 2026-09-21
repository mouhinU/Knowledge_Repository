package com.mouhin.knowledge.repository.application.executor.examgrading;

import static org.assertj.core.api.Assertions.assertThat;

import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 客观题判分特征测试（golden-file 回归基线，改造计划 0-A）。
 *
 * <p>本测试不加载 Spring 上下文，直接以全 null 依赖构造 {@link ExamGradingSupport} （{@code gradeObjective}
 * 不触碰任何注入字段），冻结当前客观题判分行为作为基线： 单选 / 多选（全对、少选半分、含错选零分、乱序、带分隔符）/ 判断题符号归一， 以及"缺标准答案判零分并转人工复核"。任何后续对
 * {@code gradeObjective} / {@code normalizeForCompare} 逻辑的改动若改变此处预期，都会失败并强制显式确认。
 *
 * <p>阶段 0-B~0-E 上线后，本基线应全部保持绿色（这些改动本就已固化在当前实现中）； 阶段 2-A 若切换评分为纯结构化读取，需同步复核本测试的适用性。
 *
 * @author Knowledge-Repository
 * @date 2026-09-18
 */
@DisplayName("客观题判分回归基线 (0-A)")
class ExamGradingCharacterizationTest {

    /** gradeObjective 不使用任何注入依赖，直接以 null 构造被测对象即可。 */
    private final ExamGradingSupport support =
            new ExamGradingSupport(null, null, null, null, null, null, null);

    private ExamAnswer objectiveAnswer(
            String type, String correctAnswer, String studentAnswer, int maxScore) {
        ExamAnswer answer = new ExamAnswer();
        answer.setQuestionIndex(1);
        answer.setQuestionNumber(1);
        answer.setQuestionType(type);
        answer.setQuestionContent("（测试题干）");
        answer.setCorrectAnswer(correctAnswer);
        answer.setStudentAnswer(studentAnswer);
        answer.setMaxScore(maxScore);
        return answer;
    }

    // ==================== 单选 ====================

    @Nested
    @DisplayName("单选题")
    class SingleChoice {

        @Test
        @DisplayName("大小写 / 全角 / 空格容差后命中满分")
        void toleranceMatches() {
            Stream.of("B", "b", " Ｂ ", "\tB\n")
                    .forEach(
                            raw -> {
                                ExamAnswer answer = objectiveAnswer("SINGLE_CHOICE", "B", raw, 3);
                                support.gradeObjective(answer, null);
                                assertThat(answer.getAiScore())
                                        .as("学生答案 [%s] 应判满分", raw)
                                        .isEqualTo(3);
                                assertThat(answer.getCorrect()).isTrue();
                                assertThat(answer.getAiFeedback()).isEqualTo("回答正确");
                            });
        }

        @Test
        @DisplayName("答错判零分并回填正确答案")
        void wrongAnswerScoresZero() {
            ExamAnswer answer = objectiveAnswer("SINGLE_CHOICE", "B", "C", 3);
            support.gradeObjective(answer, null);
            assertThat(answer.getAiScore()).isZero();
            assertThat(answer.getCorrect()).isFalse();
            assertThat(answer.getAiFeedback()).isEqualTo("回答错误，正确答案：B");
        }

        @Test
        @DisplayName("DATA-1：D 之后的选项(E) 不再因归一为空而误判满分")
        void optionBeyondDIsNotFalseFullMark() {
            // 正确答案 E，学生未答（空）——旧 A-D 归一会把双方都剥成空串误判相等给满分
            ExamAnswer blank = objectiveAnswer("SINGLE_CHOICE", "E", "", 3);
            support.gradeObjective(blank, null);
            assertThat(blank.getAiScore()).as("期望 E / 学生未答 应判 0 分").isZero();
            assertThat(blank.getCorrect()).isFalse();

            // 正确答案 E，学生作答 E —— 应正常命中满分
            ExamAnswer hit = objectiveAnswer("SINGLE_CHOICE", "E", "E", 3);
            support.gradeObjective(hit, null);
            assertThat(hit.getAiScore()).as("期望 E / 学生 E 应满分").isEqualTo(3);
            assertThat(hit.getCorrect()).isTrue();
        }
    }

    // ==================== 多选（部分给分 D4） ====================

    @Nested
    @DisplayName("多选题（部分给分）")
    class MultiChoice {

        static Stream<Arguments> partialCreditCases() {
            return Stream.of(
                    // correct, student, maxScore, expectedScore, expectedCorrect, expectedFeedback
                    Arguments.of("AC", "CA", 4, 4, true, "回答正确（全对）"), // 乱序仍全对
                    Arguments.of("AC", "A、C", 4, 4, true, "回答正确（全对）"), // 带顿号分隔
                    Arguments.of("AC", "ac", 4, 4, true, "回答正确（全对）"), // 小写
                    Arguments.of("AC", "A", 4, 2, false, "少选（正确答案：AC），得半分"), // 少选半分(偶)
                    Arguments.of("ABD", "A", 5, 2, false, "少选（正确答案：ABD），得半分"), // 少选半分(奇→向下取整)
                    Arguments.of("AC", "AB", 4, 0, false, "含错选（选择了不在正确答案中的选项）"), // 含错选
                    Arguments.of("AC", "", 4, 0, false, "未选择任何选项"), // 未选(空串)
                    Arguments.of("AC", "   ", 4, 0, false, "未选择任何选项") // 未选(纯空白)
                    );
        }

        @ParameterizedTest(name = "标准[{0}] 学生[{1}] 满分{2} → {3}分")
        @MethodSource("partialCreditCases")
        @DisplayName("全对 / 少选半分 / 含错选零分 / 未选零分")
        void partialCreditMatrix(
                String correct,
                String student,
                int maxScore,
                int expectedScore,
                boolean expectedCorrect,
                String expectedFeedback) {
            ExamAnswer answer = objectiveAnswer("MULTI_CHOICE", correct, student, maxScore);
            support.gradeObjective(answer, null);
            assertThat(answer.getAiScore()).isEqualTo(expectedScore);
            assertThat(answer.getCorrect()).isEqualTo(expectedCorrect);
            assertThat(answer.getAiFeedback()).isEqualTo(expectedFeedback);
        }

        @Test
        @DisplayName("学生未答（null）按未选零分处理")
        void nullStudentAnswer() {
            ExamAnswer answer = objectiveAnswer("MULTI_CHOICE", "AC", null, 4);
            support.gradeObjective(answer, null);
            assertThat(answer.getAiScore()).isZero();
            assertThat(answer.getCorrect()).isFalse();
            assertThat(answer.getAiFeedback()).isEqualTo("未选择任何选项");
        }

        @Test
        @DisplayName("字母集归一：去重排序且保留 A-Z（DATA-1 修正）")
        void choiceSetNormalization() {
            // 归一化后按首次出现去重排序
            assertThat(support.normalizeForCompare("DCA", "MULTI_CHOICE")).isEqualTo("ACD");
            assertThat(support.normalizeForCompare("A,D ; C", "MULTI_CHOICE")).isEqualTo("ACD");
            assertThat(support.normalizeForCompare("aacc", "MULTI_CHOICE")).isEqualTo("AC");
            // DATA-1：A 之外的字母（含 E/F/G）不再被误剥
            assertThat(support.normalizeForCompare("AEFG", "MULTI_CHOICE")).isEqualTo("AEFG");
            assertThat(support.normalizeForCompare("ACE", "MULTI_CHOICE")).isEqualTo("ACE");
        }
    }

    // ==================== 判断题（符号归一） ====================

    @Nested
    @DisplayName("判断题")
    class TrueFalse {

        @Test
        @DisplayName("正向符号字典全部归一为 TRUE 并互相命中")
        void truthyVariantsMatch() {
            Stream.of("正确", "对", "是", "√", "✓", "T", "true", "Y", "YES")
                    .forEach(
                            v ->
                                    assertThat(support.normalizeForCompare(v, "TRUE_FALSE"))
                                            .as("正向变体 [%s]", v)
                                            .isEqualTo("TRUE"));
        }

        @Test
        @DisplayName("负向符号字典全部归一为 FALSE 并互相命中")
        void falsyVariantsMatch() {
            Stream.of("错误", "错", "否", "×", "✗", "✕", "X", "false", "N", "NO")
                    .forEach(
                            v ->
                                    assertThat(support.normalizeForCompare(v, "TRUE_FALSE"))
                                            .as("负向变体 [%s]", v)
                                            .isEqualTo("FALSE"));
        }

        @Test
        @DisplayName("学生 √ vs 标准 正确 → 命中满分")
        void symbolAgainstWord() {
            ExamAnswer answer = objectiveAnswer("TRUE_FALSE", "正确", "√", 2);
            support.gradeObjective(answer, null);
            assertThat(answer.getAiScore()).isEqualTo(2);
            assertThat(answer.getCorrect()).isTrue();
        }

        @Test
        @DisplayName("答反判零分")
        void reversed() {
            ExamAnswer answer = objectiveAnswer("TRUE_FALSE", "正确", "×", 2);
            support.gradeObjective(answer, null);
            assertThat(answer.getAiScore()).isZero();
            assertThat(answer.getCorrect()).isFalse();
            assertThat(answer.getAiFeedback()).isEqualTo("回答错误，正确答案：正确");
        }
    }

    // ==================== 缺标准答案（0-E 去静默满分） ====================

    @Nested
    @DisplayName("缺标准答案")
    class MissingAnswerKey {

        @Test
        @DisplayName("correctAnswer 为 null → 判零分 + 待人工确认反馈")
        void nullKey() {
            ExamAnswer answer = objectiveAnswer("SINGLE_CHOICE", null, "A", 3);
            support.gradeObjective(answer, null);
            assertThat(answer.getAiScore()).isZero();
            assertThat(answer.getCorrect()).isFalse();
            assertThat(answer.getAiFeedback()).isEqualTo("缺少标准答案，待人工确认");
        }

        @Test
        @DisplayName("correctAnswer 纯空白 → 同样判零分")
        void blankKey() {
            ExamAnswer answer = objectiveAnswer("MULTI_CHOICE", "   ", "A", 4);
            support.gradeObjective(answer, null);
            assertThat(answer.getAiScore()).isZero();
            assertThat(answer.getAiFeedback()).isEqualTo("缺少标准答案，待人工确认");
        }

        @Test
        @DisplayName("缺答案判零分后 needsReview 应为真（0 < maxScore）")
        void missingKeyTriggersReview() {
            ExamAnswer answer = objectiveAnswer("SINGLE_CHOICE", "", "A", 3);
            support.gradeObjective(answer, null);
            assertThat(answer.needsReview()).isTrue();
        }
    }

    // ==================== 脏标准答案（含内联解释，判断题契约修复回归） ====================

    @Nested
    @DisplayName("脏标准答案（结论+内联解释）")
    class DirtyAnswerKey {

        @Test
        @DisplayName("判断题「正确。理由：…」遇学生 √ → 命中满分")
        void trueFalseWithReason() {
            ExamAnswer answer = objectiveAnswer("TRUE_FALSE", "正确。理由：光合作用的产物包括氧气。", "√", 2);
            support.gradeObjective(answer, null);
            assertThat(answer.getAiScore()).isEqualTo(2);
            assertThat(answer.getCorrect()).isTrue();
        }

        @Test
        @DisplayName("判断题「错误。理由：…」遇学生 × → 命中满分")
        void falseWithReason() {
            ExamAnswer answer = objectiveAnswer("TRUE_FALSE", "错误。理由：该说法以偏概全。", "×", 2);
            support.gradeObjective(answer, null);
            assertThat(answer.getAiScore()).isEqualTo(2);
            assertThat(answer.getCorrect()).isTrue();
        }

        @Test
        @DisplayName("单选「B【解析】C 项…」不因解释字母误判 → 命中满分")
        void singleChoiceWithInlineAnalysis() {
            ExamAnswer answer = objectiveAnswer("SINGLE_CHOICE", "B【解析】C 项表述过于绝对，故排除。", "B", 3);
            support.gradeObjective(answer, null);
            assertThat(answer.getAiScore()).isEqualTo(3);
            assertThat(answer.getCorrect()).isTrue();
        }

        @Test
        @DisplayName("多选「AC（解析：B错D错）」不因解释字母判为少选 → 全对满分")
        void multiChoiceWithInlineAnalysis() {
            ExamAnswer answer = objectiveAnswer("MULTI_CHOICE", "AC（解析：B 项错误，D 项以偏概全）", "AC", 4);
            support.gradeObjective(answer, null);
            assertThat(answer.getAiScore()).isEqualTo(4);
            assertThat(answer.getCorrect()).isTrue();
            assertThat(answer.getAiFeedback()).isEqualTo("回答正确（全对）");
        }
    }
}
