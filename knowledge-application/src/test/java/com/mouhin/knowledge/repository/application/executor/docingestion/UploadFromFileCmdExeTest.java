package com.mouhin.knowledge.repository.application.executor.docingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.client.dto.DocumentVO;
import com.mouhin.knowledge.repository.domain.event.DocumentCreatedEvent;
import com.mouhin.knowledge.repository.domain.gateway.DocumentExtractionGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 分片组装文件入库命令执行器单测：锁定文件缺失异常包装、MD5 去重短路、成功链路（UPLOADED 落库 + visibility null→INTERNAL 归一 + 图片抽取容错 +
 * 发布创建事件）。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("分片组装文件入库命令执行器 (UploadFromFileCmdExe)")
class UploadFromFileCmdExeTest {

    @TempDir Path tmp;

    private final DocumentIngestionSupport support = mock(DocumentIngestionSupport.class);
    private final DocumentGateway documentGateway = mock(DocumentGateway.class);
    private final DocumentExtractionGateway documentExtractionService =
            mock(DocumentExtractionGateway.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final DocumentImageSupport documentImageSupport = mock(DocumentImageSupport.class);
    private final UploadFromFileCmdExe exe =
            new UploadFromFileCmdExe(
                    support,
                    documentGateway,
                    documentExtractionService,
                    eventPublisher,
                    documentImageSupport);

    private Path assembledFile() throws IOException {
        Path file = tmp.resolve("assembled.pdf");
        Files.write(file, new byte[] {1, 2, 3, 4});
        return file;
    }

    @Test
    @DisplayName("组装文件不存在 → 'Failed to get file size' 包装，不落库")
    void missingFileThrows() {
        Path ghost = tmp.resolve("not-exist.pdf");

        assertThatThrownBy(
                        () ->
                                exe.execute(
                                        ghost,
                                        "x.pdf",
                                        "u-1",
                                        "d-1",
                                        DocumentVisibilityEnum.INTERNAL,
                                        null,
                                        null,
                                        null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to get file size");
        verify(documentGateway, never()).save(any());
    }

    @Test
    @DisplayName("checksum 命中已有文档 → 去重短路，不落库不发事件")
    void duplicateChecksumShortCircuits() throws IOException {
        Path file = assembledFile();
        when(documentExtractionService.calculateChecksum(file)).thenReturn("md5-dup");
        Document existing = new Document();
        existing.setDocumentKey("dup-key");
        when(documentGateway.findByFileChecksum("md5-dup")).thenReturn(Optional.of(existing));

        assertThatThrownBy(
                        () ->
                                exe.execute(
                                        file,
                                        "assembled.pdf",
                                        "u-1",
                                        "d-1",
                                        DocumentVisibilityEnum.INTERNAL,
                                        null,
                                        null,
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate file detected")
                .hasMessageContaining("dup-key");
        verify(documentGateway, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("成功链路：UPLOADED 落库 + checksum 回写 + visibility null→INTERNAL + 发布事件")
    void successPersistsAndPublishes() throws IOException {
        Path file = assembledFile();
        when(documentExtractionService.calculateChecksum(file)).thenReturn("md5-new");
        when(documentGateway.findByFileChecksum("md5-new")).thenReturn(Optional.empty());
        when(support.newDocumentKey()).thenReturn("key-f1");
        when(support.detectFileType(file)).thenReturn("application/pdf");

        DocumentVO vo = exe.execute(file, "assembled.pdf", "u-1", "d-1", null, null, "标签", "学习");

        ArgumentCaptor<Document> cap = ArgumentCaptor.forClass(Document.class);
        verify(documentGateway).save(cap.capture());
        Document saved = cap.getValue();
        assertThat(saved.getDocumentKey()).isEqualTo("key-f1");
        assertThat(saved.getStatus()).isEqualTo(DocumentStatusEnum.UPLOADED);
        assertThat(saved.getVisibility()).isEqualTo(DocumentVisibilityEnum.INTERNAL);
        assertThat(saved.getFileChecksum()).isEqualTo("md5-new");
        assertThat(saved.getFileSize()).isEqualTo(4L);
        assertThat(saved.getFileType()).isEqualTo("application/pdf");
        assertThat(saved.getStoragePath()).isEqualTo(file.toString());
        ArgumentCaptor<Object> eventCap = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCap.capture());
        assertThat(((DocumentCreatedEvent) eventCap.getValue()).fileName())
                .isEqualTo("assembled.pdf");
        verify(documentImageSupport).extractAndPersist(eq(saved), eq(file));
        assertThat(vo.getDocumentKey()).isEqualTo("key-f1");
    }

    @Test
    @DisplayName("显式 visibility 不被归一覆盖（PUBLIC 原样落库）")
    void explicitVisibilityPreserved() throws IOException {
        Path file = assembledFile();
        when(documentExtractionService.calculateChecksum(file)).thenReturn("md5-pub");
        when(documentGateway.findByFileChecksum("md5-pub")).thenReturn(Optional.empty());
        when(support.newDocumentKey()).thenReturn("key-pub");

        exe.execute(
                file,
                "assembled.pdf",
                "u-1",
                "d-1",
                DocumentVisibilityEnum.PUBLIC,
                null,
                null,
                null);

        ArgumentCaptor<Document> cap = ArgumentCaptor.forClass(Document.class);
        verify(documentGateway).save(cap.capture());
        assertThat(cap.getValue().getVisibility()).isEqualTo(DocumentVisibilityEnum.PUBLIC);
    }

    @Test
    @DisplayName("图片抽取抛异常 → 吞掉并保主流程：落库与事件发布照常")
    void imageExtractionFailureIsSwallowed() throws IOException {
        Path file = assembledFile();
        when(documentExtractionService.calculateChecksum(file)).thenReturn("md5-img");
        when(documentGateway.findByFileChecksum("md5-img")).thenReturn(Optional.empty());
        when(support.newDocumentKey()).thenReturn("key-img");
        doThrow(new RuntimeException("extract fail"))
                .when(documentImageSupport)
                .extractAndPersist(any(), any(Path.class));

        DocumentVO vo =
                exe.execute(
                        file,
                        "assembled.pdf",
                        "u-1",
                        "d-1",
                        DocumentVisibilityEnum.INTERNAL,
                        null,
                        null,
                        null);

        verify(documentGateway).save(any(Document.class));
        verify(eventPublisher).publishEvent(any(Object.class));
        assertThat(vo.getStatus()).isEqualTo("UPLOADED");
    }
}
