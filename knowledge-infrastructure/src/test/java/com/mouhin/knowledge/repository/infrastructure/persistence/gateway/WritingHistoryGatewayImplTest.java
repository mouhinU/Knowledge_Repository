package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.model.entity.WritingHistory;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.WritingHistoryDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.WritingHistoryMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link WritingHistoryGatewayImpl} 契约单测：锁定 CRUD 转换、按 sessionId 查询与最近列表映射。
 *
 * <p>Mapper 以 {@code mock(WritingHistoryMapper.class)} 手工注入，不启 Spring。
 *
 * @author mouhinU
 * @date 2026-09-24 18:21:00
 */
@DisplayName("AI 写作历史仓储实现契约单测 (WritingHistoryGatewayImpl)")
class WritingHistoryGatewayImplTest {

    private final WritingHistoryMapper writingHistoryMapper = mock(WritingHistoryMapper.class);
    private final WritingHistoryGatewayImpl gateway =
            new WritingHistoryGatewayImpl(writingHistoryMapper);

    private WritingHistoryDO doOf(Long id, String sessionId) {
        WritingHistoryDO doObj = new WritingHistoryDO();
        doObj.setId(id);
        doObj.setSessionId(sessionId);
        doObj.setQuestion("题目" + sessionId);
        doObj.setStatus("DONE");
        return doObj;
    }

    @Test
    @DisplayName("save → Entity→DO 回写 + 主键回填")
    void saveWritesAndBackfillsId() {
        WritingHistory history = new WritingHistory();
        history.setSessionId("ws-1");
        history.setQuestion("写一首诗");

        when(writingHistoryMapper.insert(any(WritingHistoryDO.class)))
                .thenAnswer(
                        inv -> {
                            inv.getArgument(0, WritingHistoryDO.class).setId(33L);
                            return 1;
                        });

        gateway.save(history);

        ArgumentCaptor<WritingHistoryDO> cap = ArgumentCaptor.forClass(WritingHistoryDO.class);
        verify(writingHistoryMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getSessionId()).isEqualTo("ws-1");
        assertThat(cap.getValue().getQuestion()).isEqualTo("写一首诗");
        assertThat(history.getId()).isEqualTo(33L);
    }

    @Test
    @DisplayName("update → 走 updateById")
    void updateUsesUpdateById() {
        WritingHistory history = new WritingHistory();
        history.setId(33L);
        history.setSessionId("ws-1");
        gateway.update(history);
        verify(writingHistoryMapper, times(1)).updateById(any(WritingHistoryDO.class));
    }

    @Test
    @DisplayName("findBySessionId → 条件查询命中转 Entity；未命中回 empty")
    void findBySessionIdMapsAndHandlesMissing() {
        when(writingHistoryMapper.selectOne(any())).thenReturn(doOf(1L, "ws-1"));
        Optional<WritingHistory> hit = gateway.findBySessionId("ws-1");
        assertThat(hit).isPresent();
        assertThat(hit.get().getQuestion()).isEqualTo("题目ws-1");

        when(writingHistoryMapper.selectOne(any())).thenReturn(null);
        assertThat(gateway.findBySessionId("ghost")).isEmpty();
    }

    @Test
    @DisplayName("listRecent → selectList 批量转 Entity")
    void listRecentMapsEveryRow() {
        when(writingHistoryMapper.selectList(any()))
                .thenReturn(List.of(doOf(1L, "a"), doOf(2L, "b")));

        List<WritingHistory> recent = gateway.listRecent(10);

        assertThat(recent).hasSize(2);
        assertThat(recent).extracting(WritingHistory::getSessionId).containsExactly("a", "b");
    }
}
