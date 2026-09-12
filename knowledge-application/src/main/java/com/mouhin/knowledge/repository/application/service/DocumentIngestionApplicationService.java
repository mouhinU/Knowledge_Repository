package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.domain.event.DocumentCreatedEvent;
import com.mouhin.knowledge.repository.domain.event.DocumentProcessedEvent;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingConfig;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingStrategyEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import com.mouhin.knowledge.repository.domain.repository.DocumentChunkRepository;
import com.mouhin.knowledge.repository.domain.repository.DocumentRepository;
import com.mouhin.knowledge.repository.domain.service.DocumentIngestionDomainService;
import com.mouhin.knowledge.repository.infrastructure.milvus.MilvusVectorStoreService;
import com.mouhin.knowledge.repository.infrastructure.pdf.DocumentExtractionService;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文档摄入应用服务
 * <p>
 * 编排文档上传 → 文本提取 → 分块 → 向量化 → 存储的完整流程。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@Service
public class DocumentIngestionApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentIngestionApplicationService.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentExtractionService documentExtractionService;
    private final MilvusVectorStoreService vectorStoreService;
    private final DocumentIngestionDomainService ingestionDomainService;
    private final ApplicationEventPublisher eventPublisher;
    private final Path storagePath;

    /** 缓存已提取的文本结果，供预览和入库复用（documentKey → ExtractionResult） */
    private final Map<String, DocumentExtractionService.ExtractionResult> extractionCache =
            new ConcurrentHashMap<>();

    public DocumentIngestionApplicationService(
            DocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            DocumentExtractionService documentExtractionService,
            MilvusVectorStoreService vectorStoreService,
            DocumentIngestionDomainService ingestionDomainService,
            ApplicationEventPublisher eventPublisher,
            @Value("${knowledge.storage.path:./data/documents}") String storageDir) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.documentExtractionService = documentExtractionService;
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
     * 上传并处理文档
     *
     * @param file           文档文件
     * @param ownerId        所有者用户 ID
     * @param departmentId   所属部门 ID
     * @param visibility     可见性
     * @param allowedRoles   允许访问的角色（逗号分隔）
     * @param tags           标签（逗号分隔）
     * @param chunkingConfig 分块配置（null 使用默认）
     * @return 文档聚合根
     */
    @Transactional
    public Document uploadAndProcess(MultipartFile file,
                                     String ownerId,
                                     String departmentId,
                                     DocumentVisibilityEnum visibility,
                                     String allowedRoles,
                                     String tags,
                                     ChunkingConfig chunkingConfig) {
        // 1. 基本校验
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }

        // 2. 保存为临时文件
        Path tempFile;
        try {
            tempFile = saveToTemp(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save uploaded file", e);
        }

        try {
            // 3. 提取文本并检查去重
            DocumentExtractionService.ExtractionResult result;
            try {
                result = documentExtractionService.extractText(
                        tempFile, file.getSize(), file.getOriginalFilename());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to extract text from PDF", e);
            }

            documentRepository.findByFileChecksum(result.checksum()).ifPresent(existing -> {
                throw new IllegalArgumentException(
                        "Duplicate file detected. Existing document: " + existing.getDocumentKey());
            });

            // 4. 持久化存储文件
            Path permanentFile;
            try {
                permanentFile = copyToStorage(tempFile, file.getOriginalFilename());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to store uploaded file", e);
            }

            // 5. 创建文档记录
            String documentKey = UUID.randomUUID().toString();
            Document document = buildDocument(documentKey, file, permanentFile.toString(),
                    ownerId, departmentId, visibility, allowedRoles, tags, chunkingConfig);
            document.validateForCreate();
            documentRepository.save(document);

            eventPublisher.publishEvent(new DocumentCreatedEvent(
                    documentKey, file.getOriginalFilename(), ownerId, departmentId, LocalDateTime.now()));

            // 5. 处理文档
            processDocument(document, result);

            return document;

        } finally {
            deleteTempFile(tempFile);
        }
    }

    /**
     * 仅上传并提取文本（不分块、不向量化）
     * <p>
     * 上传完成后文档状态为 UPLOADED，用户可通过预览接口查看提取效果，
     * 确认后再调用 indexDocument 完成向量化入库。
     * </p>
     *
     * @param file         文档文件
     * @param ownerId      所有者用户 ID
     * @param departmentId 所属部门 ID
     * @param visibility   可见性
     * @param allowedRoles 允许访问的角色（逗号分隔）
     * @param tags         标签（逗号分隔）
     * @return 文档聚合根（状态为 UPLOADED）
     */
    @Transactional
    public Document uploadOnly(MultipartFile file,
                               String ownerId,
                               String departmentId,
                               DocumentVisibilityEnum visibility,
                               String allowedRoles,
                               String tags) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }

        Path tempFile;
        try {
            tempFile = saveToTemp(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save uploaded file", e);
        }

        try {
            // 提取文本
            DocumentExtractionService.ExtractionResult result;
            try {
                result = documentExtractionService.extractText(
                        tempFile, file.getSize(), file.getOriginalFilename());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to extract text", e);
            }

            // 去重检查
            documentRepository.findByFileChecksum(result.checksum()).ifPresent(existing -> {
                throw new IllegalArgumentException(
                        "Duplicate file detected. Existing document: " + existing.getDocumentKey());
            });

            // 持久化存储
            Path permanentFile;
            try {
                permanentFile = copyToStorage(tempFile, file.getOriginalFilename());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to store uploaded file", e);
            }

            // 创建文档记录（默认分块配置，后续 indexDocument 时可用用户选择的配置覆盖）
            String documentKey = UUID.randomUUID().toString();
            Document document = buildDocument(documentKey, file, permanentFile.toString(),
                    ownerId, departmentId, visibility, allowedRoles, tags, ChunkingConfig.defaultConfig());
            document.validateForCreate();
            documentRepository.save(document);

            // 保存提取到的页数和元数据
            document.setTotalPages(result.totalPages());
            documentRepository.update(document);

            eventPublisher.publishEvent(new DocumentCreatedEvent(
                    documentKey, file.getOriginalFilename(), ownerId, departmentId, LocalDateTime.now()));

            // 缓存提取结果
            extractionCache.put(documentKey, result);

            logger.info("Document uploaded (pending index): {} -> {}", documentKey, document.getFileName());
            return document;

        } finally {
            deleteTempFile(tempFile);
        }
    }

    /**
     * 基于已上传文档进行解析预览（不入库，使用缓存的提取结果）
     *
     * @param documentKey 文档唯一标识
     * @param chunkSize   分块大小
     * @param overlap     分块重叠
     * @param strategy    切分策略
     * @return 解析预览结果
     */
    public PreviewResult previewFromDocument(String documentKey, int chunkSize, int overlap,
                                            ChunkingStrategyEnum strategy) {
        Document document = documentRepository.findByDocumentKey(documentKey)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentKey));

        DocumentExtractionService.ExtractionResult extraction = getOrReextract(document);

        ChunkingConfig config = new ChunkingConfig(chunkSize, overlap,
                strategy != null ? strategy : ChunkingStrategyEnum.FIXED_SIZE, true, true);

        Document tempDoc = new Document();
        tempDoc.setDocumentKey(documentKey);
        tempDoc.setOwnerId(document.getOwnerId());
        tempDoc.setDepartmentId(document.getDepartmentId());
        tempDoc.setVisibility(document.getVisibility());
        tempDoc.setChunkingConfig(config);

        List<DocumentChunk> chunks = ingestionDomainService.chunkDocument(
                tempDoc, extraction.pageTexts(), config);

        List<PageDetail> pages = new ArrayList<>();
        for (int i = 0; i < extraction.pageTexts().size(); i++) {
            String text = extraction.pageTexts().get(i);
            String sanitized = sanitizeForJson(text);
            pages.add(new PageDetail(i + 1, sanitized != null ? sanitized.length() : 0,
                    sanitized != null ? sanitized.substring(0, Math.min(sanitized.length(), 500)) : ""));
        }

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

        return new PreviewResult(
                document.getFileName(),
                document.getFileType(),
                document.getTotalPages() != null && document.getTotalPages() > 0
                        ? document.getTotalPages() : extraction.totalPages(),
                extraction.pageTexts().size(),
                extraction.likelyScanned(),
                extraction.checksum(),
                chunks.size(),
                pages,
                chunkDetails
        );
    }

    /**
     * 确认入库：对已上传文档执行分块 → 向量化 → 存储
     *
     * @param documentKey 文档唯一标识
     * @param chunkSize   分块大小
     * @param overlap     分块重叠
     * @param strategy    切分策略
     * @return 文档聚合根
     */
    @Transactional
    public Document indexDocument(String documentKey, int chunkSize, int overlap,
                                  ChunkingStrategyEnum strategy) {
        Document document = documentRepository.findByDocumentKey(documentKey)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentKey));

        if (document.getStatus() == DocumentStatusEnum.INDEXED) {
            throw new IllegalStateException("Document is already indexed. Use reindex to re-process.");
        }

        // 更新分块配置
        ChunkingConfig config = new ChunkingConfig(chunkSize, overlap,
                strategy != null ? strategy : ChunkingStrategyEnum.FIXED_SIZE, true, true);
        document.setChunkingConfig(config);

        DocumentExtractionService.ExtractionResult extraction = getOrReextract(document);
        processDocument(document, extraction);

        // 清理缓存
        extractionCache.remove(documentKey);

        return document;
    }

    /**
     * 获取缓存的提取结果，若缓存未命中则从存储文件重新提取
     */
    private DocumentExtractionService.ExtractionResult getOrReextract(Document document) {
        DocumentExtractionService.ExtractionResult cached = extractionCache.get(document.getDocumentKey());
        if (cached != null) {
            return cached;
        }

        // 缓存未命中，从存储文件重新提取
        Path storedPath = Path.of(document.getStoragePath());
        if (!Files.exists(storedPath)) {
            throw new IllegalStateException("Stored file not found at: " + document.getStoragePath());
        }

        try {
            logger.info("Re-extracting text for document {}: {}", document.getDocumentKey(), document.getFileName());
            DocumentExtractionService.ExtractionResult result =
                    documentExtractionService.extractText(storedPath, Files.size(storedPath), document.getFileName());
            extractionCache.put(document.getDocumentKey(), result);
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to re-extract text: " + e.getMessage(), e);
        }
    }

    /**
     * 重新入库已有文档（清理旧向量/分块，重新提取、分块、向量化）
     *
     * @param documentKey 文档唯一标识
     * @return 文档聚合根
     */
    @Transactional
    public Document reindex(String documentKey) {
        Document document = documentRepository.findByDocumentKey(documentKey)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentKey));

        Path storagePath = Path.of(document.getStoragePath());
        if (!Files.exists(storagePath)) {
            throw new IllegalStateException(
                    "Stored file not found at: " + document.getStoragePath());
        }

        // 清理旧数据
        vectorStoreService.deleteByDocumentKey(documentKey);
        chunkRepository.deleteByDocumentId(document.getId());

        // 重置状态以允许重新处理
        document.setStatus(DocumentStatusEnum.UPLOADED);
        document.setErrorMessage(null);
        documentRepository.update(document);

        logger.info("Reindexing document {}: {}", documentKey, document.getFileName());

        try {
            DocumentExtractionService.ExtractionResult result =
                    documentExtractionService.extractText(
                            storagePath, Files.size(storagePath), document.getFileName());

            processDocument(document, result);
            return document;

        } catch (IOException e) {
            throw new IllegalStateException("Failed to re-extract text: " + e.getMessage(), e);
        }
    }

    /**
     * 文档解析预览（不入库，仅返回提取与分块结果）
     *
     * @param file      文档文件
     * @param chunkSize 分块大小
     * @param overlap   分块重叠
     * @param strategy  切分策略
     * @return 解析预览结果
     */
    public PreviewResult preview(MultipartFile file, int chunkSize, int overlap,
                                 ChunkingStrategyEnum strategy) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }

        Path tempFile;
        try {
            tempFile = saveToTemp(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save uploaded file", e);
        }

        try {
            DocumentExtractionService.ExtractionResult extraction =
                    documentExtractionService.extractText(tempFile, file.getSize(), file.getOriginalFilename());

            ChunkingConfig config = new ChunkingConfig(chunkSize, overlap,
                    strategy != null ? strategy : ChunkingStrategyEnum.FIXED_SIZE, true, true);

            // 构造临时 Document 用于分块（不持久化）
            Document tempDoc = new Document();
            tempDoc.setDocumentKey("preview");
            tempDoc.setOwnerId("preview");
            tempDoc.setDepartmentId("preview");
            tempDoc.setVisibility(DocumentVisibilityEnum.INTERNAL);
            tempDoc.setChunkingConfig(config);

            List<DocumentChunk> chunks = ingestionDomainService.chunkDocument(
                    tempDoc, extraction.pageTexts(), config);

            List<PageDetail> pages = new ArrayList<>();
            for (int i = 0; i < extraction.pageTexts().size(); i++) {
                String text = extraction.pageTexts().get(i);
                String sanitized = sanitizeForJson(text);
                pages.add(new PageDetail(i + 1, sanitized != null ? sanitized.length() : 0,
                        sanitized != null ? sanitized.substring(0, Math.min(sanitized.length(), 500)) : ""));
            }

            List<ChunkDetail> chunkDetails = new ArrayList<>();
            for (DocumentChunk chunk : chunks) {
                String preview = chunk.getContent() != null
                        ? chunk.getContent().substring(0, Math.min(chunk.getContent().length(), 300))
                        : "";
                chunkDetails.add(new ChunkDetail(
                        chunk.getChunkIndex(),
                        chunk.getStartPage(),
                        chunk.getEndPage(),
                        chunk.getTokenCount(),
                        chunk.getContent() != null ? chunk.getContent().length() : 0,
                        preview,
                        chunk.getContent()
                ));
            }

            return new PreviewResult(
                    file.getOriginalFilename(),
                    extraction.detectedFormat(),
                    extraction.totalPages(),
                    extraction.pageTexts().size(),
                    extraction.likelyScanned(),
                    extraction.checksum(),
                    chunks.size(),
                    pages,
                    chunkDetails
            );

        } catch (IOException e) {
            throw new IllegalStateException("Failed to extract text: " + e.getMessage(), e);
        } finally {
            deleteTempFile(tempFile);
        }
    }

    /**
     * 解析预览结果
     */
    public record PreviewResult(
            String fileName,
            String format,
            int totalPages,
            int extractedSections,
            boolean likelyScanned,
            String checksum,
            int totalChunks,
            List<PageDetail> pages,
            List<ChunkDetail> chunks
    ) {}

    public record PageDetail(int pageNumber, int charCount, String preview) {}

    public record ChunkDetail(int chunkIndex, int startPage, int endPage, int tokenCount, int charCount, String preview, String content) {}

    /**
     * 自定义分块输入（前端提交）
     */
    public record CustomChunkInput(int chunkIndex, int startPage, int endPage, String content) {}

    /**
     * 使用用户自定义分块入库（支持手动调整分块顺序和内容）
     *
     * @param documentKey  文档唯一标识
     * @param customChunks 用户调整后的分块列表
     * @return 文档聚合根
     */
    @Transactional
    public Document indexWithCustomChunks(String documentKey, List<CustomChunkInput> customChunks) {
        if (customChunks == null || customChunks.isEmpty()) {
            throw new IllegalArgumentException("Custom chunks must not be empty");
        }

        Document document = documentRepository.findByDocumentKey(documentKey)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentKey));

        if (document.getStatus() == DocumentStatusEnum.INDEXED) {
            throw new IllegalStateException("Document is already indexed. Use reindex to re-process.");
        }

        try {
            document.markProcessing();
            documentRepository.update(document);

            // 将自定义分块转为 DocumentChunk 实体
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

                // 附加权限元数据
                chunk.setDepartmentId(document.getDepartmentId());
                chunk.setVisibility(document.getVisibility() != null
                        ? document.getVisibility().name()
                        : DocumentVisibilityEnum.INTERNAL.name());
                chunk.setAllowedRoles(document.getAllowedRoles());
                chunk.setOwnerId(document.getOwnerId());

                // 附加文档展示信息
                chunk.setDocumentName(document.getFileName());
                chunk.setFileType(document.getFileType());
                chunk.setTags(document.getTags());

                chunks.add(chunk);
            }

            if (chunks.isEmpty()) {
                document.markFailed("No valid chunks provided");
                documentRepository.update(document);
                return document;
            }

            // 保存分块到关系数据库
            chunkRepository.saveBatch(chunks);

            // 向量化并存入 Milvus
            vectorStoreService.storeChunks(chunks);

            // 标记完成
            DocumentExtractionService.ExtractionResult extraction = getOrReextract(document);
            document.markIndexed(extraction.totalPages());
            document.setFileChecksum(extraction.checksum());
            documentRepository.update(document);

            eventPublisher.publishEvent(new DocumentProcessedEvent(
                    document.getDocumentKey(), document.getFileName(),
                    extraction.totalPages(), chunks.size(),
                    document.getOwnerId(), document.getDepartmentId(), LocalDateTime.now()));

            logger.info("Document {} indexed with custom chunks: {} chunks",
                    document.getDocumentKey(), chunks.size());

            // 清理缓存
            extractionCache.remove(documentKey);

            return document;

        } catch (Exception e) {
            logger.error("Failed to index document {} with custom chunks: {}",
                    documentKey, e.getMessage(), e);
            document.markFailed(e.getMessage());
            documentRepository.update(document);
            return document;
        }
    }

    /**
     * 处理文档：分块 → 向量化 → 存储
     */
    private void processDocument(Document document, DocumentExtractionService.ExtractionResult result) {
        try {
            document.markProcessing();
            documentRepository.update(document);

            if (result.likelyScanned()) {
                logger.warn("Document {} appears to be a scanned PDF. Text extraction may be incomplete.",
                        document.getDocumentKey());
            }

            // 分块
            List<DocumentChunk> chunks = ingestionDomainService.chunkDocument(
                    document, result.pageTexts(), document.getChunkingConfig());

            if (chunks.isEmpty()) {
                document.markFailed("No content extracted from PDF");
                documentRepository.update(document);
                return;
            }

            // 保存分块到关系数据库
            chunkRepository.saveBatch(chunks);

            // 向量化并存入 Milvus
            vectorStoreService.storeChunks(chunks);

            // 标记完成
            document.markIndexed(result.totalPages());
            document.setFileChecksum(result.checksum());
            documentRepository.update(document);

            eventPublisher.publishEvent(new DocumentProcessedEvent(
                    document.getDocumentKey(), document.getFileName(),
                    result.totalPages(), chunks.size(),
                    document.getOwnerId(), document.getDepartmentId(), LocalDateTime.now()));

            logger.info("Document {} processed successfully: {} pages, {} chunks",
                    document.getDocumentKey(), result.totalPages(), chunks.size());

        } catch (Exception e) {
            logger.error("Failed to process document {}: {}", document.getDocumentKey(), e.getMessage(), e);
            document.markFailed(e.getMessage());
            documentRepository.update(document);
        }
    }

    private Document buildDocument(String documentKey, MultipartFile file, String storagePath,
                                   String ownerId, String departmentId,
                                   DocumentVisibilityEnum visibility,
                                   String allowedRoles, String tags,
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
        document.setChunkingConfig(chunkingConfig != null ? chunkingConfig : ChunkingConfig.defaultConfig());
        return document;
    }

    private Path saveToTemp(MultipartFile file) throws IOException {
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

    private Path copyToStorage(Path sourceFile, String originalName) throws IOException {
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }
        String storedName = UUID.randomUUID() + ext;
        Path target = storagePath.resolve(storedName);
        Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private void deleteTempFile(Path tempFile) {
        try {
            Files.deleteIfExists(tempFile);
            if (tempFile.getParent() != null) {
                Files.deleteIfExists(tempFile.getParent());
            }
        } catch (IOException e) {
            logger.warn("Failed to clean up temp file: {}", tempFile, e);
        }
    }

    /**
     * 清理文本中的控制字符，避免 JSON 序列化失败
     * <p>
     * 保留换行符(\n)、回车符(\r)、制表符(\t)，移除其他 ASCII 控制字符。
     * </p>
     */
    private String sanitizeForJson(String text) {
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
}
