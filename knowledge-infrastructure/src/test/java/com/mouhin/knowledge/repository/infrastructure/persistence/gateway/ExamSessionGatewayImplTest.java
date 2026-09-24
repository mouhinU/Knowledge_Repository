package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.ExamSessionDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.ExamSessionMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link ExamSessionGatewayImpl} 契约单测：锁定 CRUD 转换、按状态计数与空集合/空参短路。
 *
 * <p>Mapper 以 {@code mock(ExamSessionMapper.class)} 手工注入，不启 Spring。
 *
 * <p>注：{@code casUpdateStatus} / {@code claimForGrading} / {@code touchGradingHeartbeat} / {@code
 * completeGrading} / {@code releaseGradingToSubmitted} / {@code reclaimStuckGrading} / {@code
 * markVoidedByExamHistoryId} 均依赖 {@code LambdaUpdateWrapper.set(...)}，需 TableInfo 缓存，其并发认领/心跳语义交由
 * {@code ExamGradingConcurrencyTest} 与 E2E 覆盖，本类不建测。
 *
 * @author mouhinU
 * @date 2026-09-24 18:21:00
 */
@DisplayName("考试场次仓储实现契约单测 (ExamSessionGatewayImpl)")
class ExamSessionGatewayImplTest {

    private final ExamSessionMapper examSessionMapper = mock(ExamSessionMapper.class);
    private final ExamSessionGatewayImpl gateway = new ExamSessionGatewayImpl(examSessionMapper);

    private ExamSessionDO doOf(Long id, Long studentId, String status) {
        ExamSessionDO doObj = new ExamSessionDO();
        doObj.setId(id);
        doObj.setSessionKey("sk-" + id);
        doObj.setStudentId(studentId);
        doObj.setStatus(status);
        doObj.setVoided(Boolean.FALSE);
        return doObj;
    }

    @Test
    @DisplayName("findById 命中 → DO→Entity 字段映射；未命中回 empty")
    void findByIdMapsAndHandlesMissing() {
        when(examSessionMapper.selectById(1L)).thenReturn(doOf(1L, 100L, "SUBMITTED"));
        Optional<ExamSession> hit = gateway.findById(1L);
        assertThat(hit).isPresent();
        assertThat(hit.get().getStudentId()).isEqualTo(100L);
        assertThat(hit.get().getStatus()).isEqualTo("SUBMITTED");
        assertThat(hit.get().isVoided()).isFalse();

        when(examSessionMapper.selectById(99L)).thenReturn(null);
        assertThat(gateway.findById(99L)).isEmpty();
    }

    @Test
    @DisplayName("save → Entity→DO 回写 + 主键回填；save(null) 抛 NPE")
    void saveWritesAndBackfillsId() {
        ExamSession session = new ExamSession();
        session.setSessionKey("sk-9");
        session.setStudentId(200L);
        session.setStatus("ONGOING");

        when(examSessionMapper.insert(any(ExamSessionDO.class)))
                .thenAnswer(
                        inv -> {
                            inv.getArgument(0, ExamSessionDO.class).setId(9L);
                            return 1;
                        });

        gateway.save(session);

        ArgumentCaptor<ExamSessionDO> cap = ArgumentCaptor.forClass(ExamSessionDO.class);
        verify(examSessionMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getSessionKey()).isEqualTo("sk-9");
        assertThat(cap.getValue().getStudentId()).isEqualTo(200L);
        assertThat(session.getId()).isEqualTo(9L);

        assertThatThrownBy(() -> gateway.save(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("findBySessionKey → 条件查询命中转 Entity")
    void findBySessionKeyUsesWrapper() {
        when(examSessionMapper.selectOne(any())).thenReturn(doOf(3L, 100L, "AI_GRADED"));
        Optional<ExamSession> hit = gateway.findBySessionKey("sk-3");
        assertThat(hit).isPresent();
        assertThat(hit.get().getStatus()).isEqualTo("AI_GRADED");
    }

    @Test
    @DisplayName("listByStudentIdAndStatuses 空/ null 状态集合 → 短路返回空，不查库")
    void listByStudentIdAndStatusesShortCircuitsOnEmpty() {
        assertThat(gateway.listByStudentIdAndStatuses(1L, List.of())).isEmpty();
        assertThat(gateway.listByStudentIdAndStatuses(1L, null)).isEmpty();
        verify(examSessionMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("listByStudentId → selectList 批量转 Entity；countByStatus → selectCount 透传")
    void listAndCountDelegateToMapper() {
        when(examSessionMapper.selectList(any()))
                .thenReturn(List.of(doOf(1L, 100L, "SUBMITTED"), doOf(2L, 100L, "AI_GRADED")));
        assertThat(gateway.listByStudentId(100L)).hasSize(2);

        when(examSessionMapper.selectCount(any())).thenReturn(4L);
        assertThat(gateway.countByStatus("SUBMITTED")).isEqualTo(4L);
    }
}
