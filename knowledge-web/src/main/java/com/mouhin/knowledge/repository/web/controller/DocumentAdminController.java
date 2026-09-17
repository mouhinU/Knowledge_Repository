package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.application.executor.docingestion.IndexAsyncCmdExe;
import com.mouhin.knowledge.repository.application.executor.docingestion.IndexCustomChunksAsyncCmdExe;
import com.mouhin.knowledge.repository.client.api.DocumentIngestionServiceI;
import com.mouhin.knowledge.repository.client.api.DocumentServiceI;
import com.mouhin.knowledge.repository.client.dto.ChunkingRequest;
import com.mouhin.knowledge.repository.client.dto.CustomChunkInput;
import com.mouhin.knowledge.repository.client.dto.DocumentVO;
import com.mouhin.knowledge.repository.client.dto.KnowledgeStatsVO;
import com.mouhin.knowledge.repository.client.dto.PreviewResult;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
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

    private final DocumentServiceI documentService;
    private final DocumentIngestionServiceI ingestionService;
    private final IndexAsyncCmdExe indexAsyncCmdExe;
    private final IndexCustomChunksAsyncCmdExe indexCustomChunksAsyncCmdExe;
    private final IndexProgressStore indexProgressStore;

    public DocumentAdminController(DocumentServiceI documentService,
                                   DocumentIngestionServiceI ingestionService,
                                   IndexAsyncCmdExe indexAsyncCmdExe,
                                   IndexCustomChunksAsyncCmdExe indexCustomChunksAsyncCmdExe,
                                   IndexProgressStore indexProgressStore) {
        this.documentService = documentService;
        this.ingestionService = ingestionService;
        this.indexAsyncCmdExe = indexAsyncCmdExe;
        this.indexCustomChunksAsyncCmdExe = indexCustomChunksAsyncCmdExe;
        this.indexProgressStore = indexProgressStore;
    }

    /**
     * 获取文档详情
     */
    @GetMapping("/{documentKey}")
    public ResponseEntity<DocumentVO> getDocument(@PathVariable String documentKey) {
        return ResponseEntity.ok(documentService.getDocument(documentKey));
    }

    /**
     * 按所有者查询文档列表
     */
    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<List<DocumentVO>> listByOwner(
            @PathVariable String ownerId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(documentService.listByOwner(ownerId, page, size));
    }

    /**
     * 按部门查询文档列表
     */
    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<DocumentVO>> listByDepartment(
            @PathVariable String departmentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(documentService.listByDepartment(departmentId, page, size));
    }

    /**
     * 查询全部文档列表
     */
    @GetMapping("/list")
    public ResponseEntity<List<DocumentVO>> listAll() {
        return ResponseEntity.ok(documentService.listDocuments());
    }

    /**
     * 按状态查询文档列表
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<DocumentVO>> listByStatus(@PathVariable String status) {
        DocumentStatusEnum statusEnum;
        try {
            statusEnum = DocumentStatusEnum.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(documentService.listByStatus(statusEnum.name()));
    }

    /**
     * 获取知识库统计
     */
    @GetMapping("/stats")
    public ResponseEntity<KnowledgeStatsVO> getStats() {
        return ResponseEntity.ok(documentService.getStats());
    }

    /**
     * 获取各分类文档数量统计
     */
    @GetMapping("/category-stats")
    public ResponseEntity<Map<String, Long>> getCategoryStats() {
        return ResponseEntity.ok(documentService.getCategoryStats());
    }

    /**
     * 归档文档
     */
    @PutMapping("/{documentKey}/archive")
    public ResponseEntity<Map<String, String>> archive(@PathVariable String documentKey) {
        documentService.archive(documentKey);
        return ResponseEntity.ok(Map.of("message", "Document archived: " + documentKey));
    }

    /**
     * 重新入库文档（清理旧向量/分块，重新提取、分块、向量化）
     */
    @PostMapping("/{documentKey}/reindex")
    public ResponseEntity<Map<String, Object>> reindex(@PathVariable String documentKey) {
        DocumentVO document = ingestionService.reindex(documentKey);
        return ResponseEntity.ok(Map.of(
                "documentKey", document.getDocumentKey(),
                "fileName", document.getFileName(),
                "status", document.getStatus(),
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

        logger.info("Preview document {}: chunkSize={}, strategy={}", documentKey,
                chunkingRequest.getChunkSize(), chunkingRequest.getStrategy());
        PreviewResult result = ingestionService.previewFromDocument(
                documentKey, chunkingRequest.getChunkSize(), chunkingRequest.getOverlap(),
                chunkingRequest.getStrategy());
        return ResponseEntity.ok(result);
    }

    /**
     * 确认入库（异步，通过 SSE 推送进度）
     */
    @PostMapping("/{documentKey}/index")
    public ResponseEntity<Map<String, Object>> indexDocument(
            @PathVariable String documentKey,
            ChunkingRequest chunkingRequest) {

        logger.info("Async indexing document {}: chunkSize={}, strategy={}", documentKey,
                chunkingRequest.getChunkSize(), chunkingRequest.getStrategy());

        var callback = indexProgressStore.createCallback(documentKey);
        indexAsyncCmdExe.execute(
                documentKey, chunkingRequest.getChunkSize(), chunkingRequest.getOverlap(),
                chunkingRequest.getStrategy(), callback);

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
            @RequestBody List<CustomChunkInput> customChunks) {

        logger.info("Async indexing document {} with {} custom chunks", documentKey,
                customChunks != null ? customChunks.size() : 0);

        var callback = indexProgressStore.createCallback(documentKey);
        indexCustomChunksAsyncCmdExe.execute(documentKey, customChunks, callback);

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
        documentService.delete(documentKey);
        return ResponseEntity.ok(Map.of("message", "Document deleted: " + documentKey));
    }
}
