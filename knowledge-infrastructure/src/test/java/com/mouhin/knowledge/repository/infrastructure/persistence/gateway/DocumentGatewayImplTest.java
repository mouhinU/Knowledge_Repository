package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.DocumentDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.DocumentMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link DocumentGatewayImpl} 契约单测：锁定枚举⇄字符串双向映射、upsert 主键回填与分类聚合归并。
 *
 * <p>Mapper 以 {@code mock(DocumentMapper.class)} 手工注入，不启 Spring。
 *
 * <p>注：{@code clearErrorMessage} 依赖 {@code LambdaUpdateWrapper.set(...)} 清空列，需 TableInfo 缓存，交由 E2E
 * 覆盖，本类不建测。
 *
 * @author mouhinU
 * @date 2026-09-24 18:21:00
 */
@DisplayName("文档仓储实现契约单测 (DocumentGatewayImpl)")
class DocumentGatewayImplTest {

    private final DocumentMapper documentMapper = mock(DocumentMapper.class);
    private final DocumentGatewayImpl gateway = new DocumentGatewayImpl(documentMapper);

    private DocumentDO doOf(Long id, String status) {
        DocumentDO doObj = new DocumentDO();
        doObj.setId(id);
        doObj.setDocumentKey("dk-" + id);
        doObj.setFileName("doc.pdf");
        doObj.setStatus(status);
        doObj.setVisibility("PUBLIC");
        doObj.setOwnerId("u1");
        doObj.setCategory("工作");
        return doObj;
    }

    @Test
    @DisplayName("findById 命中 → status/visibility 字符串转枚举；未命中回 empty")
    void findByIdMapsEnumsAndHandlesMissing() {
        when(documentMapper.selectById(1L)).thenReturn(doOf(1L, "INDEXED"));
        Optional<Document> hit = gateway.findById(1L);
        assertThat(hit).isPresent();
        assertThat(hit.get().getStatus()).isEqualTo(DocumentStatusEnum.INDEXED);
        assertThat(hit.get().getVisibility()).isEqualTo(DocumentVisibilityEnum.PUBLIC);

        when(documentMapper.selectById(99L)).thenReturn(null);
        assertThat(gateway.findById(99L)).isEmpty();
    }

    @Test
    @DisplayName("findByFileChecksum → 条件查询命中转 Entity")
    void findByFileChecksumUsesWrapper() {
        when(documentMapper.selectOne(any())).thenReturn(doOf(2L, "UPLOADED"));
        Optional<Document> hit = gateway.findByFileChecksum("md5");
        assertThat(hit).isPresent();
        assertThat(hit.get().getStatus()).isEqualTo(DocumentStatusEnum.UPLOADED);
    }

    @Test
    @DisplayName("save → 枚举回写为字符串 + 时间戳非空 + 主键回填，返回同一实体")
    void saveWritesEnumsAndBackfillsId() {
        Document doc = new Document();
        doc.setStatus(DocumentStatusEnum.PROCESSING);
        doc.setVisibility(DocumentVisibilityEnum.PRIVATE);
        doc.setOwnerId("u1");
        doc.setCategory("学习");
        doc.setFileName("a.pdf");

        when(documentMapper.insert(any(DocumentDO.class)))
                .thenAnswer(
                        inv -> {
                            inv.getArgument(0, DocumentDO.class).setId(42L);
                            return 1;
                        });

        Document returned = gateway.save(doc);

        ArgumentCaptor<DocumentDO> cap = ArgumentCaptor.forClass(DocumentDO.class);
        verify(documentMapper, times(1)).insert(cap.capture());
        DocumentDO saved = cap.getValue();
        assertThat(saved.getStatus()).isEqualTo("PROCESSING");
        assertThat(saved.getVisibility()).isEqualTo("PRIVATE");
        assertThat(saved.getOwnerId()).isEqualTo("u1");
        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(saved.getUpdateTime()).isNotNull();
        assertThat(returned.getId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("countByStatus → selectCount 透传枚举 name 作为条件")
    void countByStatusDelegatesToSelectCount() {
        when(documentMapper.selectCount(any())).thenReturn(5L);

        long count = gateway.countByStatus(DocumentStatusEnum.INDEXED);

        assertThat(count).isEqualTo(5L);
    }

    @Test
    @DisplayName("countByCategory → null 分类归并为'其他'并按类别累加")
    void countByCategoryMergesNullBucket() {
        Map<String, Object> rowWork = new HashMap<>();
        rowWork.put("category", "工作");
        rowWork.put("total", 2L);
        Map<String, Object> rowNull = new HashMap<>();
        rowNull.put("category", null);
        rowNull.put("total", 3L);
        when(documentMapper.selectMaps(any())).thenReturn(List.of(rowWork, rowNull));

        Map<String, Long> result = gateway.countByCategory();

        assertThat(result).containsEntry("工作", 2L).containsEntry("其他", 3L);
    }

    @Test
    @DisplayName("update 走 updateById，不再触发 insert")
    void updateUsesUpdateById() {
        Document doc = new Document();
        doc.setId(1L);
        doc.setStatus(DocumentStatusEnum.FAILED);
        gateway.update(doc);
        verify(documentMapper, times(1)).updateById(any(DocumentDO.class));
        verify(documentMapper, never()).insert(any(DocumentDO.class));
    }
}
