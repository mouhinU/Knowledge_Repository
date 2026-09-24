package com.mouhin.knowledge.repository.application.executor.docingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.client.dto.ChunkDetail;
import com.mouhin.knowledge.repository.client.dto.CustomChunkInput;
import com.mouhin.knowledge.repository.client.dto.PageDetail;
import com.mouhin.knowledge.repository.domain.event.DocumentProcessedEvent;
import com.mouhin.knowledge.repository.domain.gateway.DocumentChunkGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.gateway.VectorStoreGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingConfig;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingStrategyEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.domain.service.DocumentIngestionDomainService;
import com.mouhin.knowledge.repository.domain.service.IndexProgressCallback;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档摄入共享支撑单测：锁定分块策略解析回退（无效值 → FIXED_SIZE）、处理流水线的状态机与事件发布 （UPLOADED→PROCESSING→INDEXED，空分块 / 异常回退
 * FAILED）、上传聚合装配缺省、 临时文件 / 存储落盘与扩展名类型探测、JSON 控制字符清洗与页 / 块明细组装。
 *
 * @author mouhinU
 * @date 2026-09-24 18:16:00
 */
@DisplayName("文档摄入共享支撑 (DocumentIngestionSupport)")
class DocumentIngestionSupportTest {

    private final DocumentGateway documentGateway = mock(DocumentGateway.class);
    private final DocumentChunkGateway chunkGateway = mock(DocumentChunkGateway.class);
    private final VectorStoreGateway vectorStoreService = mock(VectorStoreGateway.class);
    private final DocumentIngestionDomainService domainService =
            mock(DocumentIngestionDomainService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    @TempDir Path storageDir;

    private DocumentIngestionSupport support;

    @BeforeEach
    void setUp() {
        support =
                new DocumentIngestionSupport(
                        documentGateway,
                        chunkGateway,
                        vectorStoreService,
                        domainService,
                        eventPublisher,
                        storageDir.toString());
    }

    private Document uploadedDocument() {
        Document document = new Document();
        document.setId(7L);
        document.setDocumentKey("dk-7");
        document.setFileName("样本.pdf");
        document.setStatus(DocumentStatusEnum.UPLOADED);
        document.setOwnerId("t-1");
        document.setDepartmentId("d-1");
        document.setChunkingConfig(ChunkingConfig.defaultConfig());
        return document;
    }

    private DocumentChunk chunk(String content) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setContent(content);
        chunk.setChunkIndex(0);
        chunk.setStartPage(1);
        chunk.setEndPage(1);
        chunk.setTokenCount(10);
        return chunk;
    }

    @Test
    @DisplayName("resolveStrategy：null / 空白 / 非法值回退 FIXED_SIZE；合法值大小写不敏感")
    void resolveStrategyFallback() {
        assertEquals(ChunkingStrategyEnum.FIXED_SIZE, support.resolveStrategy(null));
        assertEquals(ChunkingStrategyEnum.FIXED_SIZE, support.resolveStrategy(" "));
        assertEquals(ChunkingStrategyEnum.FIXED_SIZE, support.resolveStrategy("NOT_A_STRATEGY"));
        assertEquals(ChunkingStrategyEnum.RECURSIVE, support.resolveStrategy("recursive"));
        assertEquals(ChunkingStrategyEnum.PAGE, support.resolveStrategy("PAGE"));
    }

    @Test
    @DisplayName("buildConfig：策略 null 回退 FIXED_SIZE，段落 / 页面边界默认开启")
    void buildConfigDefaults() {
        ChunkingConfig config = support.buildConfig(300, 30, null);
        assertEquals(ChunkingStrategyEnum.FIXED_SIZE, config.getStrategy());
        assertEquals(300, config.getMaxChunkSize());
        assertTrue(config.isRespectParagraphBoundary() && config.isRespectPageBoundary());
        // 非法尺寸由值对象自身拒绝（不吞参数错误）
        assertThrows(IllegalArgumentException.class, () -> support.buildConfig(0, 0, null));
    }

