package com.mouhin.knowledge.repository.application.executor.examgrading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.client.dto.ExamSessionDTO;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 待评分场次列表查询执行器单测：锁定固定状态口径（SUBMITTED）、limit/offset 原样透传分页、 DTO 字段转换与空结果语义。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("待评分场次列表查询执行器 (ListPendingGradingSessionsQryExe)")
class ListPendingGradingSessionsQryExeTest {

    private final ExamSessionGateway examSessionGateway = mock(ExamSessionGateway.class);
    private final ListPendingGradingSessionsQryExe exe =
            new ListPendingGradingSessionsQryExe(examSessionGateway);

    @Test
    @DisplayName("状态口径固定 SUBMITTED，limit/offset 原样透传")
    void passesPagingThroughWithSubmittedStatus() {
        when(examSessionGateway.listByStatus(eq("SUBMITTED"), eq(20), eq(40)))
                .thenReturn(List.of());

        exe.execute(20, 40);

        verify(examSessionGateway).listByStatus("SUBMITTED", 20, 40);
    }

    @Test
    @DisplayName("正常转换：场次核心字段映射为 DTO")
    void convertsSessionsToDto() {
        ExamSession session = new ExamSession();
        session.setId(1L);
        session.setSessionKey("sk-1");
        session.setStudentId(7L);
        session.setTopic("函数");
        session.setStatus("SUBMITTED");
        session.setSubmitTime(LocalDateTime.of(2026, 9, 20, 10, 0));
        session.setVoided(false);
        when(examSessionGateway.listByStatus("SUBMITTED", 10, 0)).thenReturn(List.of(session));

        List<ExamSessionDTO> result = exe.execute(10, 0);

        assertThat(result)
                .singleElement()
                .satisfies(
                        dto -> {
                            assertThat(dto.getId()).isEqualTo(1L);
                            assertThat(dto.getSessionKey()).isEqualTo("sk-1");
                            assertThat(dto.getStudentId()).isEqualTo(7L);
                            assertThat(dto.getTopic()).isEqualTo("函数");
                            assertThat(dto.getStatus()).isEqualTo("SUBMITTED");
                            assertThat(dto.getSubmitTime())
                                    .isEqualTo(LocalDateTime.of(2026, 9, 20, 10, 0));
                            assertThat(dto.isVoided()).isFalse();
                        });
    }

    @Test
    @DisplayName("无待评分场次 → 返回空列表")
    void emptyReturnsEmptyList() {
        when(examSessionGateway.listByStatus("SUBMITTED", 5, 0)).thenReturn(List.of());

        assertThat(exe.execute(5, 0)).isEmpty();
    }
}
