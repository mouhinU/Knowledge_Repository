package com.mouhin.knowledge.repository.application.executor.docingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.client.dto.CustomChunkInput;
import com.mouhin.knowledge.repository.domain.event.DocumentProcessedEvent;
import com.mouhin.knowledge.repository.domain.gateway.DocumentChunkGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.gateway.VectorStoreGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.domain.service.IndexProgressCallback;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 异步自定义分块入库命令执行器单测：执行体在 {@code CompletableFuture.runAsync} 中运行，断言统一用 Mockito {@code timeout(...)}。
 * 锁定成功分派（buildCustomChunks → saveBatch → storeChunks → onComplete）、全空白分块与已 INDEXED 门禁经 onError 上报。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("异步自定义分块入库命令执行器 (IndexCustomChunksAsyncCmdExe)")
class IndexCustomChunksAsyncCmdExeTest {

    private static final String KEY = "doc-key-async-custom";
    private static final long TIMEOUT_MS = 3000L;

    private final DocumentIngestionSupport support = mock(DocumentIngestionSupport.class);
    private final ExtractionCacheHolder extractionCache = mock(ExtractionCacheHolder.class);
    private final DocumentGateway documentGateway = mock(DocumentGateway.class);
    private final DocumentChunkGateway chunkGateway = mock(DocumentChunkGateway.class);
    private final VectorStoreGateway vectorStoreService = mock(VectorStoreGateway.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final IndexCustomChunksAsyncCmdExe exe =
            new IndexCustomChunksAsyncCmdExe(
                    support,
                    extractionCache,
                    documentGateway,
                    chunkGateway,
                    vectorStoreService,
                    eventPublisher);

    private Document uploadedDoc() {
        Document doc = new Document();
        doc.setId(8L);
        doc.setDocumentKey(KEY);
        doc.setFileName("a.pdf");
        doc.setStatus(DocumentStatusEnum.UPLOADED);
        doc.setVisibility(DocumentVisibilityEnum.INTERNAL);
        doc.setOwnerId("u");
        doc.setDepartmentId("d");
        return doc;
    }

    @Test
    @DisplayName("成功分派：buildCustomChunks → saveBatch → storeChunks(带回调) → onComplete + 发布事件")
    void successDispatchesWithCallback() {
        Document doc = uploadedDoc();
        when(documentGateway.findByDocumentKey(KEY)).thenReturn(Optional.of(doc));
        DocumentChunk chunk = new DocumentChunk();
        chunk.setContent("有效内容");
        when(support.buildCustomChunks(eq(doc), anyList())).thenReturn(List.of(chunk));
        when(extractionCache.getOrReextract(doc))
                .thenReturn(new ExtractionResult(List.of("p1", "p2"), 2, false, "md5", "pdf"));
        IndexProgressCallback callback = mock(IndexProgressCallback.class);
        List<CustomChunkInput> inputs = List.of(new CustomChunkInput(0, 1, 1, "有效内容"));

        exe.execute(KEY, inputs, callback);

        verify(support, timeout(TIMEOUT_MS)).buildCustomChunks(eq(doc), eq(inputs));
        verify(chunkGateway, timeout(TIMEOUT_MS)).saveBatch(anyList());
        verify(vectorStoreService, timeout(TIMEOUT_MS)).storeChunks(anyList(), eq(callback));
        verify(callback, timeout(TIMEOUT_MS)).onComplete();
        ArgumentCaptor<Object> eventCap = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, timeout(TIMEOUT_MS)).publishEvent(eventCap.capture());
        DocumentProcessedEvent event = (DocumentProcessedEvent) eventCap.getValue();
        assertThat(event.totalChunks()).isEqualTo(1);
        verify(extractionCache, timeout(TIMEOUT_MS)).remove(KEY);
    }

    @Test
    @DisplayName("customChunks 为空列表 → 异步分支 onError('must not be empty')，不落库")
    void emptyChunksReportsError() {
        IndexProgressCallback callback = mock(IndexProgressCallback.class);

        exe.execute(KEY, List.of(), callback);

        verify(callback, timeout(TIMEOUT_MS))
                .onError(org.mockito.ArgumentMatchers.contains("must not be empty"));
        verify(documentGateway, never()).findByDocumentKey(anyString());
        verify(chunkGateway, never()).saveBatch(anyList());
    }

    @Test
    @DisplayName("全空白分块（buildCustomChunks 返回空）→ onError('No valid chunks provided')，不向量化")
    void allBlankChunksReportsError() {
        Document doc = uploadedDoc();
        when(documentGateway.findByDocumentKey(KEY)).thenReturn(Optional.of(doc));
        when(support.buildCustomChunks(eq(doc), anyList())).thenReturn(List.of());
        IndexProgressCallback callback = mock(IndexProgressCallback.class);

        exe.execute(KEY, List.of(new CustomChunkInput(0, 1, 1, "  ")), callback);

        verify(callback, timeout(TIMEOUT_MS))
                .onError(org.mockito.ArgumentMatchers.contains("No valid chunks provided"));
        verify(vectorStoreService, never()).storeChunks(anyList(), any());
    }

    @Test
    @DisplayName("文档已 INDEXED → 门禁经 onError 上报，不清理不重入库")
    void alreadyIndexedReportsError() {
        Document doc = uploadedDoc();
        doc.setStatus(DocumentStatusEnum.INDEXED);
        when(documentGateway.findByDocumentKey(KEY)).thenReturn(Optional.of(doc));
        IndexProgressCallback callback = mock(IndexProgressCallback.class);

        exe.execute(KEY, List.of(new CustomChunkInput(0, 1, 1, "x")), callback);

        verify(callback, timeout(TIMEOUT_MS))
                .onError(org.mockito.ArgumentMatchers.contains("already indexed"));
        verify(documentGateway, never()).update(any());
    }
}
