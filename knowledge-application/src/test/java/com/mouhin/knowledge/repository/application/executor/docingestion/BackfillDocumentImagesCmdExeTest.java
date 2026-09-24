package com.mouhin.knowledge.repository.application.executor.docingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 文档图片回填命令执行器单测：锁定 documentId 空校验、文档不存在异常、成功回填返回新增数并透传文档实体。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("文档图片回填命令执行器 (BackfillDocumentImagesCmdExe)")
class BackfillDocumentImagesCmdExeTest {

    private final DocumentGateway documentGateway = mock(DocumentGateway.class);
    private final DocumentImageSupport documentImageSupport = mock(DocumentImageSupport.class);
    private final BackfillDocumentImagesCmdExe exe =
            new BackfillDocumentImagesCmdExe(documentGateway, documentImageSupport);

    @Test
    @DisplayName("documentId 为 null → '文档 ID 不能为空'，不查库")
    void nullIdRejected() {
        assertThatThrownBy(() -> exe.execute(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文档 ID 不能为空");
        verify(documentGateway, never()).findById(any());
    }

    @Test
    @DisplayName("文档不存在 → '文档不存在: id='，不触发回填")
    void missingDocumentThrows() {
        when(documentGateway.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exe.execute(404L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文档不存在")
                .hasMessageContaining("404");
        verify(documentImageSupport, never()).backfill(any());
    }

    @Test
    @DisplayName("成功回填 → 透传文档实体并返回新增图片数")
    void successReturnsAddedCount() {
        Document doc = new Document();
        doc.setId(9L);
        doc.setDocumentKey("key-9");
        doc.setStoragePath("/data/docs/key-9.pdf");
        when(documentGateway.findById(9L)).thenReturn(Optional.of(doc));
        when(documentImageSupport.backfill(doc)).thenReturn(3);

        assertThat(exe.execute(9L)).isEqualTo(3);
        verify(documentImageSupport).backfill(doc);
    }

    @Test
    @DisplayName("源文件缺失由 support 内部吞并返回 0 → 执行器原样透传 0，不视为异常")
    void zeroWhenSourceMissingIsPropagated() {
        Document doc = new Document();
        doc.setId(10L);
        doc.setDocumentKey("key-10");
        when(documentGateway.findById(10L)).thenReturn(Optional.of(doc));
        when(documentImageSupport.backfill(doc)).thenReturn(0);

        assertThat(exe.execute(10L)).isZero();
    }
}
