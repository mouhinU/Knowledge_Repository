package com.mouhin.knowledge.repository.application.executor.docingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.client.dto.PreviewDocumentQuery;
import com.mouhin.knowledge.repository.client.dto.PreviewResult;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingConfig;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingStrategyEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.domain.service.DocumentIngestionDomainService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 已上传文档解析预览查询执行器单测：锁定文档不存在抛非法、totalPages 优先取文档已记录值（null/0 回退提取结果）、
 * 权限上下文（owner/dept/visibility）从原文档继承到临时分块入参。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("已上传文档解析预览查询执行器 (PreviewFromDocumentQryExe)")
class PreviewFromDocumentQryExeTest {

    private static final String KEY = "doc-key-preview";

    private final DocumentIngestionSupport support = mock(DocumentIngestionSupport.class);
    private final ExtractionCacheHolder extractionCache = mock(ExtractionCacheHolder.class);
    private final DocumentGateway documentGateway = mock(DocumentGateway.class);
    private final DocumentIngestionDomainService ingestionDomainService =
            mock(DocumentIngestionDomainService.class);
    private final PreviewFromDocumentQryExe exe =
            new PreviewFromDocumentQryExe(
                    support, extractionCache, documentGateway, ingestionDomainService);

    private PreviewDocumentQuery query() {
        PreviewDocumentQuery q = new PreviewDocumentQuery();
        q.setDocumentKey(KEY);
        q.setChunkSize(800);
        q.setOverlap(80);
        q.setStrategy("PARAGRAPH");
        return q;
    }

    private Document storedDoc() {
        Document doc = new Document();
        doc.setId(1L);
        doc.setDocumentKey(KEY);
        doc.setFileName("a.pdf");
        doc.setFileType("application/pdf");
        doc.setOwnerId("u-1");
        doc.setDepartmentId("d-1");
        doc.setVisibility(DocumentVisibilityEnum.PRIVATE);
        when(documentGateway.findByDocumentKey(KEY)).thenReturn(Optional.of(doc));
        when(support.resolveStrategy("PARAGRAPH")).thenReturn(ChunkingStrategyEnum.PARAGRAPH);
        when(support.buildConfig(800, 80, ChunkingStrategyEnum.PARAGRAPH))
                .thenReturn(ChunkingConfig.defaultConfig());
        return doc;
    }

    @Test
    @DisplayName("文档不存在 → 'Document not found'，不触发提取缓存")
    void missingDocumentThrows() {
        when(documentGateway.findByDocumentKey(KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exe.execute(query()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Document not found")
                .hasMessageContaining(KEY);
        verify(extractionCache, never()).getOrReextract(any());
    }

    @Test
    @DisplayName("文档已记录 totalPages>0 → 预览以其为准（覆盖提取页数）")
    void totalPagesPrefersDocumentRecord() {
        Document doc = storedDoc();
        doc.setTotalPages(12);
        when(extractionCache.getOrReextract(doc))
                .thenReturn(new ExtractionResult(List.of("p1"), 3, false, "md5", "pdf"));
        when(ingestionDomainService.chunkDocument(any(), any(), any())).thenReturn(List.of());

        PreviewResult result = exe.execute(query());

        assertThat(result.totalPages()).isEqualTo(12);
        assertThat(result.extractedSections()).isEqualTo(1);
        assertThat(result.fileName()).isEqualTo("a.pdf");
        assertThat(result.format()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("totalPages 为 null → 回退提取结果的 totalPages")
    void nullTotalPagesFallsBackToExtraction() {
        Document doc = storedDoc();
        when(extractionCache.getOrReextract(doc))
                .thenReturn(new ExtractionResult(List.of("p1", "p2"), 2, true, "md5", "pdf"));
        when(ingestionDomainService.chunkDocument(any(), any(), any())).thenReturn(List.of());

        PreviewResult result = exe.execute(query());

        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.likelyScanned()).isTrue();
    }

    @Test
    @DisplayName("分块临时文档继承原文档权限上下文（owner / department / visibility）")
    void chunkContextInheritsPermissionsFromDocument() {
        Document doc = storedDoc();
        when(extractionCache.getOrReextract(doc))
                .thenReturn(new ExtractionResult(List.of("text"), 1, false, "md5", "pdf"));
        DocumentChunk chunk = new DocumentChunk();
        chunk.setContent("text");
        when(ingestionDomainService.chunkDocument(any(), eq(List.of("text")), any()))
                .thenReturn(List.of(chunk));

        PreviewResult result = exe.execute(query());

        org.mockito.ArgumentCaptor<Document> cap =
                org.mockito.ArgumentCaptor.forClass(Document.class);
        verify(ingestionDomainService).chunkDocument(cap.capture(), any(), any());
        Document tempDoc = cap.getValue();
        assertThat(tempDoc.getOwnerId()).isEqualTo("u-1");
        assertThat(tempDoc.getDepartmentId()).isEqualTo("d-1");
        assertThat(tempDoc.getVisibility()).isEqualTo(DocumentVisibilityEnum.PRIVATE);
        assertThat(tempDoc.getDocumentKey()).isEqualTo(KEY);
        assertThat(result.totalChunks()).isEqualTo(1);
    }
}
