package com.mouhin.knowledge.repository.application.executor.examgeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 同步出卷命令执行器单测：锁定两条路径（按已确认方案 / 按题型数量兜底）分别命中 {@link ExamGenerationSupport#generateExam}
 * 的对应重载，参数完整透传且结果原样返回。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("同步出卷命令执行器 (GenerateExamSyncCmdExe)")
class GenerateExamSyncCmdExeTest {

    private final ExamGenerationSupport support = mock(ExamGenerationSupport.class);
    private final GenerateExamSyncCmdExe exe = new GenerateExamSyncCmdExe(support);

    @Test
    @DisplayName("executeWithPlan → 命中 plan 重载并透传全部参数，返回 support 结果")
    void executeWithPlanDelegatesToPlanOverload() {
        when(support.generateExam(
                        anyString(),
                        anyString(),
                        anyString(),
                        any(ExamPlan.class),
                        any(),
                        anyString()))
                .thenReturn("# 试卷正文");
        Permission permission = new Permission("u-1", "d-1", null, false);
        ExamPlan plan = new ExamPlan();

        String result = exe.executeWithPlan("二次函数", "MEDIUM", "初中", plan, permission, "数学");

        assertThat(result).isEqualTo("# 试卷正文");
        verify(support)
                .generateExam(
                        eq("二次函数"), eq("MEDIUM"), eq("初中"), eq(plan), eq(permission), eq("数学"));
    }

    @Test
    @DisplayName("executeByCounts → 命中六题型数量重载并逐项透传")
    void executeByCountsDelegatesToCountsOverload() {
        when(support.generateExam(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn("# 兜底试卷");
        Permission permission = new Permission("u-1", null, null, true);

        String result = exe.executeByCounts("函数", "EASY", "高中", 3, 2, 5, 4, 1, 0, permission, null);

        assertThat(result).isEqualTo("# 兜底试卷");
        verify(support)
                .generateExam(
                        eq("函数"),
                        eq("EASY"),
                        eq("高中"),
                        eq(3),
                        eq(2),
                        eq(5),
                        eq(4),
                        eq(1),
                        eq(0),
                        eq(permission),
                        eq(null));
    }

    @Test
    @DisplayName("plan 为 null 也原样透传（失败兜底文案由 support 决定，执行器不做校验）")
    void nullPlanPassedThrough() {
        when(support.generateExam(
                        anyString(),
                        anyString(),
                        anyString(),
                        org.mockito.ArgumentMatchers.<ExamPlan>isNull(),
                        any(Permission.class),
                        org.mockito.ArgumentMatchers.<String>isNull()))
                .thenReturn("# 试卷生成失败");

        String result =
                exe.executeWithPlan(
                        "t", "EASY", "初中", null, new Permission("u", null, null, false), null);

        assertThat(result).contains("失败");
        verify(support)
                .generateExam(
                        eq("t"),
                        eq("EASY"),
                        eq("初中"),
                        org.mockito.ArgumentMatchers.<ExamPlan>isNull(),
                        any(Permission.class),
                        org.mockito.ArgumentMatchers.<String>isNull());
    }
}
