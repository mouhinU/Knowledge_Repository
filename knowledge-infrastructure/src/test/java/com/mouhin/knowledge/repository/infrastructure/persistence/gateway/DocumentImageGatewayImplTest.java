package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.model.entity.DocumentImage;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentImageHit;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.DocumentImageDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.DocumentImageSearchRowDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.DocumentImageMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link DocumentImageGatewayImpl} 契约单测：锁定字段映射、去重计数与联表检索命中装配。
 *
 * <p>Mapper 以 {@code mock(DocumentImageMapper.class)} 手工注入，不启 Spring。
 *
 * @author mouhinU
 * @date 2026-09-24 18:21:00
 */
@DisplayName("文档图片仓储实现契约单测 (DocumentImageGatewayImpl)")
class DocumentImageGatewayImplTest {

    private final DocumentImageMapper imageMapper = mock(DocumentImageMapper.class);
    private final DocumentImageGatewayImpl gateway = new DocumentImageGatewayImpl(imageMapper);

    private DocumentImageDO doOf(Long id, String assetKey) {
        DocumentImageDO doObj = new DocumentImageDO();
        doObj.setId(id);
        doObj.setAssetKey(assetKey);
        doObj.setDocumentId(1L);
        doObj.setPageNo(2);
        doObj.setSeqOnPage(1);
        doObj.setSha256("sha-" + assetKey);
        return doObj;
    }

    @Test
    @DisplayName("save → createTime 为空时补当前时间 + 主键回填")
    void saveFillsTimestampAndBackfillsId() {
        DocumentImage image = new DocumentImage();
        image.setAssetKey("a1");
        image.setDocumentId(1L);
        image.setSha256("sha-a1");

        when(imageMapper.insert(any(DocumentImageDO.class)))
                .thenAnswer(
                        inv -> {
                            inv.getArgument(0, DocumentImageDO.class).setId(9L);
                            return 1;
                        });

        gateway.save(image);

        ArgumentCaptor<DocumentImageDO> cap = ArgumentCaptor.forClass(DocumentImageDO.class);
        verify(imageMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getAssetKey()).isEqualTo("a1");
        assertThat(cap.getValue().getCreateTime()).isNotNull();
        assertThat(image.getCreateTime()).isNotNull();
        assertThat(image.getId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("findByAssetKey → 条件查询命中转 Entity；未命中回 empty")
    void findByAssetKeyMapsAndHandlesMissing() {
        when(imageMapper.selectOne(any())).thenReturn(doOf(5L, "a5"));
        Optional<DocumentImage> hit = gateway.findByAssetKey("a5");
        assertThat(hit).isPresent();
        assertThat(hit.get().getPageNo()).isEqualTo(2);

        when(imageMapper.selectOne(any())).thenReturn(null);
        assertThat(gateway.findByAssetKey("nope")).isEmpty();
    }

    @Test
    @DisplayName("existsByDocumentIdAndSha256 → selectCount>0 为真，=0 为假")
    void existsReflectsSelectCount() {
        when(imageMapper.selectCount(any())).thenReturn(1L);
        assertThat(gateway.existsByDocumentIdAndSha256(1L, "sha-a1")).isTrue();

        when(imageMapper.selectCount(any())).thenReturn(0L);
        assertThat(gateway.existsByDocumentIdAndSha256(1L, "sha-x")).isFalse();
    }

    @Test
    @DisplayName("listByDocumentId → selectList 结果批量转 Entity")
    void listByDocumentIdMapsEveryRow() {
        when(imageMapper.selectList(any())).thenReturn(List.of(doOf(1L, "a1"), doOf(2L, "a2")));

        List<DocumentImage> images = gateway.listByDocumentId(1L);

        assertThat(images).hasSize(2);
        assertThat(images).extracting(DocumentImage::getAssetKey).containsExactly("a1", "a2");
    }

    @Test
    @DisplayName("search → 联表行装配 DocumentImageHit，带出来源文档名")
    void searchAssemblesHitsWithSourceDocumentName() {
        DocumentImageSearchRowDO row = new DocumentImageSearchRowDO();
        row.setId(7L);
        row.setAssetKey("a7");
        row.setDocumentId(1L);
        row.setSourceDocumentName("报告.pdf");
        when(imageMapper.searchRows("kw", null, 10, 0)).thenReturn(List.of(row));

        List<DocumentImageHit> hits = gateway.search("kw", null, 10, 0);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).image().getAssetKey()).isEqualTo("a7");
        assertThat(hits.get(0).sourceDocumentName()).isEqualTo("报告.pdf");
    }
}
