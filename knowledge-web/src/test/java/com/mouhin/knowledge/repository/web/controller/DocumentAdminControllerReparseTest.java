package com.mouhin.knowledge.repository.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.application.executor.docingestion.ExtractEnhanceAsyncCmdExe;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * {@link DocumentAdminController#reparse} 契约测试（Phase D）。
 *
 * <p>直接驱动控制器方法，锁定：策略 CSV 经白名单解析后传入异步增强执行器、立即返回 {@code 202 ENQUEUED}， 且 {@code auto}
 * 归一为空栈（走配置默认）。不启动 Web 容器，专注编排语义。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@DisplayName("D 重解析端点 (reparse)")
class DocumentAdminControllerReparseTest {

    private final ExtractEnhanceAsyncCmdExe extractEnhanceAsyncCmdExe =
            mock(ExtractEnhanceAsyncCmdExe.class);

    private final DocumentAdminController controller =
            new DocumentAdminController(
                    mock(com.mouhin.knowledge.repository.client.api.DocumentServiceI.class),
                    mock(
                            com.mouhin.knowledge.repository.client.api.DocumentIngestionServiceI
                                    .class),
                    mock(
                            com.mouhin.knowledge.repository.application.executor.docingestion
                                    .IndexAsyncCmdExe.class),
                    mock(
                            com.mouhin.knowledge.repository.application.executor.docingestion
                                    .IndexCustomChunksAsyncCmdExe.class),
                    mock(
                            com.mouhin.knowledge.repository.application.executor.docingestion
                                    .ReindexAsyncCmdExe.class),
                    extractEnhanceAsyncCmdExe,
                    mock(IndexProgressStore.class));

    @Test
    @DisplayName("强制策略 CSV → 解析后传入执行器，返回 202 ENQUEUED")
    void reparseEnqueuesForcedStack() {
        when(extractEnhanceAsyncCmdExe.execute("doc-1", List.of("PDF_HYBRID", "PDF_BOX")))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        ResponseEntity<?> response = controller.reparse("doc-1", "PDF_HYBRID,pdf_box");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isInstanceOf(java.util.Map.class);
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> body = (java.util.Map<String, String>) response.getBody();
        assertThat(body).containsEntry("documentKey", "doc-1").containsEntry("status", "ENQUEUED");
        verify(extractEnhanceAsyncCmdExe).execute("doc-1", List.of("PDF_HYBRID", "PDF_BOX"));
    }

    @Test
    @DisplayName("auto → 空栈（配置默认路由），仍返回 202")
    void reparseAutoUsesEmptyStack() {
        when(extractEnhanceAsyncCmdExe.execute(eq("doc-2"), eq(List.of())))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        ResponseEntity<?> response = controller.reparse("doc-2", "auto");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(extractEnhanceAsyncCmdExe).execute("doc-2", List.of());
    }
}
