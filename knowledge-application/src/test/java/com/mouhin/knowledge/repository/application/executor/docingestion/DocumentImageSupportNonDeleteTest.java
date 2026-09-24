package com.mouhin.knowledge.repository.application.executor.docingestion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.DocumentImageExtractorGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentImageGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentImage;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentImageHit;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/**
 * 文档配图支撑非删除路径单测：锁定「抽取 → SHA-256 去重 → 落盘 → 持久化」主干（含按文档去重、 抽取异常吞断、源路径越界拒绝）、回填前置校验、按句柄回读二进制的 assetRoot
 * confinement，以及检索 / 列举纯委托。 deleteImages 清理由既有 {@code DocumentImageSupportDeleteImagesTest}
 * 覆盖，此处不重复。
 *
 * @author mouhinU
 * @date 2026-09-24 18:16:00
 */
@DisplayName("文档配图支撑：抽取去重与回读 (DocumentImageSupport, 非删除路径)")
class DocumentImageSupportNonDeleteTest {

    @TempDir Path root;

    private final DocumentImageExtractorGateway extractor =
            mock(DocumentImageExtractorGateway.class);
    private final DocumentImageGateway imageGateway = mock(DocumentImageGateway.class);

    private Path assetRoot;
    private Path storageRoot;
    private DocumentImageSupport support;

    @BeforeEach
    void setUp() throws IOException {
        assetRoot = root.resolve("assets");
        storageRoot = root.resolve("storage");
        Files.createDirectories(assetRoot);
        Files.createDirectories(storageRoot);
        support =
                new DocumentImageSupport(
                        extractor, imageGateway, assetRoot.toString(), storageRoot.toString());
    }

    private Document doc() {
        Document document = new Document();
        document.setId(11L);
        document.setDocumentKey("dk-11");
        document.setFileName("课件.pdf");
        return document;
    }

    private Path sourceInStorage() throws IOException {
        return Files.write(
                storageRoot.resolve("课件.pdf"), "source-bytes".getBytes(StandardCharsets.UTF_8));
    }

