package com.mouhin.knowledge.repository.application.executor.examgrading;

import com.mouhin.knowledge.repository.domain.gateway.ExamAnswerGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

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
    }
}
