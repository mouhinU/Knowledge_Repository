package com.mouhin.knowledge.repository.application.executor.examtaking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.application.executor.examgeneration.ExamQuestionSplitSupport;
import com.mouhin.knowledge.repository.client.dto.ExamSessionDTO;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import com.mouhin.knowledge.repository.domain.service.ExamContractValidator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 即时试卷开考执行器单测（ExamStartWithPaperCmdExe）。
 *
 * <p>锁定即时开考链路的编排与门禁：正常开考落库 IN_PROGRESS 场次并回 sessionId、令牌过期拒绝、 内容校验失败拒绝、结构化回灌失败不阻断开考、缺省主题回退「在线考试」。
 *
 * <p>注：本执行器走「即时卷」路径（不关联出卷历史），因此不存在「未发布 / 已作废 / 时间窗口 / 一人一卷一次」 等历史卷门禁——那组守卫位于 {@code
 * ExamStartFromHistoryCmdExe}，已由 {@code ExamStartFromHistoryGuardTest} 覆盖。
 *
 * @author mouhinU
 * @date 2026-09-24 17:27:38
 */
@DisplayName("即时试卷开考执行器 (ExamStartWithPaperCmdExe)")
class ExamStartWithPaperCmdExeTest {

    private static final String TOKEN = "stu-token";
    private static final String PAPER = "# 一份即时卷\n时长：60 分钟\n";
    private static final String ANSWER_KEY = "1. A\n";
    private static final String RENDERED = "[]";

    private final ExamSessionGateway sessionGateway = mock(ExamSessionGateway.class);
    private final ExamTakingSupport support = mock(ExamTakingSupport.class);
    private final ExamQuestionSplitSupport splitSupport = mock(ExamQuestionSplitSupport.class);
    private final ExamStartWithPaperCmdExe exe =
            new ExamStartWithPaperCmdExe(sessionGateway, support, splitSupport);

    private Student stubValidStudent() {
        Student student = new Student();
        student.setId(7L);
        when(support.resolveStudent(TOKEN)).thenReturn(student);
        return student;
    }

    private void stubRenderPipeline() {
        when(support.renderNoPlan(PAPER)).thenReturn(RENDERED);
        when(support.sumMaxScore(RENDERED)).thenReturn(100);
        when(splitSupport.splitAndPersist(anyString(), eq(PAPER), eq(ANSWER_KEY), any()))
                .thenReturn(
                        new ExamQuestionSplitSupport.SplitOutcome(
                                2, new ExamContractValidator.Result(true, List.of())));
    }

    @Test
    @DisplayName("正常即时开考 → 落 IN_PROGRESS 场次并返回 sessionId")
    void startsExamAndPersistsSession() {
        stubValidStudent();
        stubRenderPipeline();

        ExamSessionDTO dto = exe.execute(TOKEN, PAPER, ANSWER_KEY, "初中数学", "EASY");

        assertThat(dto).isNotNull();
        assertThat(dto.getSessionKey()).isNotBlank();
        assertThat(dto.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(dto.getStudentId()).isEqualTo(7L);
        assertThat(dto.getTopic()).isEqualTo("初中数学");
        assertThat(dto.getTotalScore()).isEqualTo(100);

        ArgumentCaptor<ExamSession> cap = ArgumentCaptor.forClass(ExamSession.class);
        verify(sessionGateway).save(cap.capture());
        ExamSession saved = cap.getValue();
        assertThat(saved.getSessionKey()).isEqualTo(dto.getSessionKey());
        assertThat(saved.getQuestionsJson()).isEqualTo(RENDERED);
        assertThat(saved.getStartTime()).isNotNull();
    }

    @Test
    @DisplayName("考生令牌失效 → resolveStudent 抛异常，不落任何场次")
    void rejectsExpiredToken() {
        when(support.resolveStudent(TOKEN)).thenThrow(new IllegalArgumentException("登录已过期，请重新登录"));

        assertThatThrownBy(() -> exe.execute(TOKEN, PAPER, ANSWER_KEY, "数学", "EASY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("登录已过期");
        verify(sessionGateway, never()).save(any());
    }

    @Test
    @DisplayName("渲染后内容校验不通过 → 抛异常阻断开考，不落库")
    void rejectsInvalidRenderedPaper() {
        stubValidStudent();
        when(support.renderNoPlan(PAPER)).thenReturn(RENDERED);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("试卷内容校验未通过：缺少题目"))
                .when(support)
                .validateOrThrow(RENDERED);

        assertThatThrownBy(() -> exe.execute(TOKEN, PAPER, ANSWER_KEY, "数学", "EASY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("校验未通过");
        verify(sessionGateway, never()).save(any());
    }

    @Test
    @DisplayName("结构化回灌失败 → 不阻断开考，场次仍落库并返回")
    void splitFailureDoesNotBlockStart() {
        stubValidStudent();
        when(support.renderNoPlan(PAPER)).thenReturn(RENDERED);
        when(support.sumMaxScore(RENDERED)).thenReturn(100);
        when(splitSupport.splitAndPersist(anyString(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("切分服务不可用"));

        ExamSessionDTO dto = exe.execute(TOKEN, PAPER, ANSWER_KEY, "数学", "EASY");

        assertThat(dto).isNotNull();
        verify(sessionGateway, times(1)).save(any());
    }

    @Test
    @DisplayName("topic 为空 → 回退默认主题「在线考试」")
    void blankTopicFallsBackToDefault() {
        stubValidStudent();
        stubRenderPipeline();

        exe.execute(TOKEN, PAPER, ANSWER_KEY, null, null);

        ArgumentCaptor<ExamSession> cap = ArgumentCaptor.forClass(ExamSession.class);
        verify(sessionGateway).save(cap.capture());
        assertThat(cap.getValue().getTopic()).isEqualTo("在线考试");
    }
}
