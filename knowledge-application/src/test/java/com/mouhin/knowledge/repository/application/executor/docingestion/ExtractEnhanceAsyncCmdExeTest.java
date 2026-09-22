package com.mouhin.knowledge.repository.application.executor.docingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.DocumentExtractionGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 视觉异步增强执行器单测（{@link ExtractEnhanceAsyncCmdExe}，Phase C）。
 *
 * <p>锁定：（1）正常增强走 {@code extractText → put} 回填进程内缓存；（2）文档缺失 / 存储文件不存在直接跳过，不调解析、不写缓存。 用同步 {@code
 * Runnable::run} 执行器避免线程时序，专注编排语义。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@DisplayName("C 视觉异步增强 (ExtractEnhanceAsyncCmdExe)")
class ExtractEnhanceAsyncCmdExeTest {

    private static final String KEY = "doc-key-1";

    @TempDir Path tmp;

    private final DocumentGateway documentGateway = mock(DocumentGateway.class);
    private final DocumentExtractionGateway documentExtractionService =
            mock(DocumentExtractionGateway.class);
    private final ExtractionCacheHolder extractionCache = mock(ExtractionCacheHolder.class);
    private final Executor directExecutor = Runnable::run;

    private final ExtractEnhanceAsyncCmdExe exe =
            new ExtractEnhanceAsyncCmdExe(
                    documentGateway, documentExtractionService, extractionCache, directExecutor);

    private Document stubDocumentWithFile() throws IOException {
        Path file = tmp.resolve("stored.pdf");
        Files.write(file, new byte[] {1, 2, 3});
        Document doc = new Document();
        doc.setDocumentKey(KEY);
        doc.setFileName("stored.pdf");
        doc.setStoragePath(file.toString());
        when(documentGateway.findByDocumentKey(KEY)).thenReturn(Optional.of(doc));
        return doc;
    }

    @Test
    @DisplayName("正常增强：extractText → 回填进程内缓存")
    void enhancesAndCaches() throws IOException {
        stubDocumentWithFile();
        ExtractionResult result =
                new ExtractionResult(List.of("增强文本"), 1, true, "md5", "pdf-hybrid");
        when(documentExtractionService.extractText(any(), anyLong(), anyString()))
                .thenReturn(result);

        ExtractionResult returned = exe.enhance(KEY);

        assertThat(returned).isSameAs(result);
        verify(extractionCache).put(eq(KEY), eq(result));
    }

    @Test
    @DisplayName("文档不存在：跳过，不调解析、不写缓存")
    void skipsWhenDocumentMissing() throws IOException {
        when(documentGateway.findByDocumentKey(KEY)).thenReturn(Optional.empty());

        assertThat(exe.enhance(KEY)).isNull();
        verify(documentExtractionService, never()).extractText(any(), anyLong(), anyString());
        verify(extractionCache, never()).put(anyString(), any());
    }

    @Test
    @DisplayName("存储文件缺失：跳过，不写缓存")
    void skipsWhenFileMissing() throws IOException {
        Document doc = new Document();
        doc.setDocumentKey(KEY);
        doc.setFileName("gone.pdf");
        doc.setStoragePath(tmp.resolve("gone.pdf").toString());
        when(documentGateway.findByDocumentKey(KEY)).thenReturn(Optional.of(doc));

        assertThat(exe.enhance(KEY)).isNull();
        verify(documentExtractionService, never()).extractText(any(), anyLong(), anyString());
        verify(extractionCache, never()).put(anyString(), any());
    }

    @Test
    @DisplayName("execute 经注入执行器异步提交并回填缓存")
    void executeRunsOnExecutor() throws IOException {
        stubDocumentWithFile();
        ExtractionResult result =
                new ExtractionResult(List.of("增强文本"), 1, true, "md5", "pdf-hybrid");
        when(documentExtractionService.extractText(any(), anyLong(), anyString()))
                .thenReturn(result);

        exe.execute(KEY).join();

        verify(extractionCache).put(eq(KEY), eq(result));
    }
}
