package com.mouhin.knowledge.repository.application.executor.examgeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 可用考试列表按学生过滤单测： （1）token 有效 → 剔除该考生已考过的 examHistoryId； （2）token 为空 / 无效 → 走原全量语义（不查考生场次）；
 * （3）已考卷过多导致过滤后不足 limit 时仍从放大窗口内取满。
 *
 * @author mouhinU
 * @date 2026-09-20
 */
@DisplayName("可用考试按学生过滤 (ListPublishedHistoryQryExe)")
class ListPublishedHistoryQryExeStudentFilterTest {

    private final ExamHistoryGateway historyGateway = mock(ExamHistoryGateway.class);
    private final ExamSessionGateway sessionGateway = mock(ExamSessionGateway.class);
    private final StudentGateway studentGateway = mock(StudentGateway.class);
    private final ListPublishedHistoryQryExe exe =
            new ListPublishedHistoryQryExe(historyGateway, sessionGateway, studentGateway);

    private static ExamHistory published(long id) {
        ExamHistory h = new ExamHistory();
        h.setId(id);
        h.setSessionId("sess-" + id);
        h.setTopic("T" + id);
        h.setStatus(ExamHistory.STATUS_PUBLISHED);
        return h;
    }

    @Test
    @DisplayName("学生已考 id=2 的卷 → 返回列表剔除 id=2")
    void filtersAlreadyTaken() {
        Student s = new Student();
        s.setId(7L);
        s.setSessionToken("tok");
        s.setTokenExpiry(java.time.LocalDateTime.now().plusHours(1));
        when(studentGateway.findBySessionToken(eq("tok"))).thenReturn(Optional.of(s));
        when(historyGateway.listPublished(anyInt()))
                .thenReturn(List.of(published(1), published(2), published(3)));
        when(sessionGateway.listExamHistoryIdsByStudentId(7L)).thenReturn(List.of(2L));

        List<ExamHistoryDTO> result = exe.execute(3, "tok");

        assertEquals(List.of(1L, 3L), result.stream().map(ExamHistoryDTO::getId).toList());
    }

    @Test
    @DisplayName("token 为空 → 全量返回已发布列表，不查询考生场次")
    void noTokenFallsBackToUnfiltered() {
        when(historyGateway.listPublished(5)).thenReturn(List.of(published(10), published(11)));

        List<ExamHistoryDTO> result = exe.execute(5, null);

        assertEquals(2, result.size());
        verify(sessionGateway, never())
                .listExamHistoryIdsByStudentId(org.mockito.ArgumentMatchers.anyLong());
        verify(studentGateway, never())
                .findBySessionToken(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("token 无效（找不到学生）→ 走全量分支")
    void invalidTokenFallsBack() {
        when(studentGateway.findBySessionToken("bad")).thenReturn(Optional.empty());
        when(historyGateway.listPublished(4)).thenReturn(List.of(published(20)));

        List<ExamHistoryDTO> result = exe.execute(4, "bad");

        assertEquals(1, result.size());
        verify(sessionGateway, never())
                .listExamHistoryIdsByStudentId(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("过滤后可能不足 limit → 从 over-fetch 窗口内取满，返回结果不超过 limit")
    void overFetchTrimsToLimit() {
        Student s = new Student();
        s.setId(9L);
        s.setSessionToken("t");
        s.setTokenExpiry(java.time.LocalDateTime.now().plusHours(1));
        when(studentGateway.findBySessionToken("t")).thenReturn(Optional.of(s));
        // 4 条 PUBLISHED 中第 1 条被考过，窗口 limit=2 会放大到 8 后过滤剩 3，再截断到 2 条
        when(historyGateway.listPublished(8))
                .thenReturn(List.of(published(1), published(2), published(3), published(4)));
        when(sessionGateway.listExamHistoryIdsByStudentId(9L)).thenReturn(List.of(1L));

        List<ExamHistoryDTO> result = exe.execute(2, "t");

        assertEquals(2, result.size());
        assertEquals(List.of(2L, 3L), result.stream().map(ExamHistoryDTO::getId).toList());
    }
}
