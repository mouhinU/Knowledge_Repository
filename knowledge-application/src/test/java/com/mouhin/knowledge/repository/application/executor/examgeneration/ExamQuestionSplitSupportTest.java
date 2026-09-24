package com.mouhin.knowledge.repository.application.executor.examgeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.ExamQuestionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 出卷期切分支撑单测：锁定「试卷 Markdown → 结构化题目行」主干 —— 印刷题号 / 题型 / 分值 / 选项序列化与 答案键按题序绑定、空白卷回空列表、落库幂等（先删后插）与
 * sessionKey 缺失 / 切分为空时的校验短路语义。 配图快照保留分支已由 {@code ExamQuestionSplitSupportImageTest} 覆盖，此处不重复。
 *
 * @author mouhinU
 * @date 2026-09-24 18:16:00
 */
@DisplayName("出卷期切分支撑 (ExamQuestionSplitSupport)")
class ExamQuestionSplitSupportTest {

    private final ExamQuestionGateway questionGateway = mock(ExamQuestionGateway.class);
    private final ExamQuestionSplitSupport support = new ExamQuestionSplitSupport(questionGateway);

    /** 单选 2 题 + 判断 1 题的最小可解析卷面（复用 ExamPaperParserTest 的成熟格式）。 */
    private static final String PAPER =
            """
            # 单元测试卷

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
            """;

    private static final String ANSWER_KEY =
            """
            ## 一、单选题
            1. 答案：B
            2. 答案：C

            ## 二、判断题
            3. 答案：正确
            """;

    @Test
    @DisplayName("splitAndBind：题号 / 题型 / 分值 / 选项 JSON 与答案键按题序绑定，不落库")
    void splitAndBindParsesAndBindsAnswers() {
        List<ExamQuestion> questions = support.splitAndBind("s-1", PAPER, ANSWER_KEY, null);

        assertEquals(3, questions.size());
        ExamQuestion q1 = questions.get(0);
        assertEquals("s-1", q1.getSessionKey());
        assertEquals(1, q1.getQuestionNumber());
        assertEquals("SINGLE_CHOICE", q1.getQuestionType());
        assertEquals(2, q1.getMaxScore());
        assertNotNull(q1.getOptionsJson(), "选择题选项应序列化为 JSON");
        assertTrue(q1.getOptionsJson().contains("\"a\""), "选项正文应进入 options_json");
        assertEquals("B", q1.getCorrectAnswer(), "第 1 题应按题序绑定答案键 B");
        assertEquals("C", questions.get(1).getCorrectAnswer());
        assertEquals("TRUE_FALSE", questions.get(2).getQuestionType());
        assertEquals("正确", questions.get(2).getCorrectAnswer());
        assertNotNull(q1.getCreateTime());
        verify(questionGateway, never()).batchInsert(any());
    }

    @Test
    @DisplayName("splitAndBind：空白试卷 / null 入参 → 空列表不抛异常")
    void splitAndBindEmptyPaper() {
        assertTrue(support.splitAndBind("s-1", "", ANSWER_KEY, null).isEmpty());
        assertTrue(support.splitAndBind("s-1", "  \n  ", null, null).isEmpty());
    }

    @Test
    @DisplayName("splitAndBind：答案键缺失该题 → 行仍产出但 correctAnswer 为空（留给契约校验暴露）")
    void missingAnswerKeyEntryLeavesAnswerNull() {
        List<ExamQuestion> questions = support.splitAndBind("s-1", PAPER, "1. 答案：B", null);

        assertEquals("B", questions.get(0).getCorrectAnswer());
        assertNull(questions.get(2).getCorrectAnswer(), "无对应答案键条目应保持 null");
    }

    @Test
    @DisplayName("splitAndPersist：sessionKey 空白 → 短路返回 0 +「试卷标识为空」，不触库")
    void persistRejectsBlankSessionKey() {
        ExamQuestionSplitSupport.SplitOutcome outcome =
                support.splitAndPersist(" ", PAPER, ANSWER_KEY, null);

        assertEquals(0, outcome.count());
        assertFalse(outcome.validation().pass());
        assertTrue(outcome.validation().issues().get(0).contains("试卷标识为空"));
        verify(questionGateway, never()).deleteBySessionKey(anyString());
        verify(questionGateway, never()).batchInsert(any());
    }

    @Test
    @DisplayName("splitAndPersist 主干：幂等先删后插，契约校验通过")
    void persistDeletesThenBatchInsertsAndValidates() {
        when(questionGateway.listBySessionKey("s-9")).thenReturn(List.of());

        ExamQuestionSplitSupport.SplitOutcome outcome =
                support.splitAndPersist("s-9", PAPER, ANSWER_KEY, null);

        assertEquals(3, outcome.count());
        assertTrue(outcome.validation().pass(), "应通过契约校验，实际 " + outcome.validation().issues());
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(questionGateway);
        inOrder.verify(questionGateway).deleteBySessionKey("s-9");
        inOrder.verify(questionGateway).batchInsert(any());
        ArgumentCaptor<List<ExamQuestion>> cap = ArgumentCaptor.forClass(List.class);
        verify(questionGateway).batchInsert(cap.capture());
        assertTrue(cap.getValue().stream().allMatch(q -> "s-9".equals(q.getSessionKey())));
    }

    @Test
    @DisplayName("splitAndPersist：试卷解析为空 →「试卷切分结果为空」且不删不插（幂等保护存量行）")
    void persistEmptyPaperShortCircuitsWithoutDelete() {
        when(questionGateway.listBySessionKey("s-8")).thenReturn(List.of());

        ExamQuestionSplitSupport.SplitOutcome outcome =
                support.splitAndPersist("s-8", "# 空卷\n", ANSWER_KEY, null);

        assertEquals(0, outcome.count());
        assertFalse(outcome.validation().pass());
        assertTrue(outcome.validation().issues().get(0).contains("试卷切分结果为空"));
        verify(questionGateway, never()).deleteBySessionKey(anyString());
        verify(questionGateway, never()).batchInsert(any());
    }

    @Test
    @DisplayName("splitAndPersist：答案缺失 → 落库照常但契约校验 FAIL 并含「缺少标准答案」")
    void persistStillWritesButValidationFailsOnMissingAnswer() {
        when(questionGateway.listBySessionKey("s-7")).thenReturn(List.of());

        ExamQuestionSplitSupport.SplitOutcome outcome =
                support.splitAndPersist("s-7", PAPER, null, null);

        assertEquals(3, outcome.count());
        assertFalse(outcome.validation().pass());
        assertTrue(outcome.validation().issues().stream().anyMatch(s -> s.contains("缺少标准答案")));
        verify(questionGateway).batchInsert(any());
    }

    // 其他分支待补：填空题 blankCount 统计口径（ExamBlankCounter 已有独立测试）、评分标准绑定细节。
}
