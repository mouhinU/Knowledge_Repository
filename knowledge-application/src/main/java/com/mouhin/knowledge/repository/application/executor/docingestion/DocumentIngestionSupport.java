package com.mouhin.knowledge.repository.application.executor.docingestion;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文档摄入共享支撑（app 层）
 * <p>
 * 收敛原 {@code DocumentIngestionApplicationService} 中被多个用例复用的协作逻辑：
 * 临时文件 / 存储管理、文档聚合构建、分块→向量化→存储处理流水线、分块策略解析、JSON 文本清洗。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class DocumentIngestionSupport {

    private static final Logger logger = LoggerFactory.getLogger(DocumentIngestionSupport.class);

    private final DocumentGateway documentGateway;
    private final DocumentChunkGateway chunkGateway;
    private final VectorStoreGateway vectorStoreService;
    private final DocumentIngestionDomainService ingestionDomainService;
    private final ApplicationEventPublisher eventPublisher;
    private final Path storagePath;

    public DocumentIngestionSupport(
            DocumentGateway documentGateway,
            DocumentChunkGateway chunkGateway,
            VectorStoreGateway vectorStoreService,
            DocumentIngestionDomainService ingestionDomainService,
            ApplicationEventPublisher eventPublisher,
            @Value("${knowledge.storage.path:./data/documents}") String storageDir) {
        this.documentGateway = documentGateway;
        this.chunkGateway = chunkGateway;
        this.vectorStoreService = vectorStoreService;
        this.ingestionDomainService = ingestionDomainService;
        this.eventPublisher = eventPublisher;
        this.storagePath = Path.of(storageDir);
        try {
            Files.createDirectories(this.storagePath);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create storage directory: " + storageDir, e);
        }
    }

    /**
     * 解析分块策略枚举，无效值回退为 FIXED_SIZE（原适配层 resolveStrategy 逻辑下沉至 app 层）。
     */
    public ChunkingStrategyEnum resolveStrategy(String strategy) {
        if (strategy == null || strategy.isBlank()) {
            return ChunkingStrategyEnum.FIXED_SIZE;
        }
        try {
            return ChunkingStrategyEnum.valueOf(strategy.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ChunkingStrategyEnum.FIXED_SIZE;
        }
    }

    /** 依据分块参数构建 ChunkingConfig（strategy 为空回退 FIXED_SIZE）。 */
    public ChunkingConfig buildConfig(int chunkSize, int overlap, ChunkingStrategyEnum strategy) {
        return new ChunkingConfig(chunkSize, overlap,
                strategy != null ? strategy : ChunkingStrategyEnum.FIXED_SIZE, true, true);
    }

    /**
     * 处理文档：分块 → 向量化 → 存储（无进度回调）。
     */
    public void processDocument(Document document, ExtractionResult result) {
        processDocument(document, result, null);
    }

    /**
     * 处理文档：分块 → 向量化 → 存储（带进度回调）。
     */
    public void processDocument(Document document, ExtractionResult result, IndexProgressCallback callback) {
        try {
            document.markProcessing();
            documentGateway.update(document);

            if (result.likelyScanned()) {
                logger.warn("Document {} appears to be a scanned PDF. Text extraction may be incomplete.",
                        document.getDocumentKey());
            }

            List<DocumentChunk> chunks = ingestionDomainService.chunkDocument(
                    document, result.pageTexts(), document.getChunkingConfig());

            if (chunks.isEmpty()) {
                document.markFailed("No content extracted from PDF");
                documentGateway.update(document);
                if (callback != null) {
                    callback.onError("No content extracted");
                }
                return;
            }

            chunkGateway.saveBatch(chunks);
            vectorStoreService.storeChunks(chunks, callback);

            document.markIndexed(result.totalPages());
            document.setFileChecksum(result.checksum());
            documentGateway.update(document);

            eventPublisher.publishEvent(new DocumentProcessedEvent(
                    document.getDocumentKey(), document.getFileName(),
                    result.totalPages(), chunks.size(),
                    document.getOwnerId(), document.getDepartmentId(), LocalDateTime.now()));

            logger.info("Document {} processed successfully: {} pages, {} chunks",
                    document.getDocumentKey(), result.totalPages(), chunks.size());

            if (callback != null) {
                callback.onComplete();
            }

        } catch (Exception e) {
            logger.error("Failed to process document {}: {}", document.getDocumentKey(), e.getMessage(), e);
            document.markFailed(e.getMessage());
            documentGateway.update(document);
            if (callback != null) {
                callback.onError(e.getMessage());
            }
        }
    }

    /**
     * 构建上传文档聚合（默认分块配置，后续入库时可覆盖）。
     */
    public Document buildDocument(String documentKey, MultipartFile file, String storagePath,
                                  String ownerId, String departmentId,
                                  DocumentVisibilityEnum visibility,
                                  String allowedRoles, String tags, String category,
                                  ChunkingConfig chunkingConfig) {
        Document document = new Document();
        document.setDocumentKey(documentKey);
        document.setFileName(file.getOriginalFilename());
        document.setFileType(file.getContentType());
        document.setFileSize(file.getSize());
        document.setStoragePath(storagePath);
        document.setStatus(DocumentStatusEnum.UPLOADED);
        document.setVisibility(visibility != null ? visibility : DocumentVisibilityEnum.INTERNAL);
        document.setOwnerId(ownerId);
        document.setDepartmentId(departmentId);
        document.setAllowedRoles(allowedRoles);
        document.setTags(tags);
        document.setCategory(category);
        document.setChunkingConfig(chunkingConfig != null ? chunkingConfig : ChunkingConfig.defaultConfig());
        return document;
    }

    /** 生成新的文档唯一标识。 */
    public String newDocumentKey() {
        return UUID.randomUUID().toString();
    }

    public Path saveToTemp(MultipartFile file) throws IOException {
        Path tempDir = Files.createTempDirectory("knowledge-pdf-");
        String originalName = file.getOriginalFilename();
        String tempName;
        if (originalName != null && originalName.contains(".")) {
            String ext = originalName.substring(originalName.lastIndexOf('.'));
            tempName = "upload" + ext;
        } else {
            tempName = "upload.tmp";
        }
        Path tempFile = tempDir.resolve(tempName);
        try (InputStream is = file.getInputStream()) {
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }

    public Path copyToStorage(Path sourceFile, String originalName) throws IOException {
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }
        String storedName = UUID.randomUUID() + ext;
        Path target = storagePath.resolve(storedName);
        Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    public void deleteTempFile(Path tempFile) {
        try {
            Files.deleteIfExists(tempFile);
            if (tempFile.getParent() != null) {
                Files.deleteIfExists(tempFile.getParent());
            }
        } catch (IOException e) {
            logger.warn("Failed to clean up temp file: {}", tempFile, e);
        }
    }

    public String detectFileType(Path file) {
        try {
            String name = file.getFileName().toString();
            if (name.endsWith(".pdf")) return "application/pdf";
            if (name.endsWith(".docx"))
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            if (name.endsWith(".doc")) return "application/msword";
            if (name.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            if (name.endsWith(".xls")) return "application/vnd.ms-excel";
            if (name.endsWith(".pptx"))
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            if (name.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
            if (name.endsWith(".txt")) return "text/plain";
            if (name.endsWith(".csv")) return "text/csv";
            if (name.endsWith(".html") || name.endsWith(".htm")) return "text/html";
            return "application/octet-stream";
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }

    /**
     * 清理文本中的控制字符，避免 JSON 序列化失败。保留换行 / 回车 / 制表符，移除其他 ASCII 控制字符。
     */
    public String sanitizeForJson(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t' || c >= ' ') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 组装页明细（1 起始页号 / 清洗后字符数 / 前 500 字符预览）。
     */
    public List<PageDetail> buildPages(List<String> pageTexts) {
        List<PageDetail> pages = new ArrayList<>();
        for (int i = 0; i < pageTexts.size(); i++) {
            String text = pageTexts.get(i);
            String sanitized = sanitizeForJson(text);
            pages.add(new PageDetail(i + 1, sanitized != null ? sanitized.length() : 0,
                    sanitized != null ? sanitized.substring(0, Math.min(sanitized.length(), 500)) : ""));
        }
        return pages;
    }

    /**
     * 组装分块明细（前 300 字符预览 + 全文）。
     */
    public List<ChunkDetail> buildChunkDetails(List<DocumentChunk> chunks) {
        List<ChunkDetail> chunkDetails = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            String preview = chunk.getContent() != null
                    ? chunk.getContent().substring(0, Math.min(chunk.getContent().length(), 300))
                    : "";
            chunkDetails.add(new ChunkDetail(
                    chunk.getChunkIndex(), chunk.getStartPage(), chunk.getEndPage(),
                    chunk.getTokenCount(),
                    chunk.getContent() != null ? chunk.getContent().length() : 0,
                    preview,
                    chunk.getContent()
            ));
        }
        return chunkDetails;
    }

    public List<DocumentChunk> buildCustomChunks(Document document, List<CustomChunkInput> customChunks) {
        List<DocumentChunk> chunks = new ArrayList<>(customChunks.size());
        int index = 0;
        for (CustomChunkInput input : customChunks) {
            if (input.content() == null || input.content().isBlank()) {
                continue;
            }
            DocumentChunk chunk = new DocumentChunk();
            chunk.setChunkKey(UUID.randomUUID().toString());
            chunk.setDocumentId(document.getId());
            chunk.setDocumentKey(document.getDocumentKey());
            chunk.setChunkIndex(index++);
            chunk.setStartPage(input.startPage());
            chunk.setEndPage(input.endPage());
            chunk.setContent(input.content());
            chunk.setTokenCount(chunk.estimateTokenCount(input.content()));
            chunk.setDepartmentId(document.getDepartmentId());
            chunk.setVisibility(document.getVisibility() != null
                    ? document.getVisibility().name()
                    : DocumentVisibilityEnum.INTERNAL.name());
            chunk.setAllowedRoles(document.getAllowedRoles());
            chunk.setOwnerId(document.getOwnerId());
            chunk.setDocumentName(document.getFileName());
            chunk.setFileType(document.getFileType());
            chunk.setTags(document.getTags());
            chunks.add(chunk);
        }
        return chunks;
    }

    public DocumentGateway documentGateway() {
        return documentGateway;
    }

    public DocumentChunkGateway chunkGateway() {
        return chunkGateway;
    }

    public VectorStoreGateway vectorStoreService() {
        return vectorStoreService;
    }

    public DocumentIngestionDomainService ingestionDomainService() {
        return ingestionDomainService;
    }

    public ApplicationEventPublisher eventPublisher() {
        return eventPublisher;
    }
}
