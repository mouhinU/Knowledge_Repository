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

import com.mouhin.knowledge.repository.application.dto.PreviewFileQuery;
import com.mouhin.knowledge.repository.client.dto.PreviewResult;
import com.mouhin.knowledge.repository.domain.gateway.DocumentExtractionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingConfig;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingStrategyEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.domain.service.DocumentIngestionDomainService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传文件解析预览查询执行器单测：锁定空文件拒绝、成功预览组装（提取 + 分块 + 页/块明细透传、不入库不发事件）、 IO 异常包装与临时文件必清理（finally 语义）。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("上传文件解析预览查询执行器 (PreviewFileQryExe)")
class PreviewFileQryExeTest {

    @TempDir Path tmp;

    private final DocumentIngestionSupport support = mock(DocumentIngestionSupport.class);
    private final DocumentExtractionGateway documentExtractionService =
            mock(DocumentExtractionGateway.class);
    private final DocumentIngestionDomainService ingestionDomainService =
            mock(DocumentIngestionDomainService.class);
    private final PreviewFileQryExe exe =
            new PreviewFileQryExe(support, documentExtractionService, ingestionDomainService);

    private PreviewFileQuery query(MultipartFile file) {
        PreviewFileQuery q = new PreviewFileQuery();
        q.setFile(file);
        q.setChunkSize(500);
        q.setOverlap(50);
        q.setStrategy("FIXED_SIZE");
        return q;
    }

    private MultipartFile stubFile() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("note.pdf");
        when(file.getSize()).thenReturn(512L);
        return file;
    }

    @Test
    @DisplayName("文件为 null / 空 → 'File must not be empty'，不落盘")
    void emptyFileRejected() throws IOException {
        assertThatThrownBy(() -> exe.execute(query(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File must not be empty");
        MultipartFile empty = mock(MultipartFile.class);
        when(empty.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> exe.execute(query(empty)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File must not be empty");
        verify(support, never()).saveToTemp(any());
    }

    @Test
    @DisplayName("成功预览：提取 + 分块参数规范化 + 页/块明细透传，全程不落库")
    void successBuildsPreviewWithoutPersisting() throws IOException {
        MultipartFile file = stubFile();
        Path tempFile = Files.createTempFile(tmp, "preview", ".pdf");
        when(support.saveToTemp(file)).thenReturn(tempFile);
        ExtractionResult extraction =
                new ExtractionResult(List.of("第一段", "第二段"), 2, false, "md5-p", "pdf");
        when(documentExtractionService.extractText(tempFile, 512L, "note.pdf"))
                .thenReturn(extraction);
        when(support.resolveStrategy("FIXED_SIZE")).thenReturn(ChunkingStrategyEnum.FIXED_SIZE);
        ChunkingConfig config = ChunkingConfig.defaultConfig();
        when(support.buildConfig(500, 50, ChunkingStrategyEnum.FIXED_SIZE)).thenReturn(config);
        DocumentChunk chunk = new DocumentChunk();
        chunk.setContent("第一段");
        when(ingestionDomainService.chunkDocument(any(), eq(List.of("第一段", "第二段")), eq(config)))
                .thenReturn(List.of(chunk));

        PreviewResult result = exe.execute(query(file));

        assertThat(result.fileName()).isEqualTo("note.pdf");
        assertThat(result.format()).isEqualTo("pdf");
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.extractedSections()).isEqualTo(2);
        assertThat(result.likelyScanned()).isFalse();
        assertThat(result.checksum()).isEqualTo("md5-p");
        assertThat(result.totalChunks()).isEqualTo(1);
        verify(support).deleteTempFile(tempFile);
    }

    @Test
    @DisplayName("提取抛 IOException → 包装 'Failed to extract text'，finally 仍清理临时文件")
    void extractionIoFailureWrappedAndTempCleaned() throws IOException {
        MultipartFile file = stubFile();
        Path tempFile = Files.createTempFile(tmp, "preview", ".pdf");
        when(support.saveToTemp(file)).thenReturn(tempFile);
        when(documentExtractionService.extractText(any(), anyLong(), anyString()))
                .thenThrow(new IOException("password protected"));

        assertThatThrownBy(() -> exe.execute(query(file)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to extract text")
                .hasMessageContaining("password protected");
        verify(support).deleteTempFile(tempFile);
    }

    @Test
    @DisplayName("落盘 IO 失败 → 'Failed to save uploaded file'，无临时文件可清")
    void saveToTempFailureWrapped() throws IOException {
        MultipartFile file = stubFile();
        when(support.saveToTemp(file)).thenThrow(new IOException("disk full"));

        assertThatThrownBy(() -> exe.execute(query(file)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to save uploaded file");
        verify(support, never()).deleteTempFile(any());
        verify(documentExtractionService, never()).extractText(any(), anyLong(), anyString());
    }
}
