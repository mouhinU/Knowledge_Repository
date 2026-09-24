package com.mouhin.knowledge.repository.application.executor.docingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.client.dto.CustomChunkInput;
import com.mouhin.knowledge.repository.client.dto.DocumentVO;
import com.mouhin.knowledge.repository.domain.event.DocumentProcessedEvent;
import com.mouhin.knowledge.repository.domain.gateway.DocumentChunkGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.gateway.VectorStoreGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 自定义分块入库命令执行器单测：锁定入参非空校验、文档不存在 / 已 INDEXED 门禁、全空白分块标记 FAILED 短路、 成功链路（saveBatch → storeChunks →
 * markIndexed → 发布事件 → 清缓存）以及处理异常吞并标记 FAILED 的兜底语义。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("自定义分块入库命令执行器 (IndexCustomChunksCmdExe)")
class IndexCustomChunksCmdExeTest {

    private static final String KEY = "doc-key-custom";

    private final DocumentIngestionSupport support = mock(DocumentIngestionSupport.class);
    private final ExtractionCacheHolder extractionCache = mock(ExtractionCacheHolder.class);
    private final DocumentGateway documentGateway = mock(DocumentGateway.class);
    private final DocumentChunkGateway chunkGateway = mock(DocumentChunkGateway.class);
    private final VectorStoreGateway vectorStoreService = mock(VectorStoreGateway.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final IndexCustomChunksCmdExe exe =
            new IndexCustomChunksCmdExe(
                    support,
                    extractionCache,
                    documentGateway,
                    chunkGateway,
                    vectorStoreService,
                    eventPublisher);

    private Document uploadedDoc() {
        Document doc = new Document();
        doc.setId(5L);
        doc.setDocumentKey(KEY);
        doc.setFileName("a.pdf");
        doc.setStatus(DocumentStatusEnum.UPLOADED);
        doc.setVisibility(DocumentVisibilityEnum.INTERNAL);
        doc.setOwnerId("u");
        doc.setDepartmentId("d");
        return doc;
    }

    @Test
    @DisplayName("customChunks 为 null / 空列表 → 'Custom chunks must not be empty'")
    void emptyCustomChunksRejected() {
        assertThatThrownBy(() -> exe.execute(KEY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Custom chunks must not be empty");
        assertThatThrownBy(() -> exe.execute(KEY, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Custom chunks must not be empty");
        verify(documentGateway, never()).findByDocumentKey(anyString());
    }

    @Test
    @DisplayName("文档不存在 / 已 INDEXED → 门禁抛异常，不落任何分块")
    void guardsOnDocumentState() {
        when(documentGateway.findByDocumentKey(KEY)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> exe.execute(KEY, List.of(new CustomChunkInput(0, 1, 1, "x"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Document not found");

        Document indexed = uploadedDoc();
        indexed.setStatus(DocumentStatusEnum.INDEXED);
        when(documentGateway.findByDocumentKey(KEY)).thenReturn(Optional.of(indexed));
        assertThatThrownBy(() -> exe.execute(KEY, List.of(new CustomChunkInput(0, 1, 1, "x"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already indexed");
        verify(chunkGateway, never()).saveBatch(anyList());
    }

    @Test
    @DisplayName("全部空白分块 → 标记 FAILED('No valid chunks provided') 并返回，不落分块")
    void allBlankChunksMarksFailed() {
        Document doc = uploadedDoc();
        when(documentGateway.findByDocumentKey(KEY)).thenReturn(Optional.of(doc));

        DocumentVO vo =
                exe.execute(
                        KEY,
                        List.of(
                                new CustomChunkInput(0, 1, 1, "   "),
                                new CustomChunkInput(1, 1, 1, null)));

        assertThat(vo.getStatus()).isEqualTo("FAILED");
        verify(documentGateway, org.mockito.Mockito.atLeast(2)).update(doc);
        verify(chunkGateway, never()).saveBatch(anyList());
        verify(vectorStoreService, never()).storeChunks(anyList());
    }

    @Test
    @DisplayName("成功链路：空白项被跳过、有效项落库并向量化，markIndexed 后发布事件并清缓存")
    void successStoresVectorsAndPublishes() {
        Document doc = uploadedDoc();
        when(documentGateway.findByDocumentKey(KEY)).thenReturn(Optional.of(doc));
        when(extractionCache.getOrReextract(doc))
                .thenReturn(new ExtractionResult(List.of("p1", "p2"), 2, false, "md5-c", "pdf"));

        List<CustomChunkInput> inputs =
                List.of(
                        new CustomChunkInput(0, 1, 1, "有效内容一"),
                        new CustomChunkInput(1, 1, 1, "  "),
                        new CustomChunkInput(2, 2, 2, "有效内容二"));
        DocumentVO vo = exe.execute(KEY, inputs);

        ArgumentCaptor<List<DocumentChunk>> cap = ArgumentCaptor.forClass(List.class);
        verify(chunkGateway).saveBatch(cap.capture());
        List<DocumentChunk> saved = cap.getValue();
        assertThat(saved).hasSize(2);
        // TODO(行为可疑): 本地 buildChunks 按有效项顺序重新编号——空白项跳过后 chunkIndex 连续（0,1），
        //   与前端提交的原始 chunkIndex（0,2）存在漂移，语义合理但建议产品侧确认。
        assertThat(saved.get(0).getContent()).isEqualTo("有效内容一");
        assertThat(saved.get(1).getChunkIndex()).isEqualTo(1);
        verify(vectorStoreService).storeChunks(anyList());
        assertThat(vo.getStatus()).isEqualTo("INDEXED");
        ArgumentCaptor<Object> eventCap = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCap.capture());
        DocumentProcessedEvent event = (DocumentProcessedEvent) eventCap.getValue();
        assertThat(event.totalPages()).isEqualTo(2);
        assertThat(event.totalChunks()).isEqualTo(2);
        verify(extractionCache).remove(KEY);
    }

    @Test
    @DisplayName("向量化抛异常 → 兜底吞并：标记 FAILED 并正常返回 VO，不发事件不清缓存")
    void vectorFailureSwallowedMarksFailed() {
        Document doc = uploadedDoc();
        when(documentGateway.findByDocumentKey(KEY)).thenReturn(Optional.of(doc));
        org.mockito.Mockito.doThrow(new RuntimeException("milvus down"))
                .when(vectorStoreService)
                .storeChunks(anyList());

        DocumentVO vo = exe.execute(KEY, List.of(new CustomChunkInput(0, 1, 1, "内容")));

        assertThat(vo.getStatus()).isEqualTo("FAILED");
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(extractionCache, never()).remove(anyString());
    }
}
