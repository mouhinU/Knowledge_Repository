package com.mouhin.knowledge.repository.application.executor.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.gateway.VectorStoreGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 归档文档执行器单测：锁定「DB 先、慢 IO 后」的状态迁移—— （1）INDEXED 文档 → 短事务内置为 ARCHIVED 并落库，事务提交后删 Milvus 向量；（2）非
 * INDEXED（含已 ARCHIVED 的重复归档）→ 聚合根抛状态异常并向外传播， 当前实现不具备重复归档幂等（附可疑标注）；（3）文档不存在 → 非法参数，向量不删；
 * （4）向量删除失败被吞，不回滚已提交的归档状态。
 *
 * @author mouhinU
 * @date 2026-09-24 18:15:00
 */
@DisplayName("归档文档执行器 (DocumentArchiveCmdExe)")
class DocumentArchiveCmdExeTest {

    private static final String DOC_KEY = "doc-key-2";
    private static final Long DOC_ID = 88L;

    private final DocumentGateway documentGateway = mock(DocumentGateway.class);
    private final VectorStoreGateway vectorStoreService = mock(VectorStoreGateway.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final DocumentArchiveCmdExe exe =
            new DocumentArchiveCmdExe(documentGateway, vectorStoreService, transactionTemplate);

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

    private Document stubDocumentWithStatus(DocumentStatusEnum status) {
        Document document = new Document();
        document.setId(DOC_ID);
        document.setDocumentKey(DOC_KEY);
        document.setStatus(status);
        when(documentGateway.findByDocumentKey(DOC_KEY)).thenReturn(Optional.of(document));
        return document;
    }

    @Test
    @DisplayName("INDEXED 文档 → 状态迁移 ARCHIVED 落库，随后删向量（归档文档退出检索）")
    void archivesIndexedDocumentThenDeletesVectors() {
        stubDocumentWithStatus(DocumentStatusEnum.INDEXED);

        exe.execute(DOC_KEY);

        ArgumentCaptor<Document> cap = ArgumentCaptor.forClass(Document.class);
        verify(documentGateway).update(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(DocumentStatusEnum.ARCHIVED);
        verify(vectorStoreService).deleteByDocumentKey(DOC_KEY);
    }

    @Test
    @DisplayName("已 ARCHIVED 重复归档 → 聚合根状态机抛异常而非幂等成功（TODO(行为可疑): 重复归档应静默返回，当前二次点击会报错）")
    void reArchiveIsNotIdempotent() {
        stubDocumentWithStatus(DocumentStatusEnum.ARCHIVED);

        assertThatThrownBy(() -> exe.execute(DOC_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only indexed documents can be archived");
        verify(documentGateway, never()).update(any());
        verify(vectorStoreService, never()).deleteByDocumentKey(any());
    }

    @Test
    @DisplayName("未入库完成（PROCESSING）→ 同样拒绝归档，状态与向量均不变")
    void processingDocumentCannotArchive() {
        stubDocumentWithStatus(DocumentStatusEnum.PROCESSING);

        assertThatThrownBy(() -> exe.execute(DOC_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("archived");
        verify(documentGateway, never()).update(any());
        verify(vectorStoreService, never()).deleteByDocumentKey(any());
    }

    @Test
    @DisplayName("文档不存在 → 抛 IllegalArgumentException，不更新不删向量")
    void missingDocumentRejected() {
        when(documentGateway.findByDocumentKey(DOC_KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exe.execute(DOC_KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Document not found");
        verify(documentGateway, never()).update(any());
        verify(vectorStoreService, never()).deleteByDocumentKey(any());
    }

    @Test
    @DisplayName("向量删除失败 → 被吞仅记告警，已提交的归档状态保持")
    void vectorDeleteFailureIsSwallowed() {
        stubDocumentWithStatus(DocumentStatusEnum.INDEXED);
        doThrow(new RuntimeException("milvus timeout"))
                .when(vectorStoreService)
                .deleteByDocumentKey(DOC_KEY);

        assertThatCode(() -> exe.execute(DOC_KEY)).doesNotThrowAnyException();

        ArgumentCaptor<Document> cap = ArgumentCaptor.forClass(Document.class);
        verify(documentGateway).update(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(DocumentStatusEnum.ARCHIVED);
    }
}
