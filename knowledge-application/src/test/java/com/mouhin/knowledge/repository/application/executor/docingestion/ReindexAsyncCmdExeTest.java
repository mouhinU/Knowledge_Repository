package com.mouhin.knowledge.repository.application.executor.docingestion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.client.dto.CustomChunkInput;
import com.mouhin.knowledge.repository.domain.gateway.DocumentChunkGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentExtractionGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.gateway.VectorStoreGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.domain.service.IndexProgressCallback;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 异步重新入库执行器单测（{@link ReindexAsyncCmdExe}）。
 *
 * <p>锁定：（1）自动分块重入库先清旧向量 / 分块 / 配图、重抽文本与配图，再委托 {@link DocumentIngestionSupport#processDocument}
 * 走首次入库同一条 SSE 回调通道； （2）自定义分块分支改走 {@code buildCustomChunks → saveBatch → storeChunks →
 * onComplete}，不再调用 {@code processDocument}；（3）不可重入状态（如 UPLOADED / PROCESSING）直接回调 onError 且不做任何清理。
 *
 * <p>因执行体在 {@code CompletableFuture.runAsync} 中运行，断言统一使用 Mockito {@code timeout(...)}。
 *
 * @author Knowledge-Repository
 * @date 2026-09-20
 */
@DisplayName("异步重新入库 (ReindexAsyncCmdExe)")
class ReindexAsyncCmdExeTest {

    private static final String KEY = "doc-key-1";
    private static final long TIMEOUT_MS = 3000L;

    @TempDir Path tmp;

    private final DocumentIngestionSupport support = mock(DocumentIngestionSupport.class);
    private final ExtractionCacheHolder extractionCache = mock(ExtractionCacheHolder.class);
    private final DocumentGateway documentGateway = mock(DocumentGateway.class);
    private final DocumentChunkGateway chunkGateway = mock(DocumentChunkGateway.class);
    private final VectorStoreGateway vectorStoreService = mock(VectorStoreGateway.class);
    private final DocumentExtractionGateway documentExtractionService =
            mock(DocumentExtractionGateway.class);
    private final DocumentImageSupport documentImageSupport = mock(DocumentImageSupport.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

    private final ReindexAsyncCmdExe exe =
            new ReindexAsyncCmdExe(
                    support,
                    extractionCache,
                    documentGateway,
                    chunkGateway,
                    vectorStoreService,
                    documentExtractionService,
                    documentImageSupport,
                    eventPublisher,
                    transactionTemplate);

    private Document stubDocument(DocumentStatusEnum status) throws IOException {
        Path file = tmp.resolve("stored.pdf");
        Files.write(file, new byte[] {1, 2, 3});
        Document doc = new Document();
        doc.setId(99L);
        doc.setDocumentKey(KEY);
        doc.setFileName("stored.pdf");
        doc.setStoragePath(file.toString());
        doc.setStatus(status);
        when(documentGateway.findByDocumentKey(KEY)).thenReturn(Optional.of(doc));
        // 让短事务真正执行清理闭包
        doAnswer(
                        inv -> {
                            Consumer<?> consumer = inv.getArgument(0);
                            consumer.accept(null);
                            return null;
                        })
                .when(transactionTemplate)
                .executeWithoutResult(any());
        return doc;
    }

    @Test
    @DisplayName("自动分块重入库：清旧向量/分块/配图 → 重抽 → processDocument(带回调)")
    void autoReindexClearsThenDelegates() throws IOException {
        Document doc = stubDocument(DocumentStatusEnum.INDEXED);
        ExtractionResult result =
                new ExtractionResult(List.of("page one text"), 1, false, "md5", "pdf");
        when(documentExtractionService.extractText(any(), any(Long.class), anyString()))
                .thenReturn(result);
        IndexProgressCallback callback = mock(IndexProgressCallback.class);

        exe.execute(KEY, 500, 50, "FIXED_SIZE", null, callback);

        verify(vectorStoreService, timeout(TIMEOUT_MS)).deleteByDocumentKey(KEY);
        verify(chunkGateway, timeout(TIMEOUT_MS)).deleteByDocumentId(99L);
        verify(documentImageSupport, timeout(TIMEOUT_MS)).deleteImages(doc);
        verify(extractionCache, timeout(TIMEOUT_MS)).remove(KEY);
        verify(documentImageSupport, timeout(TIMEOUT_MS)).extractAndPersist(eq(doc), any());
        verify(support, timeout(TIMEOUT_MS)).processDocument(eq(doc), eq(result), eq(callback));
        verify(support, never()).buildCustomChunks(any(), any());
    }

    @Test
    @DisplayName("自定义分块重入库：走 buildCustomChunks→storeChunks，不再 processDocument")
    void customReindexUsesCustomChunks() throws IOException {
        Document doc = stubDocument(DocumentStatusEnum.FAILED);
        ExtractionResult result =
                new ExtractionResult(List.of("page one text"), 1, false, "md5", "pdf");
        when(documentExtractionService.extractText(any(), any(Long.class), anyString()))
                .thenReturn(result);
        DocumentChunk chunk = new DocumentChunk();
        chunk.setContent("adjusted");
        when(support.buildCustomChunks(eq(doc), any())).thenReturn(List.of(chunk));
        IndexProgressCallback callback = mock(IndexProgressCallback.class);

        List<CustomChunkInput> custom = List.of(new CustomChunkInput(0, 1, 1, "adjusted"));
        exe.execute(KEY, 0, 0, null, custom, callback);

        verify(documentImageSupport, timeout(TIMEOUT_MS)).deleteImages(doc);
        verify(support, timeout(TIMEOUT_MS)).buildCustomChunks(eq(doc), eq(custom));
        verify(chunkGateway, timeout(TIMEOUT_MS)).saveBatch(any());
        verify(vectorStoreService, timeout(TIMEOUT_MS)).storeChunks(any(), eq(callback));
        verify(callback, timeout(TIMEOUT_MS)).onComplete();
        verify(support, never()).processDocument(any(), any(), any());
    }

    @Test
    @DisplayName("不可重入状态（UPLOADED）：仅 onError，不做任何清理")
    void nonReindexableStatusAborts() throws IOException {
        stubDocument(DocumentStatusEnum.UPLOADED);
        IndexProgressCallback callback = mock(IndexProgressCallback.class);

        exe.execute(KEY, 500, 50, "FIXED_SIZE", null, callback);

        verify(callback, timeout(TIMEOUT_MS)).onError(anyString());
        verify(vectorStoreService, never()).deleteByDocumentKey(anyString());
        verify(documentImageSupport, never()).deleteImages(any());
    }
}
