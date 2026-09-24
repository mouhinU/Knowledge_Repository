package com.mouhin.knowledge.repository.application.executor.examgeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.client.dto.ExamHistoryDTO;
import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 已发布试卷列表查询执行器单测（主查询分支）：锁定 limit≤0 归一为 20、旧签名 execute(limit) 等价于未登录全量语义、 登录但无已考记录时走 over-fetch
 * 窗口取满、DTO 字段转换完整。学生过滤细分支见 {@code ListPublishedHistoryQryExeStudentFilterTest}。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("已发布试卷列表查询执行器 (ListPublishedHistoryQryExe)")
class ListPublishedHistoryQryExeTest {

    private final ExamHistoryGateway historyGateway = mock(ExamHistoryGateway.class);
    private final ExamSessionGateway sessionGateway = mock(ExamSessionGateway.class);
    private final StudentGateway studentGateway = mock(StudentGateway.class);
    private final ListPublishedHistoryQryExe exe =
            new ListPublishedHistoryQryExe(historyGateway, sessionGateway, studentGateway);

    private ExamHistory published(long id) {
        ExamHistory h = new ExamHistory();
        h.setId(id);
        h.setSessionId("sess-" + id);
        h.setTopic("T" + id);
        h.setStatus(ExamHistory.STATUS_PUBLISHED);
        return h;
    }

    @Test
    @DisplayName("limit≤0 → 归一为 20 并透传给网关")
    void nonPositiveLimitNormalizedTo20() {
        when(historyGateway.listPublished(20)).thenReturn(List.of(published(1)));

        List<ExamHistoryDTO> result = exe.execute(0, null);

        assertThat(result).hasSize(1);
        verify(historyGateway).listPublished(20);
    }

    @Test
    @DisplayName("旧签名 execute(limit) → 等价未登录全量语义，不触碰学生 / 场次网关")
    void legacyOverloadIsUnfiltered() {
        when(historyGateway.listPublished(5))
                .thenReturn(List.of(published(1), published(2), published(3)));

        List<ExamHistoryDTO> result = exe.execute(5);

        assertThat(result).hasSize(3);
        verify(studentGateway, never()).findBySessionToken(org.mockito.ArgumentMatchers.any());
        verify(sessionGateway, never())
                .listExamHistoryIdsByStudentId(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("登录且无已考记录 → 走 over-fetch 窗口（limit×4），取满 limit 条")
    void loggedInWithoutSessionsUsesOverfetchWindow() {
        Student s = new Student();
        s.setId(3L);
        s.setSessionToken("tok");
        s.setTokenExpiry(LocalDateTime.now().plusHours(1));
        when(studentGateway.findBySessionToken("tok")).thenReturn(Optional.of(s));
        when(historyGateway.listPublished(8))
                .thenReturn(
                        List.of(
                                published(1),
                                published(2),
                                published(3),
                                published(4),
                                published(5)));
        when(sessionGateway.listExamHistoryIdsByStudentId(3L)).thenReturn(List.of());

        List<ExamHistoryDTO> result = exe.execute(2, "tok");

        verify(historyGateway).listPublished(eq(8));
        assertThat(result).extracting(ExamHistoryDTO::getId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("DTO 转换：核心字段（sessionId / topic / status）完整映射")
    void convertsHistoryToDto() {
        ExamHistory h = published(9);
        h.setDifficulty("HARD");
        when(historyGateway.listPublished(1)).thenReturn(List.of(h));

        ExamHistoryDTO dto = exe.execute(1, null).get(0);

        assertThat(dto.getId()).isEqualTo(9L);
        assertThat(dto.getSessionId()).isEqualTo("sess-9");
        assertThat(dto.getTopic()).isEqualTo("T9");
        assertThat(dto.getDifficulty()).isEqualTo("HARD");
        assertThat(dto.getStatus()).isEqualTo(ExamHistory.STATUS_PUBLISHED);
    }

    @Test
    @DisplayName("token 为空白串 → 按未登录处理（不查 sessionToken）")
    void blankTokenTreatedAsAnonymous() {
        when(historyGateway.listPublished(3)).thenReturn(List.of(published(1)));

        assertThat(exe.execute(3, "   ")).hasSize(1);
        verify(studentGateway, never()).findBySessionToken(org.mockito.ArgumentMatchers.any());
    }
}
