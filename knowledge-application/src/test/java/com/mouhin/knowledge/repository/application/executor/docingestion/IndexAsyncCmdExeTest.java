package com.mouhin.knowledge.repository.application.executor.docingestion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingConfig;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingStrategyEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.domain.service.IndexProgressCallback;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 异步入库命令执行器单测：执行体在 {@code CompletableFuture.runAsync} 中运行，断言统一用 Mockito {@code timeout(...)}。
 * 锁定成功分派（processDocument 携带回调 + 缓存失效）与两条异常分支经 callback.onError 上报、不打断调用线程。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("异步入库命令执行器 (IndexAsyncCmdExe)")
class IndexAsyncCmdExeTest {

    private static final String KEY = "doc-key-async";
    private static final long TIMEOUT_MS = 3000L;

    private final DocumentIngestionSupport support = mock(DocumentIngestionSupport.class);
    private final ExtractionCacheHolder extractionCache = mock(ExtractionCacheHolder.class);
    private final DocumentGateway documentGateway = mock(DocumentGateway.class);
    private final IndexAsyncCmdExe exe =
            new IndexAsyncCmdExe(support, extractionCache, documentGateway);

    private Document doc(DocumentStatusEnum status) {
        Document doc = new Document();
        doc.setId(1L);
        doc.setDocumentKey(KEY);
        doc.setFileName("a.pdf");
        doc.setStatus(status);
        return doc;
    }

    @Test
    @DisplayName("成功分派：buildConfig → processDocument(带回调) → 缓存失效")
    void successDelegatesWithCallback() {
        Document document = doc(DocumentStatusEnum.UPLOADED);
        when(documentGateway.findByDocumentKey(KEY)).thenReturn(Optional.of(document));
        when(support.resolveStrategy(anyString())).thenReturn(ChunkingStrategyEnum.FIXED_SIZE);
        ChunkingConfig config = ChunkingConfig.defaultConfig();
        when(support.buildConfig(anyInt(), anyInt(), any())).thenReturn(config);
        ExtractionResult extraction = new ExtractionResult(List.of("page"), 1, false, "md5", "pdf");
        when(extractionCache.getOrReextract(document)).thenReturn(extraction);
        IndexProgressCallback callback = mock(IndexProgressCallback.class);

        exe.execute(KEY, 500, 50, "FIXED_SIZE", callback);

        verify(support, timeout(TIMEOUT_MS))
                .processDocument(eq(document), eq(extraction), eq(callback));
        verify(extractionCache, timeout(TIMEOUT_MS)).remove(KEY);
    }

    @Test
    @DisplayName("文档不存在 → 异步分支经 callback.onError 上报，不触发 processDocument")
    void missingDocumentReportsError() {
        when(documentGateway.findByDocumentKey(KEY)).thenReturn(Optional.empty());
        IndexProgressCallback callback = mock(IndexProgressCallback.class);

        exe.execute(KEY, 500, 50, "FIXED_SIZE", callback);

        verify(callback, timeout(TIMEOUT_MS)).onError(anyString());
        verify(support, never()).processDocument(any(), any(), any());
    }

    @Test
    @DisplayName("已 INDEXED → 'already indexed' 走 onError，不清缓存")
    void alreadyIndexedReportsError() {
        when(documentGateway.findByDocumentKey(KEY))
                .thenReturn(Optional.of(doc(DocumentStatusEnum.INDEXED)));
        IndexProgressCallback callback = mock(IndexProgressCallback.class);

        exe.execute(KEY, 500, 50, "FIXED_SIZE", callback);

        verify(callback, timeout(TIMEOUT_MS))
                .onError(org.mockito.ArgumentMatchers.contains("already indexed"));
        verify(extractionCache, never()).getOrReextract(any());
        verify(extractionCache, never()).remove(anyString());
    }

    @Test
    @DisplayName("callback 为 null 时异常分支不抛 NPE（null 防护）")
    void nullCallbackSafeOnFailure() {
        when(documentGateway.findByDocumentKey(KEY)).thenReturn(Optional.empty());

        exe.execute(KEY, 500, 50, "FIXED_SIZE", null);

        verify(extractionCache, org.mockito.Mockito.after(TIMEOUT_MS).never()).remove(anyString());
    }
}
