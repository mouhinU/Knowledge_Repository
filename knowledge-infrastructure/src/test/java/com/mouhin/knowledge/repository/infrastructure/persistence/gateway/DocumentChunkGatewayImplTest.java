package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.DocumentChunkDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.DocumentChunkMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link DocumentChunkGatewayImpl} 契约单测：锁定批量落库主键回填、条件查询与删除/计数委托。
 *
 * <p>Mapper 以 {@code mock(DocumentChunkMapper.class)} 手工注入，不启 Spring。
 *
 * @author mouhinU
 * @date 2026-09-24 18:21:00
 */
@DisplayName("文档分块仓储实现契约单测 (DocumentChunkGatewayImpl)")
class DocumentChunkGatewayImplTest {

    private final DocumentChunkMapper chunkMapper = mock(DocumentChunkMapper.class);
    private final DocumentChunkGatewayImpl gateway = new DocumentChunkGatewayImpl(chunkMapper);

    private DocumentChunkDO doOf(Long id, Integer index) {
        DocumentChunkDO doObj = new DocumentChunkDO();
        doObj.setId(id);
        doObj.setChunkKey("ck-" + index);
        doObj.setDocumentId(1L);
        doObj.setChunkIndex(index);
        doObj.setContent("content-" + index);
        return doObj;
    }

    @Test
    @DisplayName("saveBatch → 逐条 insert 并回填主键与创建时间")
    void saveBatchInsertsEachAndBackfillsId() {
        DocumentChunk c1 = new DocumentChunk();
        c1.setDocumentId(1L);
        c1.setChunkIndex(0);
        DocumentChunk c2 = new DocumentChunk();
        c2.setDocumentId(1L);
        c2.setChunkIndex(1);
        final long[] seq = {100L};
        when(chunkMapper.insert(any(DocumentChunkDO.class)))
                .thenAnswer(
                        inv -> {
                            inv.getArgument(0, DocumentChunkDO.class).setId(seq[0]++);
                            return 1;
                        });

        gateway.saveBatch(List.of(c1, c2));

        verify(chunkMapper, times(2)).insert(any(DocumentChunkDO.class));
        assertThat(c1.getId()).isEqualTo(100L);
        assertThat(c2.getId()).isEqualTo(101L);
        assertThat(c1.getCreatedTime()).isNotNull();
    }

    @Test
    @DisplayName("listByDocumentId → selectList 结果批量转 Entity")
    void listByDocumentIdMapsEveryRow() {
        when(chunkMapper.selectList(any())).thenReturn(List.of(doOf(1L, 0), doOf(2L, 1)));

        List<DocumentChunk> chunks = gateway.listByDocumentId(1L);

        assertThat(chunks).hasSize(2);
        assertThat(chunks).extracting(DocumentChunk::getChunkIndex).containsExactly(0, 1);
    }

    @Test
    @DisplayName("findByChunkKey → 条件查询命中转 Entity；未命中回 empty")
    void findByChunkKeyMapsAndHandlesMissing() {
        when(chunkMapper.selectOne(any())).thenReturn(doOf(1L, 0));
        assertThat(gateway.findByChunkKey("ck-0")).isPresent();

        when(chunkMapper.selectOne(any())).thenReturn(null);
        assertThat(gateway.findByChunkKey("ck-x")).isEmpty();
    }

    @Test
    @DisplayName("deleteByDocumentId → 委托 delete")
    void deleteByDocumentIdDelegatesToDelete() {
        gateway.deleteByDocumentId(1L);
        verify(chunkMapper, times(1)).delete(any());
    }

    @Test
    @DisplayName("countByDocumentId → selectCount 透传")
    void countByDocumentIdReturnsSelectCount() {
        when(chunkMapper.selectCount(any())).thenReturn(7L);
        assertThat(gateway.countByDocumentId(1L)).isEqualTo(7L);
    }
}
