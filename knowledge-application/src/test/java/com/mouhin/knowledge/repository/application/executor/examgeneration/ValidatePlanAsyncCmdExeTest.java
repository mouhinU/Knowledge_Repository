package com.mouhin.knowledge.repository.application.executor.examgeneration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 方案异步校验命令执行器单测：薄分派层——锁定 sessionId / plan / callback 完整透传给 {@link
 * ExamGenerationSupport#validatePlanAsync}，plan 为 null 不做前置校验（判空语义在 support），异常原样冒泡。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("方案异步校验命令执行器 (ValidatePlanAsyncCmdExe)")
class ValidatePlanAsyncCmdExeTest {

    private final ExamGenerationSupport support = mock(ExamGenerationSupport.class);
    private final ValidatePlanAsyncCmdExe exe = new ValidatePlanAsyncCmdExe(support);

    @Test
    @DisplayName("入参原样透传给 support.validatePlanAsync")
    void delegatesAllArguments() {
        ExamPlan plan = new ExamPlan();
        plan.setTotalFullMark(100);
        BlackboardProgressCallback callback = mock(BlackboardProgressCallback.class);

        exe.execute("sess-v", plan, callback);

        verify(support).validatePlanAsync(eq("sess-v"), eq(plan), eq(callback));
    }

    @Test
    @DisplayName("plan / callback 为 null → 执行器不拦截，原样透传")
    void nullArgumentsPassedThrough() {
        exe.execute("s2", null, null);

        verify(support).validatePlanAsync(eq("s2"), eq(null), eq(null));
    }

    @Test
    @DisplayName("support 抛异常 → 原样冒泡")
    void supportExceptionPropagates() {
        doThrow(new IllegalStateException("validator busy"))
                .when(support)
                .validatePlanAsync(any(), any(), any());

        assertThatThrownBy(() -> exe.execute("s3", new ExamPlan(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("validator busy");
    }
}
