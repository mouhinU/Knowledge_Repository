package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.application.service.DocumentIngestionApplicationService;
import com.mouhin.knowledge.repository.application.service.DocumentIngestionApplicationService.PreviewResult;
import com.mouhin.knowledge.repository.application.service.DocumentManagementApplicationService;
import com.mouhin.knowledge.repository.application.service.DocumentManagementApplicationService.KnowledgeStats;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingStrategyEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.web.dto.ChunkingRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 文档管理控制器
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@RestController
@RequestMapping("/api/admin/document")
public class DocumentAdminController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentAdminController.class);

    private final DocumentManagementApplicationService managementService;
    private final DocumentIngestionApplicationService ingestionService;
    private final IndexProgressStore indexProgressStore;

    public DocumentAdminController(DocumentManagementApplicationService managementService,
                                   DocumentIngestionApplicationService ingestionService,
                                   IndexProgressStore indexProgressStore) {
        this.managementService = managementService;
        this.ingestionService = ingestionService;
        this.indexProgressStore = indexProgressStore;
    }

    /**
     * 获取文档详情
     */
    @GetMapping("/{documentKey}")
    public ResponseEntity<Map<String, Object>> getDocument(@PathVariable String documentKey) {
        Document doc = managementService.getByKey(documentKey);
        return ResponseEntity.ok(buildDocumentResponse(doc));
    }

    /**
     * 按所有者查询文档列表
     */
    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<List<Map<String, Object>>> listByOwner(
            @PathVariable String ownerId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Document> docs = managementService.listByOwner(ownerId, page, size);
        return ResponseEntity.ok(docs.stream().map(this::buildDocumentResponse).toList());
    }

    /**
     * 按部门查询文档列表
     */
    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<Map<String, Object>>> listByDepartment(
            @PathVariable String departmentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Document> docs = managementService.listByDepartment(departmentId, page, size);
        return ResponseEntity.ok(docs.stream().map(this::buildDocumentResponse).toList());
    }

    /**
     * 查询全部文档列表
     */
    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> listAll() {
        List<Document> docs = managementService.listAll();
        return ResponseEntity.ok(docs.stream().map(this::buildDocumentResponse).toList());
    }

    /**
     * 按状态查询文档列表
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<Map<String, Object>>> listByStatus(@PathVariable String status) {
        DocumentStatusEnum statusEnum;
        try {
            statusEnum = DocumentStatusEnum.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        List<Document> docs = managementService.listByStatus(statusEnum);
        return ResponseEntity.ok(docs.stream().map(this::buildDocumentResponse).toList());
    }

    /**
     * 获取知识库统计
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        KnowledgeStats stats = managementService.getStats();
        return ResponseEntity.ok(Map.of(
                "totalDocuments", stats.totalDocuments(),
                "indexedDocuments", stats.indexedDocuments(),
                "processingDocuments", stats.processingDocuments(),
                "failedDocuments", stats.failedDocuments()
        ));
    }

    /**
     * 获取各分类文档数量统计
     */
    @GetMapping("/category-stats")
    public ResponseEntity<Map<String, Long>> getCategoryStats() {
        return ResponseEntity.ok(managementService.getCategoryStats());
    }

    /**
     * 归档文档
     */
    @PutMapping("/{documentKey}/archive")
    public ResponseEntity<Map<String, String>> archive(@PathVariable String documentKey) {
        managementService.archive(documentKey);
        return ResponseEntity.ok(Map.of("message", "Document archived: " + documentKey));
    }

    /**
     * 重新入库文档（清理旧向量/分块，重新提取、分块、向量化）
     */
    @PostMapping("/{documentKey}/reindex")
    public ResponseEntity<Map<String, Object>> reindex(@PathVariable String documentKey) {
        Document document = ingestionService.reindex(documentKey);
        return ResponseEntity.ok(Map.of(
                "documentKey", document.getDocumentKey(),
                "fileName", document.getFileName(),
                "status", document.getStatus().name(),
                "message", "Document reindexed: " + documentKey
        ));
    }

    /**
     * 解析预览（基于已上传文档，不入库）
     */
    @GetMapping("/{documentKey}/preview")
    public ResponseEntity<PreviewResult> preview(
            @PathVariable String documentKey,
            ChunkingRequest chunkingRequest) {

        ChunkingStrategyEnum strategyEnum = resolveStrategy(chunkingRequest.getStrategy());

        logger.info("Preview document {}: chunkSize={}, strategy={}", documentKey,
                chunkingRequest.getChunkSize(), strategyEnum);
        PreviewResult result = ingestionService.previewFromDocument(
                documentKey, chunkingRequest.getChunkSize(), chunkingRequest.getOverlap(), strategyEnum);
        return ResponseEntity.ok(result);
    }

    /**
     * 确认入库（异步，通过 SSE 推送进度）
     */
    @PostMapping("/{documentKey}/index")
    public ResponseEntity<Map<String, Object>> indexDocument(
            @PathVariable String documentKey,
            ChunkingRequest chunkingRequest) {

        ChunkingStrategyEnum strategyEnum = resolveStrategy(chunkingRequest.getStrategy());

        logger.info("Async indexing document {}: chunkSize={}, strategy={}", documentKey,
                chunkingRequest.getChunkSize(), strategyEnum);

        var callback = indexProgressStore.createCallback(documentKey);
        ingestionService.indexDocumentAsync(
                documentKey, chunkingRequest.getChunkSize(), chunkingRequest.getOverlap(),
                strategyEnum, callback);

        return ResponseEntity.ok(Map.of(
                "documentKey", documentKey,
                "status", "STARTED",
                "message", "Indexing started. Connect to SSE for progress."
        ));
    }

    /**
     * 使用自定义分块入库（异步，通过 SSE 推送进度）
     */
    @PostMapping("/{documentKey}/index-custom")
    public ResponseEntity<Map<String, Object>> indexWithCustomChunks(
            @PathVariable String documentKey,
            @RequestBody List<DocumentIngestionApplicationService.CustomChunkInput> customChunks) {

        logger.info("Async indexing document {} with {} custom chunks", documentKey,
                customChunks != null ? customChunks.size() : 0);

        var callback = indexProgressStore.createCallback(documentKey);
        ingestionService.indexWithCustomChunksAsync(documentKey, customChunks, callback);

        return ResponseEntity.ok(Map.of(
                "documentKey", documentKey,
                "status", "STARTED",
                "message", "Indexing started. Connect to SSE for progress."
        ));
    }

    /**
     * 入库进度 SSE 端点
     */
    @GetMapping("/{documentKey}/index/progress")
    public SseEmitter indexProgress(@PathVariable String documentKey) {
        return indexProgressStore.createEmitter(documentKey);
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/{documentKey}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable String documentKey) {
        managementService.delete(documentKey);
        return ResponseEntity.ok(Map.of("message", "Document deleted: " + documentKey));
    }

    /**
     * 解析分块策略枚举，无效值回退为 FIXED_SIZE
     */
    private ChunkingStrategyEnum resolveStrategy(String strategy) {
        if (strategy == null || strategy.isBlank()) {
            return ChunkingStrategyEnum.FIXED_SIZE;
        }
        try {
            return ChunkingStrategyEnum.valueOf(strategy.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ChunkingStrategyEnum.FIXED_SIZE;
        }
    }

    private Map<String, Object> buildDocumentResponse(Document doc) {
        return Map.of(
                "documentKey", doc.getDocumentKey(),
                "fileName", doc.getFileName(),
                "fileSize", doc.getFileSize() != null ? doc.getFileSize() : 0,
                "totalPages", doc.getTotalPages() != null ? doc.getTotalPages() : 0,
                "status", doc.getStatus().name(),
                "visibility", doc.getVisibility() != null ? doc.getVisibility().name() : "",
                "ownerId", doc.getOwnerId(),
                "departmentId", doc.getDepartmentId(),
                "tags", doc.getTags() != null ? doc.getTags() : "",
                "createdTime", doc.getCreatedTime() != null ? doc.getCreatedTime().toString() : ""
        );
    }
}
