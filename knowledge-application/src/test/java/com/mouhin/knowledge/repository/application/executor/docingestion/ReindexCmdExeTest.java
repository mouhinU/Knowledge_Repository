package com.mouhin.knowledge.repository.application.executor.docingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.client.dto.DocumentVO;
import com.mouhin.knowledge.repository.domain.gateway.DocumentChunkGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentExtractionGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.gateway.VectorStoreGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 重新入库命令执行器单测：锁定文档不存在 / 存储文件缺失两条前置门禁，以及成功链路的执行顺序—— Milvus 清向量（事务外）→ 短事务内清分块 + 状态回退 UPLOADED + 专用清列
 * → 事务外重抽取并委托 processDocument。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("重新入库命令执行器 (ReindexCmdExe)")
class ReindexCmdExeTest {

    private static final String KEY = "doc-key-reindex";

    @TempDir Path tmp;

    private final DocumentIngestionSupport support = mock(DocumentIngestionSupport.class);
    private final DocumentGateway documentGateway = mock(DocumentGateway.class);
    private final DocumentChunkGateway chunkGateway = mock(DocumentChunkGateway.class);
    private final VectorStoreGateway vectorStoreService = mock(VectorStoreGateway.class);
    private final DocumentExtractionGateway documentExtractionService =
            mock(DocumentExtractionGateway.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final ReindexCmdExe exe =
            new ReindexCmdExe(
                    support,
                    documentGateway,
                    chunkGateway,
                    vectorStoreService,
                    documentExtractionService,
                    transactionTemplate);

    private Document indexedDoc(Path storage) {
        Document doc = new Document();
        doc.setId(11L);
        doc.setDocumentKey(KEY);
        doc.setFileName("a.pdf");
        doc.setStatus(DocumentStatusEnum.INDEXED);
        doc.setStoragePath(storage.toString());
        doc.setOwnerId("u");
        doc.setDepartmentId("d");
        when(documentGateway.findByDocumentKey(KEY)).thenReturn(Optional.of(doc));
        // 让短事务真正执行清理闭包
        org.mockito.Mockito.doAnswer(
                        inv -> {
                            java.util.function.Consumer<?> consumer = inv.getArgument(0);
                            consumer.accept(null);
                            return null;
                        })
                .when(transactionTemplate)
                .executeWithoutResult(any());
        return doc;
    }

    @Test
    @DisplayName("documentKey 不存在 → 'Document not found'，不清任何数据")
    void missingDocumentThrows() {
        when(documentGateway.findByDocumentKey(KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exe.execute(KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Document not found")
                .hasMessageContaining(KEY);
        verify(vectorStoreService, never()).deleteByDocumentKey(anyString());
    }

    @Test
    @DisplayName("存储文件缺失 → 'Stored file not found'，向量与分块保持原样")
    void missingStoredFileThrows() {
        indexedDoc(tmp.resolve("ghost.pdf"));

        assertThatThrownBy(() -> exe.execute(KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Stored file not found");
        verify(vectorStoreService, never()).deleteByDocumentKey(anyString());
        verify(chunkGateway, never()).deleteByDocumentId(anyLong());
    }

    @Test
    @DisplayName("成功链路：清旧向量 → 短事务清分块/回退 UPLOADED/清 error_message → 重抽取 → processDocument")
    void successClearsThenReprocesses() throws IOException {
        Path stored = tmp.resolve("stored.pdf");
        Files.write(stored, new byte[] {1, 2, 3});
        Document doc = indexedDoc(stored);
        ExtractionResult result =
                new ExtractionResult(List.of("page text"), 1, false, "md5", "pdf");
        when(documentExtractionService.extractText(any(), anyLong(), anyString()))
                .thenReturn(result);

        DocumentVO vo = exe.execute(KEY);

        verify(vectorStoreService).deleteByDocumentKey(KEY);
        verify(transactionTemplate).executeWithoutResult(any());
        verify(chunkGateway).deleteByDocumentId(11L);
        verify(documentGateway).update(doc);
        verify(documentGateway).clearErrorMessage(11L);
        assertThat(doc.getStatus()).isEqualTo(DocumentStatusEnum.UPLOADED);
        assertThat(doc.getErrorMessage()).isNull();
        verify(support).processDocument(doc, result);
        assertThat(vo.getDocumentKey()).isEqualTo(KEY);
    }

    @Test
    @DisplayName("重抽取 IO 异常 → 包装 'Failed to re-extract text'，不再 processDocument")
    void reExtractFailureWrapped() throws IOException {
        Path stored = tmp.resolve("stored2.pdf");
        Files.write(stored, new byte[] {1});
        indexedDoc(stored);
        when(documentExtractionService.extractText(any(), anyLong(), anyString()))
                .thenThrow(new IOException("broken"));

        assertThatThrownBy(() -> exe.execute(KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to re-extract text");
        verify(support, never()).processDocument(any(), any());
    }

    @Test
    @DisplayName("清旧向量先于短事务执行（DB 连接不被 Milvus 抖动拖住）")
    void vectorDeleteHappensBeforeShortTransaction() throws IOException {
        Path stored = tmp.resolve("stored3.pdf");
        Files.write(stored, new byte[] {1});
        indexedDoc(stored);
        when(documentExtractionService.extractText(any(), anyLong(), anyString()))
                .thenReturn(new ExtractionResult(List.of("page text"), 1, false, "md5", "pdf"));

        exe.execute(KEY);

        org.mockito.InOrder inOrder =
                org.mockito.Mockito.inOrder(vectorStoreService, transactionTemplate);
        inOrder.verify(vectorStoreService).deleteByDocumentKey(eq(KEY));
        inOrder.verify(transactionTemplate).executeWithoutResult(any());
    }
}
