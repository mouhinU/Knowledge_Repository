package com.mouhin.knowledge.repository.application.executor.examreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.ExamQuestionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.service.ExamContractValidator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 就地编辑单题校对信息执行器单测：锁定「回写 + 复跑校验」闭环—— （1）正常编辑 → updateCorrection 原样透传五个字段，并返回基于最新题目集合的契约校验结果； （2）题号为空
 * → 非法参数，先于任何加载与回写被拦截； （3）试卷不存在（requireHistory 抛错）→ 异常上抛，不回写； （4）已发布试卷当前仍可被就地改答案（附可疑标注）。
 *
 * @author mouhinU
 * @date 2026-09-24 18:15:00
 */
@DisplayName("单题就地校对编辑执行器 (UpdatePaperQuestionCmdExe)")
class UpdatePaperQuestionCmdExeTest {

    private static final String SESSION_KEY = "sess-edit-1";

    private final PaperReviewSupport support = mock(PaperReviewSupport.class);
    private final ExamQuestionGateway examQuestionGateway = mock(ExamQuestionGateway.class);
    private final UpdatePaperQuestionCmdExe exe =
            new UpdatePaperQuestionCmdExe(support, examQuestionGateway);

    @Test
    @DisplayName("正常编辑 → 回写校对信息后复跑契约校验并返回结果")
    void updatesCorrectionAndReturnsFreshValidation() {
        when(support.requireHistory(SESSION_KEY)).thenReturn(new ExamHistory());
        List<ExamQuestion> questions = List.of(new ExamQuestion());
        when(support.listQuestions(SESSION_KEY)).thenReturn(questions);
        ExamContractValidator.Result pass = new ExamContractValidator.Result(true, List.of());
        when(support.validate(eq(questions), isNull())).thenReturn(pass);

        ExamContractValidator.Result result = exe.execute(SESSION_KEY, 3, "B", "考查第二段主旨", 10);

        assertThat(result.pass()).isTrue();
        verify(examQuestionGateway).updateCorrection(SESSION_KEY, 3, "B", "考查第二段主旨", 10);
    }

    @Test
    @DisplayName("题号为 null → 非法参数，先于试卷加载与回写被拦截")
    void nullQuestionNumberRejectedBeforeAnyIo() {
        assertThatThrownBy(() -> exe.execute(SESSION_KEY, null, "A", "解析", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("题号不能为空");
        verify(support, never()).requireHistory(anyString());
        verify(examQuestionGateway, never())
                .updateCorrection(anyString(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("试卷不存在 → requireHistory 异常原样上抛，不回写不改校验")
    void missingPaperPropagatesAndSkipsUpdate() {
        when(support.requireHistory(SESSION_KEY))
                .thenThrow(new IllegalArgumentException("试卷不存在: " + SESSION_KEY));

        assertThatThrownBy(() -> exe.execute(SESSION_KEY, 1, "A", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("试卷不存在");
        verify(examQuestionGateway, never())
                .updateCorrection(anyString(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("空白会话标识 → requireHistory 抛非法参数，不回写")
    void blankSessionKeyRejectedBySupport() {
        when(support.requireHistory(" ")).thenThrow(new IllegalArgumentException("试卷标识不能为空"));

        assertThatThrownBy(() -> exe.execute(" ", 1, "A", "解析", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("试卷标识不能为空");
        verify(examQuestionGateway, never())
                .updateCorrection(anyString(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("已发布（PUBLISHED）试卷当前仍可被就地改标准答案（TODO(行为可疑): 无状态门禁，改答案可能追溯性影响已复核判分，应拒绝或提示先撤回发布）")
    void publishedPaperIsStillEditable() {
        ExamHistory published = new ExamHistory();
        published.setStatus(ExamHistory.STATUS_PUBLISHED);
        when(support.requireHistory(SESSION_KEY)).thenReturn(published);
        when(support.listQuestions(SESSION_KEY)).thenReturn(List.of());
        when(support.validate(anyList(), isNull()))
                .thenReturn(new ExamContractValidator.Result(true, List.of()));

        ExamContractValidator.Result result = exe.execute(SESSION_KEY, 1, "C", null, null);

        assertThat(result.pass()).isTrue();
        verify(examQuestionGateway)
                .updateCorrection(eq(SESSION_KEY), eq(1), eq("C"), isNull(), isNull());
    }
}
