package com.mouhin.knowledge.repository.application.executor.document;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.DocumentChunkGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.gateway.VectorStoreGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 删除文档执行器单测：锁定「DB 先、慢 IO 后」的级联删除—— （1）短事务内级联删除 chunk 行与 document 行，事务提交后再删 Milvus 向量；（2）文档不存在 →
 * 事务回调内抛非法参数并向外传播，向量不删； （3）向量删除失败被吞（DB 已提交不回滚，仅记告警）；（4）找不到即抛错而非幂等静默（当前行为，附可疑标注）。
 *
 * @author mouhinU
 * @date 2026-09-24 18:15:00
 */
@DisplayName("删除文档执行器 (DocumentDeleteCmdExe)")
class DocumentDeleteCmdExeTest {

    private static final String DOC_KEY = "doc-key-1";
    private static final Long DOC_ID = 66L;

    private final DocumentGateway documentGateway = mock(DocumentGateway.class);
    private final DocumentChunkGateway chunkGateway = mock(DocumentChunkGateway.class);
    private final VectorStoreGateway vectorStoreService = mock(VectorStoreGateway.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final DocumentDeleteCmdExe exe =
            new DocumentDeleteCmdExe(
                    documentGateway, chunkGateway, vectorStoreService, transactionTemplate);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void runTransactionCallbackInline() {
        // 手工 mock 无 Spring：让 executeWithoutResult 直接内联执行回调，模拟事务提交
        doAnswer(
                        invocation -> {
                            Consumer<TransactionStatus> action = invocation.getArgument(0);
                            action.accept(mock(TransactionStatus.class));
                            return null;
                        })
                .when(transactionTemplate)
                .executeWithoutResult(any());
    }

    private Document stubExistingDocument() {
        Document document = new Document();
        document.setId(DOC_ID);
        document.setDocumentKey(DOC_KEY);
        when(documentGateway.findByDocumentKey(DOC_KEY)).thenReturn(Optional.of(document));
        return document;
    }

    @Test
    @DisplayName("文档存在 → 事务内级联删 chunk + document 行，随后删 Milvus 向量")
    void deletesRowsThenVectors() {
        stubExistingDocument();

        exe.execute(DOC_KEY);

        verify(chunkGateway).deleteByDocumentId(DOC_ID);
        verify(documentGateway).deleteById(DOC_ID);
        verify(vectorStoreService).deleteByDocumentKey(DOC_KEY);
    }

    @Test
    @DisplayName(
            "文档不存在 → 抛 IllegalArgumentException，DB 与向量均不动（TODO(行为可疑): 与'找不到即幂等成功'的常见删除语义不同，前端重复点删会收到 4xx 而非静默）")
    void missingDocumentThrowsAndSkipsAllDeletes() {
        when(documentGateway.findByDocumentKey(DOC_KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exe.execute(DOC_KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Document not found");
        verify(chunkGateway, never()).deleteByDocumentId(any());
        verify(documentGateway, never()).deleteById(any());
        verify(vectorStoreService, never()).deleteByDocumentKey(any());
    }

    @Test
    @DisplayName("向量删除抛异常 → 被吞仅记日志，已提交的 DB 删除不回滚不重做")
    void vectorDeleteFailureIsSwallowed() {
        stubExistingDocument();
        doThrow(new RuntimeException("milvus down"))
                .when(vectorStoreService)
                .deleteByDocumentKey(DOC_KEY);

        assertThatCode(() -> exe.execute(DOC_KEY)).doesNotThrowAnyException();

        verify(chunkGateway).deleteByDocumentId(DOC_ID);
        verify(documentGateway).deleteById(DOC_ID);
    }

    @Test
    @DisplayName("DB 删除异常发生在事务内 → 向外传播，向量删除不执行")
    void dbFailurePropagatesAndSkipsVectorDelete() {
        stubExistingDocument();
        doThrow(new RuntimeException("fk constraint"))
                .when(chunkGateway)
                .deleteByDocumentId(DOC_ID);

        assertThatThrownBy(() -> exe.execute(DOC_KEY))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("fk constraint");
        verify(documentGateway, never()).deleteById(any());
        verify(vectorStoreService, never()).deleteByDocumentKey(any());
    }
}
