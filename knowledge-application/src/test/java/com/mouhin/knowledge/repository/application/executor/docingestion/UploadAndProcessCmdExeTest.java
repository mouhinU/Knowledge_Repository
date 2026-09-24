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
import com.mouhin.knowledge.repository.domain.event.DocumentCreatedEvent;
import com.mouhin.knowledge.repository.domain.gateway.DocumentExtractionGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingConfig;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传并处理命令执行器单测：锁定空文件拒绝、checksum 去重短路、成功链路（落库 → 发布创建事件 → 委托处理流水线 → 清理临时文件）。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("上传并处理命令执行器 (UploadAndProcessCmdExe)")
class UploadAndProcessCmdExeTest {

    @TempDir Path tmp;

    private final DocumentIngestionSupport support = mock(DocumentIngestionSupport.class);
    private final DocumentGateway documentGateway = mock(DocumentGateway.class);
    private final DocumentExtractionGateway documentExtractionService =
            mock(DocumentExtractionGateway.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final UploadAndProcessCmdExe exe =
            new UploadAndProcessCmdExe(
                    support, documentGateway, documentExtractionService, eventPublisher);

    private MultipartFile stubFile() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("note.pdf");
        when(file.getSize()).thenReturn(1024L);
        return file;
    }

    private Document stubbedDocument(String key) {
        Document doc = new Document();
        doc.setId(7L);
        doc.setDocumentKey(key);
        doc.setFileName("note.pdf");
        doc.setStoragePath("/data/docs/note.pdf");
        doc.setStatus(DocumentStatusEnum.INDEXED);
        doc.setVisibility(DocumentVisibilityEnum.INTERNAL);
        doc.setOwnerId("u-1");
        doc.setDepartmentId("d-1");
        return doc;
    }

    @Test
    @DisplayName("文件为 null / 空 → 'File must not be empty'，不落盘不落库")
    void emptyFileRejected() throws IOException {
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
        verify(support, never()).saveToTemp(any());
        verify(documentGateway, never()).save(any());
    }

    @Test
    @DisplayName("checksum 命中已有文档 → 'Duplicate file detected'，不落库不发事件")
    void duplicateChecksumShortCircuits() throws IOException {
        MultipartFile file = stubFile();
        Path tempFile = Files.createTempFile(tmp, "upload", ".pdf");
        when(support.saveToTemp(file)).thenReturn(tempFile);
        when(documentExtractionService.extractText(any(), anyLong(), anyString()))
                .thenReturn(new ExtractionResult(List.of("text"), 1, false, "md5-dup", "pdf"));
        Document existing = new Document();
        existing.setDocumentKey("old-key");
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
                .hasMessageContaining("old-key");
        verify(documentGateway, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        // 短路后仍须清理临时文件（finally 分支）
        verify(support).deleteTempFile(tempFile);
    }

    @Test
    @DisplayName("成功链路：落库 → 发布 DocumentCreatedEvent → processDocument → 返回 VO")
    void successSavesPublishesAndProcesses() throws IOException {
        MultipartFile file = stubFile();
        Path tempFile = Files.createTempFile(tmp, "upload", ".pdf");
        Path permanentFile = Files.createTempFile(tmp, "stored", ".pdf");
        when(support.saveToTemp(file)).thenReturn(tempFile);
        when(documentExtractionService.extractText(any(), anyLong(), anyString()))
                .thenReturn(new ExtractionResult(List.of("text"), 1, false, "md5-new", "pdf"));
        when(documentGateway.findByFileChecksum("md5-new")).thenReturn(Optional.empty());
        when(support.copyToStorage(tempFile, "note.pdf")).thenReturn(permanentFile);
        when(support.newDocumentKey()).thenReturn("key-1");
        when(support.buildDocument(
                        eq("key-1"),
                        eq(file),
                        eq(permanentFile.toString()),
                        eq("u-1"),
                        eq("d-1"),
                        eq(DocumentVisibilityEnum.INTERNAL),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(stubbedDocument("key-1"));

        ExtractionResult result = new ExtractionResult(List.of("text"), 1, false, "md5-new", "pdf");
        DocumentVO vo =
                exe.execute(
                        file,
                        "u-1",
                        "d-1",
                        DocumentVisibilityEnum.INTERNAL,
                        null,
                        "标签",
                        ChunkingConfig.defaultConfig());

        verify(documentGateway).save(any(Document.class));
        ArgumentCaptor<Object> eventCap = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCap.capture());
        assertThat(eventCap.getValue()).isInstanceOf(DocumentCreatedEvent.class);
        DocumentCreatedEvent event = (DocumentCreatedEvent) eventCap.getValue();
        assertThat(event.documentKey()).isEqualTo("key-1");
        assertThat(event.fileName()).isEqualTo("note.pdf");
        verify(support).processDocument(any(Document.class), eq(result));
        verify(support).deleteTempFile(tempFile);
        assertThat(vo.getDocumentKey()).isEqualTo("key-1");
        assertThat(vo.getStatus()).isEqualTo("INDEXED");
    }

    @Test
    @DisplayName("提取文本 IO 异常 → 包装为 IllegalStateException，不落库")
    void extractionFailureWrapped() throws IOException {
        MultipartFile file = stubFile();
        Path tempFile = Files.createTempFile(tmp, "upload", ".pdf");
        when(support.saveToTemp(file)).thenReturn(tempFile);
        when(documentExtractionService.extractText(any(), anyLong(), anyString()))
                .thenThrow(new IOException("corrupted pdf"));

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
                .hasMessageContaining("Failed to extract text from PDF");
        verify(documentGateway, never()).save(any());
    }

    @Test
    @DisplayName("文档必填校验：ownerId 空白 → validateForCreate 拒绝，不落库")
    void blankOwnerIdFailsValidation() throws IOException {
        MultipartFile file = stubFile();
        Path tempFile = Files.createTempFile(tmp, "upload", ".pdf");
        Path permanentFile = Files.createTempFile(tmp, "stored", ".pdf");
        when(support.saveToTemp(file)).thenReturn(tempFile);
        when(documentExtractionService.extractText(any(), anyLong(), anyString()))
                .thenReturn(new ExtractionResult(List.of("text"), 1, false, "md5-x", "pdf"));
        when(documentGateway.findByFileChecksum("md5-x")).thenReturn(Optional.empty());
        when(support.copyToStorage(tempFile, "note.pdf")).thenReturn(permanentFile);
        when(support.newDocumentKey()).thenReturn("key-2");
        Document invalid = stubbedDocument("key-2");
        invalid.setOwnerId(" ");
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
                .thenReturn(invalid);

        assertThatThrownBy(
                        () ->
                                exe.execute(
                                        file,
                                        " ",
                                        "d-1",
                                        DocumentVisibilityEnum.INTERNAL,
                                        null,
                                        null,
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ownerId must not be blank");
        verify(documentGateway, never()).save(any());
    }
}
