package com.mouhin.knowledge.repository.application.executor.examgrading;

import com.mouhin.knowledge.repository.domain.gateway.ExamAnswerGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 人工复核执行器回归测试（体检 P4 遗留 · HIGH H1）。
 * <p>
 * 锁定"清除人工改分"这一 {@code updateById} 无法覆盖的路径：当复核人把分数改回 {@code null}
 * （意图回落到 AI 评分）时，必须走 {@code clearReviewOverride} 显式将 review_* 列置 NULL，
 * 而非 {@code update()}——后者因 MyBatis-Plus 默认 {@code FieldStrategy=NOT_NULL} 会跳过 null 列，
 * 导致旧覆盖残留、分数永不回落。同时校验非空改分仍正常走 update、越界分数被拒。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-19
 */
@DisplayName("人工复核：清除覆盖走显式置 NULL，改分走 update")
class ReviewAnswerCmdExeTest {

    private static final long ANSWER_ID = 100L;
    private static final long SESSION_ID = 7L;

    private final ExamAnswerGateway answerGateway = mock(ExamAnswerGateway.class);
    private final ExamSessionGateway sessionGateway = mock(ExamSessionGateway.class);
    private final ReviewAnswerCmdExe exe = new ReviewAnswerCmdExe(answerGateway, sessionGateway);

    private ExamAnswer answer() {
        ExamAnswer a = new ExamAnswer();
        a.setId(ANSWER_ID);
        a.setSessionId(SESSION_ID);
        a.setQuestionType("ESSAY");
        a.setMaxScore(10);
        a.setAiScore(6);
        // 历史上存在一次人工改分，本用例验证能否被真正清除
        a.setReviewScore(9);
        return a;
    }

    private void stubSession(String status) {
        ExamSession s = new ExamSession();
        s.setId(SESSION_ID);
        s.setStatus(status);
        when(sessionGateway.findById(SESSION_ID)).thenReturn(Optional.of(s));
    }

    /** reviewScore=null → 必须调用 clearReviewOverride 显式清空，绝不走会跳过 null 列的 update。 */
    @Test
    @DisplayName("清除改分：null 分数触发 clearReviewOverride，不调用 update")
    void nullClearRoutesToExplicitClear() {
        when(answerGateway.findById(ANSWER_ID)).thenReturn(Optional.of(answer()));
        stubSession("AI_GRADED");

        exe.execute(ANSWER_ID, null, null, "teacher01");

        verify(answerGateway).clearReviewOverride(ANSWER_ID);
        verify(answerGateway, never()).update(any(ExamAnswer.class));
    }

    /** 非空分数 → 正常 update 覆盖，不走清除通道。 */
    @Test
    @DisplayName("正常改分：非空分数走 update，不调用 clearReviewOverride")
    void nonNullScoreUsesUpdate() {
        when(answerGateway.findById(ANSWER_ID)).thenReturn(Optional.of(answer()));
        stubSession("AI_GRADED");

        exe.execute(ANSWER_ID, 8, "酌情加分", "teacher01");

        verify(answerGateway).update(any(ExamAnswer.class));
        verify(answerGateway, never()).clearReviewOverride(anyLong());
    }

    /** 越界分数（> 满分）→ 拒绝，不落任何写。 */
    @Test
    @DisplayName("越界分数：抛非法参数，不落任何写")
    void outOfRangeRejected() {
        when(answerGateway.findById(ANSWER_ID)).thenReturn(Optional.of(answer()));
        stubSession("AI_GRADED");

        assertThrows(IllegalArgumentException.class,
                () -> exe.execute(ANSWER_ID, 99, null, "teacher01"));

        verify(answerGateway, never()).update(any(ExamAnswer.class));
        verify(answerGateway, never()).clearReviewOverride(anyLong());
    }

    /** 场次状态非 AI_GRADED/REVIEWED（如进行中）→ 拒绝复核。 */
    @Test
    @DisplayName("非法状态：进行中场次不允许复核")
    void illegalStatusRejected() {
        when(answerGateway.findById(ANSWER_ID)).thenReturn(Optional.of(answer()));
        stubSession("IN_PROGRESS");

        assertThrows(IllegalStateException.class,
                () -> exe.execute(ANSWER_ID, 8, null, "teacher01"));

        verify(answerGateway, never()).update(any(ExamAnswer.class));
        verify(answerGateway, never()).clearReviewOverride(anyLong());
        // 非法状态早退，绝不应回刷场次聚合
        verify(sessionGateway, never()).update(any(ExamSession.class));
    }

    private static ExamAnswer answerWithEffective(int effectiveScore) {
        ExamAnswer a = new ExamAnswer();
        a.setSessionId(SESSION_ID);
        // reviewScore 优先于 aiScore（getEffectiveScore）：直接以复核分表达有效分
        a.setReviewScore(effectiveScore);
        return a;
    }

    /** DATA-2：改分后须按全场有效分之和回刷场次 final_score 并转入 REVIEWED。 */
    @Test
    @DisplayName("DATA-2：非空改分后回刷场次聚合 (final_score=Σ有效分, status=REVIEWED)")
    void nonNullReviewRecomputesSessionAggregate() {
        when(answerGateway.findById(ANSWER_ID)).thenReturn(Optional.of(answer()));
        stubSession("AI_GRADED");
        // 本场次有效分：7 + 3 + 0 = 10
        when(answerGateway.listBySessionId(SESSION_ID)).thenReturn(List.of(
                answerWithEffective(7), answerWithEffective(3), answerWithEffective(0)));

        exe.execute(ANSWER_ID, 8, "酌情加分", "teacher01");

        ArgumentCaptor<ExamSession> captor = ArgumentCaptor.forClass(ExamSession.class);
        verify(sessionGateway).update(captor.capture());
        ExamSession updated = captor.getValue();
        assertEquals(10, updated.getFinalScore().intValue(), "final_score 应回刷为全场有效分之和");
        assertEquals("REVIEWED", updated.getStatus(), "复核后场次应进入 REVIEWED 态");
    }

    /** DATA-2：清除改分（回落 AI 分）同样须触发聚合回刷，避免聚合停留在旧人工合计。 */
    @Test
    @DisplayName("DATA-2：清除改分后仍回刷场次聚合")
    void clearOverrideAlsoRecomputesAggregate() {
        when(answerGateway.findById(ANSWER_ID)).thenReturn(Optional.of(answer()));
        stubSession("REVIEWED");
        // 清除后该题回落 AI 分=6，全场有效分 6 + 2 = 8
        when(answerGateway.listBySessionId(SESSION_ID)).thenReturn(List.of(
                answerWithEffective(6), answerWithEffective(2)));

        exe.execute(ANSWER_ID, null, null, "teacher01");

        verify(answerGateway).clearReviewOverride(ANSWER_ID);
        ArgumentCaptor<ExamSession> captor = ArgumentCaptor.forClass(ExamSession.class);
        verify(sessionGateway).update(captor.capture());
        assertEquals(8, captor.getValue().getFinalScore().intValue(), "清除改分后聚合须重算为回落值");
    }
}