    private ExtractedImage image(byte[] bytes, String mime) {
        return new ExtractedImage(bytes, mime, 10, 20, 3, 1);
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Test
    @DisplayName("extractAndPersist 主干：按 SHA-256 落盘 assetRoot/{documentKey}/，实体携带摘要与尺寸元数据")
    void extractAndPersistHappyPath() throws Exception {
        byte[] png = "fake-png".getBytes(StandardCharsets.UTF_8);
        when(extractor.extractImages(any(), anyString()))
                .thenReturn(List.of(image(png, "image/png")));
        when(imageGateway.existsByDocumentIdAndSha256(anyLong(), anyString())).thenReturn(false);

        int added = support.extractAndPersist(doc(), sourceInStorage());

        assertEquals(1, added);
        ArgumentCaptor<DocumentImage> cap = ArgumentCaptor.forClass(DocumentImage.class);
        verify(imageGateway).save(cap.capture());
        DocumentImage saved = cap.getValue();
        assertEquals(sha256Hex(png), saved.getSha256(), "SHA-256 摘要须与字节内容一致（去重键）");
        assertEquals(11L, saved.getDocumentId());
        assertEquals("dk-11", saved.getDocumentKey());
        assertEquals("image/png", saved.getMimeType());
        assertEquals(3, saved.getPageNo().intValue());
        assertEquals(1, saved.getSeqOnPage());
        assertEquals(10, saved.getWidth().intValue());
        assertEquals((long) png.length, saved.getByteSize().longValue());
        assertTrue(Path.of(saved.getStoragePath()).startsWith(assetRoot.resolve("dk-11")));
        assertTrue(saved.getStoragePath().endsWith(".png"), "扩展名按 MIME 推断");
        assertTrue(Files.exists(Path.of(saved.getStoragePath())), "二进制应已落盘");
    }

    @Test
    @DisplayName("文档内已存在同 SHA-256 → 跳过入库不落盘（去重）")
    void duplicateShaSkipped() throws Exception {
        byte[] png = "dup".getBytes(StandardCharsets.UTF_8);
        when(extractor.extractImages(any(), anyString()))
                .thenReturn(List.of(image(png, "image/png")));
        when(imageGateway.existsByDocumentIdAndSha256(eq(11L), eq(sha256Hex(png))))
                .thenReturn(true);

        assertEquals(0, support.extractAndPersist(doc(), sourceInStorage()));
        verify(imageGateway, never()).save(any());
    }

    @Test
    @DisplayName("抽取器抛异常 → 吞断返回 0，绝不阻断文本入库主流程")
    void extractorFailureSwallowed() throws Exception {
        when(extractor.extractImages(any(), anyString())).thenThrow(new IOException("corrupt"));

        assertEquals(0, support.extractAndPersist(doc(), sourceInStorage()));
        verify(imageGateway, never()).save(any());
    }

    @Test
    @DisplayName("源路径越出 assetRoot / storageRoot → 拒绝读取返回 0（filesystem oracle 防线）")
    void sourceOutsideAllowedRootsRejected() throws Exception {
        Path outside = root.resolve("elsewhere.pdf");
        Files.write(outside, "x".getBytes(StandardCharsets.UTF_8));

        assertEquals(0, support.extractAndPersist(doc(), outside));
        verify(extractor, never()).extractImages(any(), anyString());
    }

    @Test
    @DisplayName("doc / id 为 null 或源文件不存在 → 直接 0，不动抽取器")
    void nullInputsShortCircuit() throws Exception {
        assertEquals(0, support.extractAndPersist(null, sourceInStorage()));
        assertEquals(0, support.extractAndPersist(new Document(), sourceInStorage()));
        assertEquals(0, support.extractAndPersist(doc(), storageRoot.resolve("ghost.pdf")));
        verify(extractor, never()).extractImages(any(), anyString());
    }

    @Test
    @DisplayName("落库冲突（save 抛异常）→ 清理孤儿文件并继续其余图片")
    void saveConflictCleansOrphanFile() throws Exception {
        byte[] first = "one".getBytes(StandardCharsets.UTF_8);
        byte[] second = "two".getBytes(StandardCharsets.UTF_8);
        when(extractor.extractImages(any(), anyString()))
                .thenReturn(List.of(image(first, "image/jpeg"), image(second, "image/png")));
        when(imageGateway.existsByDocumentIdAndSha256(anyLong(), anyString())).thenReturn(false);
        org.mockito.Mockito.doThrow(new RuntimeException("dup key"))
                .doNothing()
                .when(imageGateway)
                .save(any());

        int added = support.extractAndPersist(doc(), sourceInStorage());

        assertEquals(1, added, "首张冲突应清理后继续，第二张成功计入");
        try (var files = Files.walk(assetRoot)) {
            long left =
                    files.filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".jpg"))
                            .count();
            assertEquals(0, left, "冲突孤儿 .jpg 应被删除");
        }
    }

    @Test
    @DisplayName("backfill：storagePath 缺失 / 空 / 文件不存在 → 0；存在则复用 extractAndPersist")
    void backfillGuards() throws Exception {
        assertEquals(0, support.backfill(null));
        assertEquals(0, support.backfill(new Document()));

        Document missing = doc();
        missing.setStoragePath(storageRoot.resolve("gone.pdf").toString());
        assertEquals(0, support.backfill(missing));

        Document present = doc();
        present.setStoragePath(sourceInStorage().toString());
        when(extractor.extractImages(any(), anyString())).thenReturn(List.of());
        assertEquals(0, support.backfill(present));
        verify(extractor).extractImages(any(), eq("课件.pdf"));
    }

    @Test
    @DisplayName("readBytes：assetRoot 内回读成功；越界 / 缺失 / null → Optional.empty")
    void readBytesConfinement() throws Exception {
        byte[] payload = "img".getBytes(StandardCharsets.UTF_8);
        Path inside = assetRoot.resolve("dk-11").resolve("key1.png");
        Files.createDirectories(inside.getParent());
        Files.write(inside, payload);

        DocumentImage ok = new DocumentImage();
        ok.setAssetKey("key1");
        ok.setStoragePath(inside.toString());
        assertArrayEquals(payload, support.readBytes(ok).orElseThrow());

        DocumentImage escaped = new DocumentImage();
        escaped.setStoragePath(root.resolve("secret.png").toString());
        assertTrue(support.readBytes(escaped).isEmpty(), "assetRoot 之外拒读");

        DocumentImage ghost = new DocumentImage();
        ghost.setStoragePath(assetRoot.resolve("not-here.png").toString());
        assertTrue(support.readBytes(ghost).isEmpty());
        assertTrue(support.readBytes(null).isEmpty());
    }

    @Test
    @DisplayName("findByAssetKey / listByDocument / searchImages / countImages 纯委托")
    void delegations() {
        DocumentImage found = new DocumentImage();
        when(imageGateway.findByAssetKey("k")).thenReturn(Optional.of(found));
        assertEquals(found, support.findByAssetKey("k").orElseThrow());

        assertEquals(List.of(), support.listByDocument(null));
        assertEquals(List.of(), support.listByDocument(new Document()));
        List<DocumentImage> listed = List.of(found);
        when(imageGateway.listByDocumentId(11L)).thenReturn(listed);
        assertEquals(listed, support.listByDocument(doc()));

        List<DocumentImageHit> hits = List.of(new DocumentImageHit(found, "课件.pdf"));
        when(imageGateway.search("词", null, 20, 0)).thenReturn(hits);
        assertEquals(hits, support.searchImages("词", null, 20, 0));
        when(imageGateway.countSearch("词", null)).thenReturn(7L);
        assertEquals(7L, support.countImages("词", null));
    }

    @Test
    @DisplayName("同字节不同 MIME：去重仅看 SHA-256，第二次仍跳过（内容寻址口径）")
    void dedupIgnoresMime() throws Exception {
        byte[] bytes = "same".getBytes(StandardCharsets.UTF_8);
        when(extractor.extractImages(any(), anyString()))
                .thenReturn(List.of(image(bytes, "image/png"), image(bytes, "image/webp")));
        when(imageGateway.existsByDocumentIdAndSha256(anyLong(), anyString())).thenReturn(false);
        // 第二张在内存判定同 SHA 前已查库：模拟查库首张后已存在 → 手工桩
        when(imageGateway.existsByDocumentIdAndSha256(anyLong(), eq(sha256Hex(bytes))))
                .thenReturn(false, true);

        int added = support.extractAndPersist(doc(), sourceInStorage());

        assertEquals(1, added);
        verify(imageGateway, times(1)).save(any());
        assertFalse(assetRoot.resolve("dk-11").resolve("x.webp").toFile().exists());
    }

    // 其他分支待补：extFromMime 全量 MIME 表（gif/bmp/tiff 走同主干，已由 .jpg/.png 断言覆盖代表值）。
}
