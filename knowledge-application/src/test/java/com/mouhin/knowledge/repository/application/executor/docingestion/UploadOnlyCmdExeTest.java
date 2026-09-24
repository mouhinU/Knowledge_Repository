package com.mouhin.knowledge.repository.application.executor.docingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.springframework.web.multipart.MultipartFile;

/**
 * 仅上传命令执行器单测：锁定空文件拒绝、MD5 去重短路、成功链路（UPLOADED 状态落库 + checksum 回写 + 图片抽取容错 + 发布创建事件，不做文本提取）。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("仅上传命令执行器 (UploadOnlyCmdExe)")
class UploadOnlyCmdExeTest {

    @TempDir Path tmp;

    private final DocumentIngestionSupport support = mock(DocumentIngestionSupport.class);
    private final DocumentGateway documentGateway = mock(DocumentGateway.class);
    private final DocumentExtractionGateway documentExtractionService =
            mock(DocumentExtractionGateway.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final DocumentImageSupport documentImageSupport = mock(DocumentImageSupport.class);
    private final UploadOnlyCmdExe exe =
            new UploadOnlyCmdExe(
                    support,
                    documentGateway,
                    documentExtractionService,
                    eventPublisher,
                    documentImageSupport);

    private MultipartFile stubFile() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("note.pdf");
        when(file.getSize()).thenReturn(2048L);
        return file;
    }

    private Document happyPath(MultipartFile file) throws IOException {
        Path tempFile = Files.createTempFile(tmp, "upload", ".pdf");
        Path permanentFile = Files.createTempFile(tmp, "stored", ".pdf");
        when(support.saveToTemp(file)).thenReturn(tempFile);
        when(documentExtractionService.calculateChecksum(tempFile)).thenReturn("md5-abc");
        when(documentGateway.findByFileChecksum("md5-abc")).thenReturn(Optional.empty());
        when(support.copyToStorage(tempFile, "note.pdf")).thenReturn(permanentFile);
        when(support.newDocumentKey()).thenReturn("key-9");
        Document doc = new Document();
        doc.setId(3L);
        doc.setDocumentKey("key-9");
        doc.setFileName("note.pdf");
        doc.setStoragePath("/data/docs/note.pdf");
        doc.setStatus(DocumentStatusEnum.UPLOADED);
        doc.setVisibility(DocumentVisibilityEnum.INTERNAL);
        doc.setOwnerId("u-1");
        doc.setDepartmentId("d-1");
        when(support.buildDocument(
                        anyString(),
                        any(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(doc);
        return doc;
    }

    @Test
    @DisplayName("文件为 null / 空 → 'File must not be empty'，不进入任何 IO")
    void emptyFileRejected() throws IOException {
        assertThatThrownBy(
                        () ->
                                exe.execute(
                                        null,
                                        "u-1",
                                        "d-1",
                                        DocumentVisibilityEnum.INTERNAL,
                                        null,
                                        null,
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File must not be empty");
        MultipartFile empty = mock(MultipartFile.class);
        when(empty.isEmpty()).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                exe.execute(
                                        empty,
                                        "u-1",
                                        "d-1",
                                        DocumentVisibilityEnum.INTERNAL,
                                        null,
                                        null,
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File must not be empty");
        verify(support, never()).saveToTemp(any());
        verify(documentGateway, never()).save(any());
    }

    @Test
    @DisplayName("checksum 命中已有文档 → 去重短路，不落库不发事件，但清理临时文件")
    void duplicateChecksumShortCircuits() throws IOException {
        MultipartFile file = stubFile();
        Path tempFile = Files.createTempFile(tmp, "upload", ".pdf");
        when(support.saveToTemp(file)).thenReturn(tempFile);
        when(documentExtractionService.calculateChecksum(tempFile)).thenReturn("md5-dup");
        Document existing = new Document();
        existing.setDocumentKey("dup-key");
        when(documentGateway.findByFileChecksum("md5-dup")).thenReturn(Optional.of(existing));

        assertThatThrownBy(
                        () ->
                                exe.execute(
                                        file,
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
        verify(support).deleteTempFile(tempFile);
    }

    @Test
    @DisplayName("成功链路：checksum 回写文档、落库、抽取图片、发布创建事件，且不做文本提取")
    void successPersistsUploadsAndPublishes() throws IOException {
        MultipartFile file = stubFile();
        Document doc = happyPath(file);

        DocumentVO vo =
                exe.execute(file, "u-1", "d-1", DocumentVisibilityEnum.PUBLIC, "[]", "标签", "工作");

        ArgumentCaptor<Document> cap = ArgumentCaptor.forClass(Document.class);
        verify(documentGateway).save(cap.capture());
        assertThat(cap.getValue().getFileChecksum()).isEqualTo("md5-abc");
        assertThat(cap.getValue().getStatus()).isEqualTo(DocumentStatusEnum.UPLOADED);
        ArgumentCaptor<Object> eventCap = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCap.capture());
        assertThat(((DocumentCreatedEvent) eventCap.getValue()).documentKey()).isEqualTo("key-9");
        verify(documentImageSupport).extractAndPersist(eq(doc), any(Path.class));
        verify(documentExtractionService, never()).extractText(any(), any(Long.class), anyString());
        verify(support).deleteTempFile(any(Path.class));
        assertThat(vo.getDocumentKey()).isEqualTo("key-9");
    }

    @Test
    @DisplayName("图片抽取抛异常 → 吞掉并保主流程：落库与事件发布照常")
    void imageExtractionFailureIsSwallowed() throws IOException {
        MultipartFile file = stubFile();
        Document doc = happyPath(file);
        doThrow(new RuntimeException("no images"))
                .when(documentImageSupport)
                .extractAndPersist(eq(doc), any(Path.class));

        DocumentVO vo =
                exe.execute(file, "u-1", "d-1", DocumentVisibilityEnum.INTERNAL, null, null, null);

        verify(documentGateway).save(any(Document.class));
        verify(eventPublisher).publishEvent(any(Object.class));
        assertThat(vo.getDocumentKey()).isEqualTo("key-9");
    }

    @Test
    @DisplayName("计算 checksum IO 异常 → 包装为 IllegalStateException，不落库")
    void checksumFailureWrapped() throws IOException {
        MultipartFile file = stubFile();
        Path tempFile = Files.createTempFile(tmp, "upload", ".pdf");
        when(support.saveToTemp(file)).thenReturn(tempFile);
        when(documentExtractionService.calculateChecksum(tempFile))
                .thenThrow(new IOException("read fail"));

        assertThatThrownBy(
                        () ->
                                exe.execute(
                                        file,
                                        "u-1",
                                        "d-1",
                                        DocumentVisibilityEnum.INTERNAL,
                                        null,
                                        null,
                                        null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to calculate file checksum");
        verify(documentGateway, never()).save(any());
    }
}
