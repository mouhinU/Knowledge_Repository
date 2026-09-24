package com.mouhin.knowledge.repository.application.executor.examtaking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 重写场次题目 JSON 执行器单测（ExamUpdateQuestionsJsonCmdExe）。
 *
 * <p>锁定编排：正常回写 questionsJson + updateTime、越权访问拒绝、场次不存在拒绝。
 *
 * <p>注：源码为「修复旧数据选项解析」用途，仅经 {@code resolveSession} 做归属校验， 未对场次状态（是否已交卷）或 JSON 合法性设门禁，据实断言当前行为。
 *
 * @author mouhinU
 * @date 2026-09-24 17:27:38
 */
@DisplayName("重写题目 JSON 执行器 (ExamUpdateQuestionsJsonCmdExe)")
class ExamUpdateQuestionsJsonCmdExeTest {

    private static final String SESSION_KEY = "sess-json-1";
    private static final String TOKEN = "stu-token";

    private final ExamSessionGateway sessionGateway = mock(ExamSessionGateway.class);
    private final ExamTakingSupport support = mock(ExamTakingSupport.class);
    private final ExamUpdateQuestionsJsonCmdExe exe =
            new ExamUpdateQuestionsJsonCmdExe(sessionGateway, support);

    @Test
    @DisplayName("正常回写 → 更新 questionsJson 与 updateTime")
    void updatesQuestionsJson() {
        ExamSession session = new ExamSession();
        session.setId(100L);
        session.setStatus("IN_PROGRESS");
        when(support.resolveSession(SESSION_KEY, TOKEN)).thenReturn(session);

        exe.execute(SESSION_KEY, TOKEN, "[{\"index\":1}]");

        ArgumentCaptor<ExamSession> cap = ArgumentCaptor.forClass(ExamSession.class);
        verify(sessionGateway).update(cap.capture());
        assertThat(cap.getValue().getQuestionsJson()).isEqualTo("[{\"index\":1}]");
        assertThat(cap.getValue().getUpdateTime()).isNotNull();
    }

    @Test
    @DisplayName("越权访问他人场次 → resolveSession 抛'无权访问'，不落库")
    void rejectsForeignSession() {
        when(support.resolveSession(SESSION_KEY, TOKEN))
                .thenThrow(new IllegalArgumentException("无权访问此考试"));

        assertThatThrownBy(() -> exe.execute(SESSION_KEY, TOKEN, "[]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无权");
        verify(sessionGateway, never()).update(any());
    }

    @Test
    @DisplayName("场次不存在 → resolveSession 抛'不存在'，不落库")
    void rejectsUnknownSession() {
        when(support.resolveSession(SESSION_KEY, TOKEN))
                .thenThrow(new IllegalArgumentException("考试场次不存在: " + SESSION_KEY));

        assertThatThrownBy(() -> exe.execute(SESSION_KEY, TOKEN, "[]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在");
        verify(sessionGateway, never()).update(any());
    }

    @Test
    @DisplayName("已提交场次仍允许回写（源码无状态门禁） // TODO(行为可疑): 修复旧数据入口未限制场次状态")
    void allowsUpdateAfterSubmit() {
        ExamSession session = new ExamSession();
        session.setId(101L);
        session.setStatus("SUBMITTED");
        when(support.resolveSession(SESSION_KEY, TOKEN)).thenReturn(session);

        exe.execute(SESSION_KEY, TOKEN, "[{\"index\":2}]");

        verify(sessionGateway).update(any());
    }
}