    @Test
    @DisplayName("processDocument 主干：PROCESSING → 分块落库 → 向量化 → INDEXED + checksum + 发布事件")
    void processDocumentHappyPath() {
        Document document = uploadedDocument();
        ExtractionResult result =
                new ExtractionResult(List.of("第一页", "第二页"), 2, false, "sum-1", "pdf");
        when(domainService.chunkDocument(eq(document), any(), any()))
                .thenReturn(List.of(chunk("块一"), chunk("块二")));
        IndexProgressCallback callback = mock(IndexProgressCallback.class);

        support.processDocument(document, result, callback);

        assertEquals(DocumentStatusEnum.INDEXED, document.getStatus());
        assertEquals("sum-1", document.getFileChecksum());
        verify(chunkGateway).saveBatch(any());
        verify(vectorStoreService).storeChunks(any(), eq(callback));
        ArgumentCaptor<Object> eventCap = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCap.capture());
        DocumentProcessedEvent event =
                assertInstanceOf(DocumentProcessedEvent.class, eventCap.getValue());
        assertEquals("dk-7", event.documentKey());
        verify(callback).onComplete();
        verify(documentGateway, org.mockito.Mockito.atLeast(2)).update(document);
    }

    @Test
    @DisplayName("processDocument：分块结果为空 → FAILED「No content」并回调 onError，不落向量库")
    void processDocumentEmptyChunksFails() {
        Document document = uploadedDocument();
        when(domainService.chunkDocument(any(), any(), any())).thenReturn(List.of());
        IndexProgressCallback callback = mock(IndexProgressCallback.class);

        support.processDocument(
                document, new ExtractionResult(List.of(), 0, false, null, "pdf"), callback);

        assertEquals(DocumentStatusEnum.FAILED, document.getStatus());
        assertTrue(document.getErrorMessage().contains("No content"));
        verify(chunkGateway, never()).saveBatch(any());
        verify(vectorStoreService, never()).storeChunks(any(), any());
        verify(callback).onError(anyString());
    }

    @Test
    @DisplayName("processDocument：流水线抛异常 → FAILED 记录原因，异常不外溢（异步链路自吞）")
    void processDocumentExceptionMarksFailed() {
        Document document = uploadedDocument();
        when(domainService.chunkDocument(any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        support.processDocument(
                document, new ExtractionResult(List.of("x"), 1, false, null, "pdf"), null);

        assertEquals(DocumentStatusEnum.FAILED, document.getStatus());
        assertEquals("boom", document.getErrorMessage());
    }

    @Test
    @DisplayName("buildDocument：visibility / chunkingConfig 缺省回填；文件元信息取自 MultipartFile")
    void buildDocumentDefaults() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("词表.docx");
        when(file.getContentType())
                .thenReturn(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        when(file.getSize()).thenReturn(2048L);

        Document document =
                support.buildDocument(
                        "dk-9",
                        file,
                        "/store/uuid.docx",
                        "t-1",
                        "d-1",
                        null,
                        null,
                        null,
                        "语文学科",
                        null);

        assertEquals("dk-9", document.getDocumentKey());
        assertEquals("词表.docx", document.getFileName());
        assertEquals(2048L, document.getFileSize());
        assertEquals(DocumentStatusEnum.UPLOADED, document.getStatus());
        assertEquals(DocumentVisibilityEnum.INTERNAL, document.getVisibility());
        assertEquals(
                ChunkingConfig.defaultConfig().getStrategy(),
                document.getChunkingConfig().getStrategy());
    }

    @Test
    @DisplayName("saveToTemp / copyToStorage：扩展名保留落盘，deleteTempFile 连目录一并清理")
    void tempAndStorageFileLifecycle() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("试卷.PDF");
        byte[] payload = "pdf-bytes".getBytes(StandardCharsets.UTF_8);
        when(file.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(payload));

        Path temp = support.saveToTemp(file);
        assertTrue(temp.getFileName().toString().equals("upload.PDF"), "临时名保留原扩展名");
        assertEquals("pdf-bytes", new String(Files.readAllBytes(temp), StandardCharsets.UTF_8));

        Path stored = support.copyToStorage(temp, "原文件.pdf");
        assertTrue(stored.startsWith(storageDir));
        assertTrue(stored.getFileName().toString().endsWith(".pdf"));

        support.deleteTempFile(temp);
        assertFalse(Files.exists(temp));
        assertFalse(Files.exists(temp.getParent()), "owner-only 临时目录应随文件清理");
    }

    @Test
    @DisplayName("detectFileType：常见扩展名映射 MIME；未知与无扩展名回退 octet-stream")
    void detectFileTypeMapping() {
        assertEquals("application/pdf", support.detectFileType(Path.of("a.pdf")));
        assertEquals("text/plain", support.detectFileType(Path.of("notes.txt")));
        assertEquals("text/html", support.detectFileType(Path.of("page.htm")));
        assertEquals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                support.detectFileType(Path.of("w.docx")));
        assertEquals("application/octet-stream", support.detectFileType(Path.of("binary.xyz")));
        assertEquals("application/octet-stream", support.detectFileType(Path.of("noext")));
    }

    @Test
    @DisplayName("sanitizeForJson：剔除控制字符但保留 \\n\\r\\t；null / 空串原样返回")
    void sanitizeKeepsWhitespaceControl() {
        assertNull(support.sanitizeForJson(null));
        assertEquals("", support.sanitizeForJson(""));
        assertEquals(
                "a\nb\tc\r d",
                support.sanitizeForJson("a\u0000\nb\tc\r d"),
                "换行/制表/回车与空格保留，\\u0000 剔除");
        assertEquals("ab", support.sanitizeForJson("a\u0000b"));
    }

    @Test
    @DisplayName("buildPages / buildChunkDetails：页号 1 起始、预览截断 500 / 300 字符")
    void pageAndChunkDetailAssembly() {
        String longPage = "字".repeat(600);
        List<PageDetail> pages = support.buildPages(List.of(longPage, "短页"));
        assertEquals(1, pages.get(0).pageNumber());
        assertEquals(600, pages.get(0).charCount());
        assertEquals(500, pages.get(0).preview().length(), "预览截断 500");
        assertEquals(2, pages.get(1).pageNumber());

        DocumentChunk c = chunk("内".repeat(400));
        List<ChunkDetail> details = support.buildChunkDetails(List.of(c));
        assertEquals(300, details.get(0).preview().length(), "分块预览截断 300");
        assertEquals(400, details.get(0).charCount());
    }

    @Test
    @DisplayName("buildCustomChunks：空白内容跳过、序号连续、文档元数据下沉")
    void buildCustomChunksSkipsBlank() {
        Document document = uploadedDocument();
        document.setVisibility(DocumentVisibilityEnum.PUBLIC);
        CustomChunkInput blank = new CustomChunkInput(0, 1, 1, "   ");
        CustomChunkInput ok1 = new CustomChunkInput(0, 1, 1, "第一块自定义内容");
        CustomChunkInput ok2 = new CustomChunkInput(1, 2, 2, "第二块自定义内容");

        List<DocumentChunk> chunks = support.buildCustomChunks(document, List.of(blank, ok1, ok2));

        assertEquals(2, chunks.size());
        assertEquals(0, chunks.get(0).getChunkIndex(), "空白块不占序号");
        assertEquals(1, chunks.get(1).getChunkIndex());
        assertEquals("PUBLIC", chunks.get(0).getVisibility());
        assertEquals("dk-7", chunks.get(0).getDocumentKey());
        assertEquals(7L, chunks.get(0).getDocumentId());
    }

    // 其他分支待补：newDocumentKey 随机性、createOwnerOnlyTempDir 非 POSIX 回落分支（依赖文件系统能力，不适合纯单测穷举）。
}
