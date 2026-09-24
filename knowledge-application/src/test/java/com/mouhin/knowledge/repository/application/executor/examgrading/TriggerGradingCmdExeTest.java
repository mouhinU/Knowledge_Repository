package com.mouhin.knowledge.repository.application.executor.examgrading;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 触发同步评分执行器单测：锁定「仅 SUBMITTED 可触发评分」的入口门禁与分派行为—— （1）SUBMITTED 场次 → 分派 {@code gradeExamInternal} 执行认领
 * + 评分； （2）已进入 GRADING（被其他评分器 CAS 认领）或已到终态的场次 → 拒绝，不触碰评分核心； （3）场次不存在 → 抛非法参数。
 *
 * <p>CAS 抢锁本体在 {@link ExamGradingSupport} 内实现，本测试以状态门禁为其在执行器层的显式投影： 认领失败的场次必然表现为非
 * SUBMITTED，故执行器须先拒后派。
 *
 * @author mouhinU
 * @date 2026-09-24 18:15:00
 */
@DisplayName("触发同步评分执行器 (TriggerGradingCmdExe)")
class TriggerGradingCmdExeTest {

    private static final Long SESSION_ID = 7L;

    private final ExamSessionGateway examSessionGateway = mock(ExamSessionGateway.class);
    private final ExamGradingSupport support = mock(ExamGradingSupport.class);
    private final TriggerGradingCmdExe exe = new TriggerGradingCmdExe(examSessionGateway, support);

    private void stubSession(String status) {
        ExamSession session = new ExamSession();
        session.setId(SESSION_ID);
        session.setStatus(status);
        when(examSessionGateway.findById(SESSION_ID)).thenReturn(Optional.of(session));
    }

    @Test
    @DisplayName("SUBMITTED 场次 → 分派 gradeExamInternal 同步评分（callback=null）")
    void submittedSessionDispatchesGrading() {
        stubSession("SUBMITTED");

        exe.execute(SESSION_ID);

        verify(support).gradeExamInternal(eq(SESSION_ID), any());
    }

    @Test
    @DisplayName("场次不存在 → 抛 IllegalArgumentException，不分派评分")
    void missingSessionRejected() {
        when(examSessionGateway.findById(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exe.execute(SESSION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("考试场次不存在");
        verify(support, never()).gradeExamInternal(any(), any());
    }

    @Test
    @DisplayName("已被认领（GRADING）→ 状态门禁拒绝，不重复触发评分")
    void gradingSessionClaimedByOtherRejected() {
        stubSession("GRADING");

        assertThatThrownBy(() -> exe.execute(SESSION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("仅已交卷")
                .hasMessageContaining("GRADING");
        verify(support, never()).gradeExamInternal(any(), any());
    }

    @Test
    @DisplayName("已到终态（AI_GRADED / IN_PROGRESS）→ 同样拒绝触发")
    void terminalOrUnsubmittedStatesRejected() {
        stubSession("AI_GRADED");
        assertThatThrownBy(() -> exe.execute(SESSION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("仅已交卷");

        stubSession("IN_PROGRESS");
        assertThatThrownBy(() -> exe.execute(SESSION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("仅已交卷");

        verify(support, never()).gradeExamInternal(any(), any());
    }
}
