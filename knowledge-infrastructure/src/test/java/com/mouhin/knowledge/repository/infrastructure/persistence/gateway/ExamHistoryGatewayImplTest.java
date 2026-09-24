package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.ExamHistoryDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.ExamHistoryMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link ExamHistoryGatewayImpl} 契约单测：锁定 CRUD 转换、按 sessionId 查询与计数透传。
 *
 * <p>Mapper 以 {@code mock(ExamHistoryMapper.class)} 手工注入，不启 Spring。
 *
 * @author mouhinU
 * @date 2026-09-24 18:21:00
 */
@DisplayName("AI 出卷历史仓储实现契约单测 (ExamHistoryGatewayImpl)")
class ExamHistoryGatewayImplTest {

    private final ExamHistoryMapper examHistoryMapper = mock(ExamHistoryMapper.class);
    private final ExamHistoryGatewayImpl gateway = new ExamHistoryGatewayImpl(examHistoryMapper);

    private ExamHistoryDO doOf(Long id, String sessionId) {
        ExamHistoryDO doObj = new ExamHistoryDO();
        doObj.setId(id);
        doObj.setSessionId(sessionId);
        doObj.setTopic("数据结构");
        doObj.setStatus("PUBLISHED");
        return doObj;
    }

    @Test
    @DisplayName("save → Entity→DO 回写 + 主键回填")
    void saveWritesAndBackfillsId() {
        ExamHistory history = new ExamHistory();
        history.setSessionId("sess-1");
        history.setTopic("操作系统");
        history.setStatus("DRAFT");

        when(examHistoryMapper.insert(any(ExamHistoryDO.class)))
                .thenAnswer(
                        inv -> {
                            inv.getArgument(0, ExamHistoryDO.class).setId(66L);
                            return 1;
                        });

        gateway.save(history);

        ArgumentCaptor<ExamHistoryDO> cap = ArgumentCaptor.forClass(ExamHistoryDO.class);
        verify(examHistoryMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getSessionId()).isEqualTo("sess-1");
        assertThat(cap.getValue().getTopic()).isEqualTo("操作系统");
        assertThat(history.getId()).isEqualTo(66L);
    }

    @Test
    @DisplayName("findById 命中 → DO→Entity 映射；未命中回 empty")
    void findByIdMapsAndHandlesMissing() {
        when(examHistoryMapper.selectById(1L)).thenReturn(doOf(1L, "sess-1"));
        Optional<ExamHistory> hit = gateway.findById(1L);
        assertThat(hit).isPresent();
        assertThat(hit.get().getTopic()).isEqualTo("数据结构");

        when(examHistoryMapper.selectById(99L)).thenReturn(null);
        assertThat(gateway.findById(99L)).isEmpty();
    }

    @Test
    @DisplayName("findBySessionId → 条件查询命中转 Entity；未命中回 empty")
    void findBySessionIdMapsAndHandlesMissing() {
        when(examHistoryMapper.selectOne(any())).thenReturn(doOf(2L, "sess-2"));
        assertThat(gateway.findBySessionId("sess-2")).isPresent();

        when(examHistoryMapper.selectOne(any())).thenReturn(null);
        assertThat(gateway.findBySessionId("ghost")).isEmpty();
    }

    @Test
    @DisplayName("listRecent → selectList 批量转 Entity")
    void listRecentMapsEveryRow() {
        when(examHistoryMapper.selectList(any())).thenReturn(List.of(doOf(1L, "a"), doOf(2L, "b")));

        List<ExamHistory> recent = gateway.listRecent(10);

        assertThat(recent).hasSize(2);
        assertThat(recent).extracting(ExamHistory::getSessionId).containsExactly("a", "b");
    }

    @Test
    @DisplayName("countAll → selectCount(null) 透传")
    void countAllReturnsSelectCount() {
        when(examHistoryMapper.selectCount(any())).thenReturn(12L);
        assertThat(gateway.countAll()).isEqualTo(12L);
    }
}
