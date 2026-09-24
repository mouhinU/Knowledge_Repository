package com.mouhin.knowledge.repository.application.executor.examreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamQuestionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.service.ExamContractValidator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 试卷校对支撑单测：锁定校对关口共用装配 —— requireHistory 的空标识 / 不存在负向语义、 planOf 方案解析缺省回 null（校验器据此跳过对齐）、
 * listQuestions 委托与 validate 复跑契约门禁。 状态迁移（markPublished / markReviewable 等）落在实体 {@code
 * ExamHistory}，由 {@code ExamStartFromHistoryGuardTest} 覆盖，此处不重复。
 *
 * @author mouhinU
 * @date 2026-09-24 18:16:00
 */
@DisplayName("试卷校对支撑 (PaperReviewSupport)")
class PaperReviewSupportTest {

    private final ExamHistoryGateway historyGateway = mock(ExamHistoryGateway.class);
    private final ExamQuestionGateway questionGateway = mock(ExamQuestionGateway.class);
    private final PaperReviewSupport support =
            new PaperReviewSupport(historyGateway, questionGateway);

    private ExamQuestion question(Integer number, String type, String answer) {
        ExamQuestion q = new ExamQuestion();
        q.setQuestionNumber(number);
        q.setQuestionType(type);
        q.setCorrectAnswer(answer);
        q.setStem("题干" + number);
        return q;
    }

    @Test
    @DisplayName("requireHistory：标识 null / 空白 → IllegalArgumentException「试卷标识不能为空」")
    void requireHistoryRejectsBlankKey() {
        assertThrows(IllegalArgumentException.class, () -> support.requireHistory(null));
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> support.requireHistory("  "));
        assertTrue(ex.getMessage().contains("试卷标识不能为空"));
        // 空白标识不得触库
        verify(historyGateway, org.mockito.Mockito.never())
                .findBySessionId(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("requireHistory：查无此卷 → 异常信息携带试卷标识")
    void requireHistoryNotFoundThrows() {
        when(historyGateway.findBySessionId("s-404")).thenReturn(Optional.empty());

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> support.requireHistory("s-404"));
        assertTrue(ex.getMessage().contains("s-404"));
    }

    @Test
    @DisplayName("requireHistory：命中 → 原样返回历史")
    void requireHistoryReturnsFound() {
        ExamHistory history = new ExamHistory();
        history.setSessionId("s-1");
        when(historyGateway.findBySessionId("s-1")).thenReturn(Optional.of(history));

        assertEquals(history, support.requireHistory("s-1"));
    }

    @Test
    @DisplayName("planOf：合法方案 JSON 解析出结构；null / 空白回退 null（校验器跳过对齐）")
    void planOfParsesOrReturnsNull() {
        ExamHistory withPlan = new ExamHistory();
        withPlan.setExamPlan(
                "{\"schoolLevel\":\"PRIMARY\",\"totalFullMark\":8,\"types\":[{\"key\":\"SINGLE_CHOICE\",\"label\":\"单选\",\"count\":2,\"perQuestion\":[4,4]}]}");

        ExamPlan plan = support.planOf(withPlan);
        assertNotNull(plan, "合法 JSON 应解析出方案");
        assertEquals(8, plan.getTotalFullMark());
        assertEquals(2, plan.totalQuestions());

        ExamHistory blank = new ExamHistory();
        blank.setExamPlan("   ");
        assertNull(support.planOf(blank), "空白方案应回 null");
        assertNull(support.planOf(new ExamHistory()), "无方案字段回 null");
    }

    @Test
    @DisplayName("listQuestions：委托题号升序查询")
    void listQuestionsDelegates() {
        List<ExamQuestion> questions = List.of(question(1, "SHORT_ANSWER", "答案"));
        when(questionGateway.listBySessionKey(eq("s-1"))).thenReturn(questions);

        assertEquals(questions, support.listQuestions("s-1"));
        verify(questionGateway).listBySessionKey("s-1");
    }

    @Test
    @DisplayName("validate：复跑契约门禁 —— 缺答案题目判 FAIL 并回问题清单")
    void validateRerunsContractGate() {
        ExamContractValidator.Result fail =
                support.validate(List.of(question(1, "SINGLE_CHOICE", null)), null);

        assertFalse(fail.pass());
        assertTrue(fail.issues().stream().anyMatch(s -> s.contains("缺少标准答案")));

        ExamContractValidator.Result pass =
                support.validate(List.of(question(2, "SHORT_ANSWER", "答案正文")), null);
        assertTrue(pass.pass(), "null 方案降级下应通过，实际 " + pass.issues());
    }

    // 其他分支待补：就地编辑回写 / 重切分等执行器对 support 的编排依赖属各 CmdExe 测试范围。
}
