package com.mouhin.knowledge.repository.application.executor.docingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.client.dto.DocumentVO;
import com.mouhin.knowledge.repository.client.dto.IndexDocumentCmd;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingConfig;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingStrategyEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 确认入库命令执行器单测：锁定文档不存在抛非法、INDEXED 门禁拒绝重复入库、成功链路（参数规范化 → 委托 processDocument → 缓存失效）。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("确认入库命令执行器 (IndexCmdExe)")
class IndexCmdExeTest {

    private static final String KEY = "doc-key-1";

    private final DocumentIngestionSupport support = mock(DocumentIngestionSupport.class);
    private final ExtractionCacheHolder extractionCache = mock(ExtractionCacheHolder.class);
    private final DocumentGateway documentGateway = mock(DocumentGateway.class);
    private final IndexCmdExe exe = new IndexCmdExe(support, extractionCache, documentGateway);

    private IndexDocumentCmd cmd() {
        IndexDocumentCmd c = new IndexDocumentCmd();
        c.setDocumentKey(KEY);
        c.setChunkSize(500);
        c.setOverlap(50);
        c.setStrategy("FIXED_SIZE");
        return c;
    }

    private Document uploadedDoc() {
        Document doc = new Document();
        doc.setId(1L);
        doc.setDocumentKey(KEY);
        doc.setFileName("a.pdf");
        doc.setStatus(DocumentStatusEnum.UPLOADED);
        doc.setVisibility(DocumentVisibilityEnum.INTERNAL);
        doc.setOwnerId("u");
        doc.setDepartmentId("d");
        return doc;
    }

    @Test
    @DisplayName("documentKey 不存在 → 'Document not found'，不触发处理")
    void missingDocumentThrows() {
        when(documentGateway.findByDocumentKey(KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exe.execute(cmd()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Document not found")
                .hasMessageContaining(KEY);
        verify(support, never()).processDocument(any(), any());
    }

    @Test
    @DisplayName("状态已是 INDEXED → 'already indexed' 门禁拒绝，提示走 reindex")
    void alreadyIndexedRejected() {
        Document doc = uploadedDoc();
        doc.setStatus(DocumentStatusEnum.INDEXED);
        when(documentGateway.findByDocumentKey(KEY)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> exe.execute(cmd()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already indexed")
                .hasMessageContaining("reindex");
        verify(support, never()).processDocument(any(), any());
    }

    @Test
    @DisplayName("成功链路：resolveStrategy + buildConfig 规范化参数并写回文档，委托 processDocument 后清缓存")
    void successNormalizesAndProcesses() {
        Document doc = uploadedDoc();
        when(documentGateway.findByDocumentKey(KEY)).thenReturn(Optional.of(doc));
        when(support.resolveStrategy("FIXED_SIZE")).thenReturn(ChunkingStrategyEnum.FIXED_SIZE);
        ChunkingConfig config = ChunkingConfig.defaultConfig();
        when(support.buildConfig(500, 50, ChunkingStrategyEnum.FIXED_SIZE)).thenReturn(config);
        ExtractionResult extraction = new ExtractionResult(List.of("page"), 1, false, "md5", "pdf");
        when(extractionCache.getOrReextract(doc)).thenReturn(extraction);

        DocumentVO vo = exe.execute(cmd());

        ArgumentCaptor<Document> cap = ArgumentCaptor.forClass(Document.class);
        verify(support).processDocument(cap.capture(), eq(extraction));
        assertThat(cap.getValue().getChunkingConfig()).isSameAs(config);
        verify(extractionCache).remove(KEY);
        assertThat(vo.getDocumentKey()).isEqualTo(KEY);
    }

    @Test
    @DisplayName("无效策略字符串 → 由 support.resolveStrategy 归一（执行器透传原始串，不自行校验）")
    void invalidStrategyDelegatedToSupport() {
        Document doc = uploadedDoc();
        when(documentGateway.findByDocumentKey(KEY)).thenReturn(Optional.of(doc));
        IndexDocumentCmd c = cmd();
        c.setStrategy("BOGUS");
        when(support.resolveStrategy("BOGUS")).thenReturn(ChunkingStrategyEnum.FIXED_SIZE);
        when(support.buildConfig(anyInt(), anyInt(), eq(ChunkingStrategyEnum.FIXED_SIZE)))
                .thenReturn(ChunkingConfig.defaultConfig());
        ExtractionResult extraction = new ExtractionResult(List.of("page"), 1, false, "md5", "pdf");
        when(extractionCache.getOrReextract(doc)).thenReturn(extraction);

        exe.execute(c);

        verify(support).resolveStrategy("BOGUS");
        verify(support).processDocument(doc, extraction);
    }
}
