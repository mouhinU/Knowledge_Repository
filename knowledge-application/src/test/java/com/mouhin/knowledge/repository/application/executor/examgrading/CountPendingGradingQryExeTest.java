package com.mouhin.knowledge.repository.application.executor.examgrading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 待评分数量统计查询执行器单测：锁定口径固定为 SUBMITTED、结果直接透传（含 0 与负数语义原样返回）。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("待评分数量统计查询执行器 (CountPendingGradingQryExe)")
class CountPendingGradingQryExeTest {

    private final ExamSessionGateway examSessionGateway = mock(ExamSessionGateway.class);
    private final CountPendingGradingQryExe exe = new CountPendingGradingQryExe(examSessionGateway);

    @Test
    @DisplayName("统计口径固定 'SUBMITTED' 并透传计数")
    void countsSubmittedAndReturnsRaw() {
        when(examSessionGateway.countByStatus("SUBMITTED")).thenReturn(12L);

        assertThat(exe.execute()).isEqualTo(12L);
        verify(examSessionGateway).countByStatus("SUBMITTED");
    }

    @Test
    @DisplayName("无待评分 → 返回 0")
    void zeroWhenNonePending() {
        when(examSessionGateway.countByStatus("SUBMITTED")).thenReturn(0L);

        assertThat(exe.execute()).isZero();
    }

    @Test
    @DisplayName("多次调用不缓存，每次实时查网关")
    void noCachingBetweenCalls() {
        when(examSessionGateway.countByStatus("SUBMITTED")).thenReturn(1L, 2L);

        assertThat(exe.execute()).isEqualTo(1L);
        assertThat(exe.execute()).isEqualTo(2L);
        verify(examSessionGateway, org.mockito.Mockito.times(2)).countByStatus("SUBMITTED");
    }
}
