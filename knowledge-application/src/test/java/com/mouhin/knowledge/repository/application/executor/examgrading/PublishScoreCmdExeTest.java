package com.mouhin.knowledge.repository.application.executor.examgrading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.ExamAnswerGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 复核完成并发布成绩执行器单测：锁定发布门禁与状态迁移—— （1）AI_GRADED 且无待人工确认题 → 重算最终分（复核分优先）、状态转 PUBLISHED、回写发布时间；
 * （2）状态不满足（SUBMITTED）→ 拒绝发布；（3）存在未确认主观题 → 拒绝并给出待确认数量； （4）重复发布 → 当前实现抛状态异常而非幂等返回；（5）场次不存在 → 非法参数。
 *
 * @author mouhinU
 * @date 2026-09-24 18:15:00
 */
@DisplayName("发布成绩执行器 (PublishScoreCmdExe)")
class PublishScoreCmdExeTest {

    private static final Long SESSION_ID = 11L;
    private static final String REVIEWER = "teacher-A";

    private final ExamSessionGateway examSessionGateway = mock(ExamSessionGateway.class);
    private final ExamAnswerGateway examAnswerGateway = mock(ExamAnswerGateway.class);
    private final PublishScoreCmdExe exe =
            new PublishScoreCmdExe(examSessionGateway, examAnswerGateway);

    private void stubSession(String status) {
        ExamSession session = new ExamSession();
        session.setId(SESSION_ID);
        session.setStatus(status);
        when(examSessionGateway.findById(SESSION_ID)).thenReturn(Optional.of(session));
    }

    private ExamAnswer objective(String correctAnswer, Integer aiScore) {
        ExamAnswer answer = new ExamAnswer();
        answer.setQuestionType("SINGLE_CHOICE");
        answer.setCorrectAnswer(correctAnswer);
        answer.setAiScore(aiScore);
        answer.setMaxScore(5);
        return answer;
    }

    private ExamAnswer essayReviewed(Integer reviewScore) {
        ExamAnswer answer = new ExamAnswer();
        answer.setQuestionType("ESSAY");
        answer.setReviewScore(reviewScore);
        answer.setAiScore(6);
        answer.setMaxScore(10);
        return answer;
    }

    @Test
    @DisplayName("AI_GRADED + 全部题项可放行 → 最终分=复核分优先求和，状态转 PUBLISHED 并落库")
    void publishesWhenAllItemsCleared() {
        stubSession("AI_GRADED");
        when(examAnswerGateway.listBySessionId(SESSION_ID))
                .thenReturn(List.of(objective("A", 5), essayReviewed(8)));

        exe.execute(SESSION_ID, REVIEWER);

        ArgumentCaptor<ExamSession> cap = ArgumentCaptor.forClass(ExamSession.class);
        verify(examSessionGateway).update(cap.capture());
        ExamSession saved = cap.getValue();
        assertThat(saved.getStatus()).isEqualTo("PUBLISHED");
        assertThat(saved.getFinalScore()).isEqualTo(13);
        assertThat(saved.getPublishTime()).isNotNull();
        assertThat(saved.getUpdateTime()).isNotNull();
    }

    @Test
    @DisplayName("未评分（SUBMITTED）→ 拒绝发布，不落库")
    void ungradedSessionCannotPublish() {
        stubSession("SUBMITTED");

        assertThatThrownBy(() -> exe.execute(SESSION_ID, REVIEWER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不允许发布成绩")
                .hasMessageContaining("SUBMITTED");
        verify(examSessionGateway, never()).update(any());
    }

    @Test
    @DisplayName("存在未人工确认的主观题 → 拒绝发布并在消息中给出待确认数量")
    void pendingHumanReviewBlocksPublish() {
        stubSession("AI_GRADED");
        ExamAnswer unreviewedEssay = new ExamAnswer();
        unreviewedEssay.setQuestionType("ESSAY");
        unreviewedEssay.setAiScore(6);
        unreviewedEssay.setMaxScore(10);
        when(examAnswerGateway.listBySessionId(SESSION_ID))
                .thenReturn(List.of(objective("A", 5), unreviewedEssay));

        assertThatThrownBy(() -> exe.execute(SESSION_ID, REVIEWER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1")
                .hasMessageContaining("未经人工确认");
        verify(examSessionGateway, never()).update(any());
    }

    @Test
    @DisplayName("重复发布（PUBLISHED）→ 抛状态异常而非幂等成功（TODO(行为可疑): 幂等语义下应静默返回或提示已发布，当前会让前端二次点击收到 500 级错误）")
    void duplicatePublishThrowsInsteadOfIdempotentSuccess() {
        stubSession("PUBLISHED");

        assertThatThrownBy(() -> exe.execute(SESSION_ID, REVIEWER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不允许发布成绩");
        verify(examSessionGateway, never()).update(any());
    }

    @Test
    @DisplayName("场次不存在 → 抛 IllegalArgumentException，不查答题行")
    void missingSessionRejected() {
        when(examSessionGateway.findById(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exe.execute(SESSION_ID, REVIEWER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("考试场次不存在");
        verify(examAnswerGateway, never()).listBySessionId(any());
    }
}
