package com.mouhin.knowledge.repository.application.executor.docingestion;

import com.mouhin.knowledge.repository.domain.gateway.DocumentImageExtractorGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentImageGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 重新入库配图清理支撑单测（{@link DocumentImageSupport#deleteImages}）。
 * <p>
 * 锁定「删图并重抽」前置清理行为：（1）assetRoot 之内落盘文件被删除、并删除全部关系记录；
 * （2）越界（非 assetRoot 之下）文件跳过删除，但记录仍被删除；（3）空 / 空白 storagePath 跳过；
 * （4）doc / id 为空直接返回 0 且不动记录。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-20
 */
@DisplayName("重新入库配图清理 (DocumentImageSupport.deleteImages)")
class DocumentImageSupportDeleteImagesTest {

    @TempDir
    Path assetRoot;

    private final DocumentImageExtractorGateway extractor = mock(DocumentImageExtractorGateway.class);
    private final DocumentImageGateway imageGateway = mock(DocumentImageGateway.class);
    private DocumentImageSupport support;

    @BeforeEach
    void setUp() {
        support = new DocumentImageSupport(extractor, imageGateway, assetRoot.toString());
    }

    private DocumentImage imageAt(Long id, String storagePath) {
        DocumentImage img = new DocumentImage();
        img.setId(id);
        img.setAssetKey("key-" + id);
        img.setStoragePath(storagePath);
        return img;
    }

    @Test
    @DisplayName("assetRoot 内文件删除 + 记录全部清除")
    void deletesInRootFilesAndRows() throws IOException {
        Path inside = assetRoot.resolve("doc-1").resolve("a.png");
        Files.createDirectories(inside.getParent());
        Files.write(inside, new byte[]{1, 2, 3});
        when(imageGateway.listByDocumentId(1L))
                .thenReturn(List.of(imageAt(10L, inside.toString())));

        Document doc = new Document();
        doc.setId(1L);
        doc.setDocumentKey("doc-1");

        int removed = support.deleteImages(doc);

        assertEquals(1, removed);
        assertFalse(Files.exists(inside), "assetRoot 内的配图文件应被删除");
        verify(imageGateway).deleteByDocumentId(1L);
    }

    @Test
    @DisplayName("越界文件跳过删除，但记录仍被清除")
    void skipsOutOfRootFilesButClearsRows() throws IOException {
        Path outside = Files.createTempFile("outside-asset", ".png");
        try {
            when(imageGateway.listByDocumentId(2L))
                    .thenReturn(List.of(imageAt(20L, outside.toString())));

            Document doc = new Document();
            doc.setId(2L);
            doc.setDocumentKey("doc-2");

            int removed = support.deleteImages(doc);

            assertEquals(0, removed, "越界文件不应被删除");
            assertTrue(Files.exists(outside), "越界文件必须保留");
            verify(imageGateway).deleteByDocumentId(2L);
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    @DisplayName("空 / 空白 storagePath 跳过文件处理")
    void skipsBlankPaths() {
        when(imageGateway.listByDocumentId(3L)).thenReturn(List.of(
                imageAt(31L, null), imageAt(32L, "  ")));

        Document doc = new Document();
        doc.setId(3L);
        doc.setDocumentKey("doc-3");

        assertEquals(0, support.deleteImages(doc));
        verify(imageGateway).deleteByDocumentId(3L);
    }

    @Test
    @DisplayName("doc / id 为空 → 返回 0，不触碰网关")
    void nullGuard() {
        assertEquals(0, support.deleteImages(null));
        Document noId = new Document();
        noId.setDocumentKey("x");
        assertEquals(0, support.deleteImages(noId));
        verify(imageGateway, never()).deleteByDocumentId(anyLong());
    }
}
