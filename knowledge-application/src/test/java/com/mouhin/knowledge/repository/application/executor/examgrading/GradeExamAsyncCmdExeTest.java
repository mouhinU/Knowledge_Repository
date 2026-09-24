package com.mouhin.knowledge.repository.application.executor.examgrading;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mouhin.knowledge.repository.domain.service.ExamGradingProgressCallback;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 异步评分执行器单测：本执行器是 SSE 通道场景下的「异步分派入口」， 只负责把 (sessionId, callback) 原样转交给 {@link
 * ExamGradingSupport#gradeExamAsync}， 认领与评分均发生在虚拟线程中。锁定四条行为：分派一次且回调同实例、
 * 不绕过异步直接同步评分、支撑层异常原样上抛、入参不做任何校验（null 透传）。
 *
 * @author mouhinU
 * @date 2026-09-24 18:15:00
 */
@DisplayName("异步评分分派执行器 (GradeExamAsyncCmdExe)")
class GradeExamAsyncCmdExeTest {

    private static final Long SESSION_ID = 42L;

    private final ExamGradingSupport support = mock(ExamGradingSupport.class);
    private final GradeExamAsyncCmdExe exe = new GradeExamAsyncCmdExe(support);

    @Test
    @DisplayName("execute → 原样分派 gradeExamAsync，回调为同一实例，不走同步评分")
    void delegatesToAsyncEntryOnly() {
        ExamGradingProgressCallback callback = mock(ExamGradingProgressCallback.class);

        exe.execute(SESSION_ID, callback);

        verify(support).gradeExamAsync(eq(SESSION_ID), same(callback));
        verify(support, never()).gradeExamInternal(any(), any());
    }

    @Test
    @DisplayName("支撑层拒绝受理（线程池饱和）→ 异常原样上抛给调用方")
    void rejectedExecutionPropagates() {
        ExamGradingProgressCallback callback = mock(ExamGradingProgressCallback.class);
        doThrow(new RejectedExecutionException("pool shut down"))
                .when(support)
                .gradeExamAsync(SESSION_ID, callback);

        assertThatThrownBy(() -> exe.execute(SESSION_ID, callback))
                .isInstanceOf(RejectedExecutionException.class)
                .hasMessage("pool shut down");
    }

    @Test
    @DisplayName("sessionId=null → 执行器层零校验直接透传（TODO(行为可疑): 缺少入参校验，非法参数延迟到异步线程才暴露，调用方拿不到同步失败信号）")
    void nullSessionIdIsPassedThroughWithoutValidation() {
        ExamGradingProgressCallback callback = mock(ExamGradingProgressCallback.class);

        exe.execute(null, callback);

        verify(support).gradeExamAsync(eq(null), same(callback));
    }

    @Test
    @DisplayName("callback=null → 允许纯后台异步评分，null 回调原样透传")
    void nullCallbackIsPassedThrough() {
        exe.execute(SESSION_ID, null);

        verify(support).gradeExamAsync(eq(SESSION_ID), eq(null));
    }
}
