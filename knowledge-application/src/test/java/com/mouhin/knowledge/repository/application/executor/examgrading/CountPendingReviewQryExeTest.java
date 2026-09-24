package com.mouhin.knowledge.repository.application.executor.examgrading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 待复核数量统计查询执行器单测：锁定委托口径（countPendingReview 的状态组合语义在网关侧）、结果透传与实时查询。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("待复核数量统计查询执行器 (CountPendingReviewQryExe)")
class CountPendingReviewQryExeTest {

    private final ExamSessionGateway examSessionGateway = mock(ExamSessionGateway.class);
    private final CountPendingReviewQryExe exe = new CountPendingReviewQryExe(examSessionGateway);

    @Test
    @DisplayName("委托网关 countPendingReview 并原样返回")
    void delegatesAndReturnsRaw() {
        when(examSessionGateway.countPendingReview()).thenReturn(7L);

        assertThat(exe.execute()).isEqualTo(7L);
        verify(examSessionGateway).countPendingReview();
    }

    @Test
    @DisplayName("无待复核 → 返回 0")
    void zeroWhenNonePending() {
        when(examSessionGateway.countPendingReview()).thenReturn(0L);

        assertThat(exe.execute()).isZero();
    }

    @Test
    @DisplayName("每次调用实时查询，不做缓存")
    void noCachingBetweenCalls() {
        when(examSessionGateway.countPendingReview()).thenReturn(3L, 5L);

        assertThat(exe.execute()).isEqualTo(3L);
        assertThat(exe.execute()).isEqualTo(5L);
        verify(examSessionGateway, org.mockito.Mockito.times(2)).countPendingReview();
    }
}
