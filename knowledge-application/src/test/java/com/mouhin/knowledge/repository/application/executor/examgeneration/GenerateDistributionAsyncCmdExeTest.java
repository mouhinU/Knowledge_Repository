package com.mouhin.knowledge.repository.application.executor.examgeneration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 异步题型分布方案生成命令执行器单测：薄分派层——锁定入参完整透传给 {@link ExamGenerationSupport#generateDistributionAsync}，异常原样冒泡。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("异步题型分布方案生成命令执行器 (GenerateDistributionAsyncCmdExe)")
class GenerateDistributionAsyncCmdExeTest {

    private final ExamGenerationSupport support = mock(ExamGenerationSupport.class);
    private final GenerateDistributionAsyncCmdExe exe =
            new GenerateDistributionAsyncCmdExe(support);

    @Test
    @DisplayName("全部入参原样透传给 support.generateDistributionAsync")
    void delegatesAllArguments() {
        Permission permission = new Permission("u-1", "d-1", "TEACHER", false);
        BlackboardProgressCallback callback = mock(BlackboardProgressCallback.class);

        exe.execute("sess-9", "三角函数", "HARD", "高中", "数学", permission, callback);

        verify(support)
                .generateDistributionAsync(
                        eq("sess-9"),
                        eq("三角函数"),
                        eq("HARD"),
                        eq("高中"),
                        eq("数学"),
                        eq(permission),
                        eq(callback));
    }

    @Test
    @DisplayName("category / callback 为 null 也原样透传（null 语义由 support 决定）")
    void nullArgumentsPassedThrough() {
        Permission permission = new Permission("u-2", null, null, true);

        exe.execute("s2", "topic", "EASY", null, null, permission, null);

        verify(support)
                .generateDistributionAsync(
                        eq("s2"),
                        eq("topic"),
                        eq("EASY"),
                        eq(null),
                        eq(null),
                        eq(permission),
                        eq(null));
    }

    @Test
    @DisplayName("support 抛异常 → 原样冒泡，分派层不吞并")
    void supportExceptionPropagates() {
        doThrow(new IllegalArgumentException("Query must not be blank"))
                .when(support)
                .generateDistributionAsync(
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        any(),
                        any(Permission.class),
                        any(BlackboardProgressCallback.class));

        assertThatThrownBy(
                        () ->
                                exe.execute(
                                        "s3",
                                        "t",
                                        "EASY",
                                        "初中",
                                        null,
                                        new Permission("u", null, null, false),
                                        mock(BlackboardProgressCallback.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Query must not be blank");
    }
}
