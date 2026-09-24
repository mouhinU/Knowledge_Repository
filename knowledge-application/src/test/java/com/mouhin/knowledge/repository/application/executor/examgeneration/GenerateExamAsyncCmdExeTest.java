package com.mouhin.knowledge.repository.application.executor.examgeneration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 异步出卷命令执行器单测：执行器是薄分派层——锁定入参完整透传给 {@link ExamGenerationSupport#generateExamAsync}， 且 support
 * 抛出的异常原样冒泡（分派层不做吞并）。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("异步出卷命令执行器 (GenerateExamAsyncCmdExe)")
class GenerateExamAsyncCmdExeTest {

    private final ExamGenerationSupport support = mock(ExamGenerationSupport.class);
    private final GenerateExamAsyncCmdExe exe = new GenerateExamAsyncCmdExe(support);

    @Test
    @DisplayName("全部入参原样透传给 support.generateExamAsync")
    void delegatesAllArguments() {
        Permission permission = new Permission("u-1", "d-1", "TEACHER", false);
        BlackboardProgressCallback callback = mock(BlackboardProgressCallback.class);
        ExamPlan plan = new ExamPlan();

        exe.execute("二次函数", "MEDIUM", "10", permission, callback, "sess-1", "数学", "初中", plan, true);

        verify(support)
                .generateExamAsync(
                        eq("二次函数"),
                        eq("MEDIUM"),
                        eq("10"),
                        eq(permission),
                        eq(callback),
                        eq("sess-1"),
                        eq("数学"),
                        eq("初中"),
                        eq(plan),
                        eq(true));
    }

    @Test
    @DisplayName("plan 为 null（未确认方案的兜底路径）也原样透传")
    void nullPlanPassedThrough() {
        Permission permission = new Permission("u-1", null, null, true);
        BlackboardProgressCallback callback = mock(BlackboardProgressCallback.class);

        exe.execute("topic", "EASY", "5", permission, callback, "s2", null, null, null, false);

        verify(support)
                .generateExamAsync(
                        eq("topic"),
                        eq("EASY"),
                        eq("5"),
                        eq(permission),
                        eq(callback),
                        eq("s2"),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(false));
    }

    @Test
    @DisplayName("support 抛异常 → 原样冒泡，不在分派层吞并")
    void supportExceptionPropagates() {
        Permission permission = new Permission("u-1", null, null, false);
        doThrow(new IllegalStateException("agent pool exhausted"))
                .when(support)
                .generateExamAsync(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyBoolean());

        assertThatThrownBy(
                        () ->
                                exe.execute(
                                        "t",
                                        "EASY",
                                        "5",
                                        permission,
                                        null,
                                        "s3",
                                        null,
                                        null,
                                        null,
                                        false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agent pool exhausted");
    }
}
