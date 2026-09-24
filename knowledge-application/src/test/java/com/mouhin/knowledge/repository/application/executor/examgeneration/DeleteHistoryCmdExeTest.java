package com.mouhin.knowledge.repository.application.executor.examgeneration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 删除出卷历史记录执行器单测：本执行器是「零门禁直删」—— （1）execute 原样委派 {@code
 * examHistoryGateway.deleteBySessionId}；（2）不做任何前置查询 / 状态判断， 已发布与已作废试卷的历史同样会被删除（幂等与状态门禁均由
 * Gateway/SQL 层承担）； （3）重复调用不去重，删除次数等于调用次数；（4）Gateway 异常原样上抛。
 *
 * @author mouhinU
 * @date 2026-09-24 18:15:00
 */
@DisplayName("删除出卷历史执行器 (DeleteHistoryCmdExe)")
class DeleteHistoryCmdExeTest {

    private static final String SESSION_KEY = "sess-del-1";

    private final ExamHistoryGateway examHistoryGateway = mock(ExamHistoryGateway.class);
    private final DeleteHistoryCmdExe exe = new DeleteHistoryCmdExe(examHistoryGateway);

    @Test
    @DisplayName("execute → 原样委派 deleteBySessionId，且不触发任何前置查询")
    void delegatesDeleteWithoutPrequery() {
        exe.execute(SESSION_KEY);

        verify(examHistoryGateway, times(1)).deleteBySessionId(SESSION_KEY);
        verify(examHistoryGateway, never()).findBySessionId(anyString());
        verify(examHistoryGateway, never()).findById(any());
    }

    @Test
    @DisplayName(
            "空白 / null 会话标识 → 执行器不校验，直接透传给 Gateway（TODO(行为可疑): 参数校验缺失，非法标识的失败信号完全依赖 Gateway 实现）")
    void blankSessionKeyPassedThrough() {
        assertThatCode(() -> exe.execute("  ")).doesNotThrowAnyException();
        assertThatCode(() -> exe.execute(null)).doesNotThrowAnyException();

        verify(examHistoryGateway, times(1)).deleteBySessionId("  ");
        verify(examHistoryGateway, times(1)).deleteBySessionId(null);
    }

    @Test
    @DisplayName("重复删除同一会话 → 执行器层不去重，两次调用即两次委派（幂等语义由 Gateway 的软删 / DELETE SQL 承担）")
    void repeatedCallsDelegateTwice() {
        exe.execute(SESSION_KEY);
        exe.execute(SESSION_KEY);

        verify(examHistoryGateway, times(2)).deleteBySessionId(SESSION_KEY);
    }

    @Test
    @DisplayName("Gateway 抛异常 → 原样上抛，执行器不吞错")
    void gatewayFailurePropagates() {
        doThrow(new IllegalStateException("db down"))
                .when(examHistoryGateway)
                .deleteBySessionId(SESSION_KEY);

        assertThatThrownBy(() -> exe.execute(SESSION_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");
    }
}
