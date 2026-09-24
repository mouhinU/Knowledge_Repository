package com.mouhin.knowledge.repository.application.executor.docingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.client.dto.ExamDocumentImageVO;
import com.mouhin.knowledge.repository.client.dto.SearchImagesQuery;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentImage;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentImageHit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 全局配图检索查询执行器单测：锁定分页参数钳制（limit∈[1,200] / offset≥0）、keyword/documentKey 空白归一为 null（走全量最新序）、 VO
 * 展示字段映射（url 句柄拼装 / 来源文档名透传）与 total 独立统计。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("全局配图检索查询执行器 (SearchImagesQryExe)")
class SearchImagesQryExeTest {

    private final DocumentImageSupport documentImageSupport = mock(DocumentImageSupport.class);
    private final SearchImagesQryExe exe = new SearchImagesQryExe(documentImageSupport);

    private SearchImagesQuery query(String keyword, String documentKey, int limit, int offset) {
        SearchImagesQuery q = new SearchImagesQuery();
        q.setKeyword(keyword);
        q.setDocumentKey(documentKey);
        q.setLimit(limit);
        q.setOffset(offset);
        return q;
    }

    private DocumentImageHit hit(String assetKey, String docName) {
        DocumentImage img = new DocumentImage();
        img.setAssetKey(assetKey);
        img.setDocumentId(3L);
        img.setDocumentKey("dk-3");
        img.setPageNo(2);
        img.setSeqOnPage(1);
        img.setMimeType("image/png");
        img.setWidth(640);
        img.setHeight(480);
        img.setByteSize(1024L);
        return new DocumentImageHit(img, docName);
    }

    @Test
    @DisplayName("limit 超上限钳到 200、负 offset 归 0；keyword / documentKey 空白归一为 null")
    void clampsPagingAndTrimsBlankFilters() {
        when(documentImageSupport.searchImages(isNull(), isNull(), eq(200), eq(0)))
                .thenReturn(List.of());
        when(documentImageSupport.countImages(isNull(), isNull())).thenReturn(0L);

        SearchImagesQryExe.SearchResult result = exe.execute(query("   ", "  ", 99999, -5));

        verify(documentImageSupport).searchImages(null, null, 200, 0);
        assertThat(result.total()).isZero();
        assertThat(result.records()).isEmpty();
    }

    @Test
    @DisplayName("limit<1 钳到 1（至少返回一条）")
    void minLimitIsOne() {
        when(documentImageSupport.searchImages(any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(documentImageSupport.countImages(any(), any())).thenReturn(0L);

        exe.execute(query(null, null, 0, 0));

        verify(documentImageSupport).searchImages(isNull(), isNull(), eq(1), eq(0));
    }

    @Test
    @DisplayName("正常检索：keyword 去首尾空格后透传，VO 映射展示字段与 url 句柄")
    void mapsVoAndTrimsKeyword() {
        when(documentImageSupport.searchImages(eq("函数图象"), eq("dk-9"), eq(60), eq(120)))
                .thenReturn(List.of(hit("asset-1", "教材上册.pdf")));
        when(documentImageSupport.countImages("函数图象", "dk-9")).thenReturn(31L);

        SearchImagesQryExe.SearchResult result = exe.execute(query(" 函数图象 ", "dk-9", 60, 120));

        assertThat(result.total()).isEqualTo(31L);
        ExamDocumentImageVO vo = result.records().get(0);
        assertThat(vo.getAssetKey()).isEqualTo("asset-1");
        assertThat(vo.getUrl()).isEqualTo("/api/exam/assets/asset-1");
        assertThat(vo.getSourceDocumentName()).isEqualTo("教材上册.pdf");
        assertThat(vo.getDocumentKey()).isEqualTo("dk-3");
        assertThat(vo.getPageNo()).isEqualTo(2);
        assertThat(vo.getSeqOnPage()).isEqualTo(1);
        assertThat(vo.getMimeType()).isEqualTo("image/png");
        assertThat(vo.getWidth()).isEqualTo(640);
        assertThat(vo.getHeight()).isEqualTo(480);
        assertThat(vo.getByteSize()).isEqualTo(1024L);
    }

    @Test
    @DisplayName("无命中 → records 空列表、total 0，不抛异常")
    void noHitsReturnsEmptyPage() {
        when(documentImageSupport.searchImages(any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(documentImageSupport.countImages(any(), any())).thenReturn(0L);

        SearchImagesQryExe.SearchResult result = exe.execute(query("不存在", null, 60, 0));

        assertThat(result.records()).isEmpty();
        assertThat(result.total()).isZero();
    }
}
