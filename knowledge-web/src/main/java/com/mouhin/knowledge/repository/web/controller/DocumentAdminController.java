package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.application.executor.docingestion.ExtractEnhanceAsyncCmdExe;
import com.mouhin.knowledge.repository.application.executor.docingestion.IndexAsyncCmdExe;
import com.mouhin.knowledge.repository.application.executor.docingestion.IndexCustomChunksAsyncCmdExe;
import com.mouhin.knowledge.repository.application.executor.docingestion.ReindexAsyncCmdExe;
import com.mouhin.knowledge.repository.client.api.DocumentIngestionServiceI;
import com.mouhin.knowledge.repository.client.api.DocumentServiceI;
import com.mouhin.knowledge.repository.client.dto.ChunkingRequest;
import com.mouhin.knowledge.repository.client.dto.CustomChunkInput;
import com.mouhin.knowledge.repository.client.dto.DocumentVO;
import com.mouhin.knowledge.repository.client.dto.KnowledgeStatsVO;
import com.mouhin.knowledge.repository.client.dto.PreviewDocumentQuery;
import com.mouhin.knowledge.repository.client.dto.PreviewResult;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.service.ExtractionStrategyStackParser;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 文档管理控制器
 *
 * @author mouhinU
 * @date 2026-09-02
 */
@RestController
@RequestMapping("/api/admin/document")
@Slf4j
public class DocumentAdminController {

    /** 异步任务响应 map 的公共 key（java:S1192 抽常量防 4×3 处漂移）。 */
    private static final String FIELD_DOCUMENT_KEY = "documentKey";

    private static final String FIELD_STATUS = "status";
    private static final String STATUS_STARTED = "STARTED";

    private final DocumentServiceI documentService;
    private final DocumentIngestionServiceI ingestionService;
    private final IndexAsyncCmdExe indexAsyncCmdExe;
    private final IndexCustomChunksAsyncCmdExe indexCustomChunksAsyncCmdExe;
    private final ReindexAsyncCmdExe reindexAsyncCmdExe;
    private final ExtractEnhanceAsyncCmdExe extractEnhanceAsyncCmdExe;
    private final IndexProgressStore indexProgressStore;

    public DocumentAdminController(
            DocumentServiceI documentService,
            DocumentIngestionServiceI ingestionService,
            IndexAsyncCmdExe indexAsyncCmdExe,
            IndexCustomChunksAsyncCmdExe indexCustomChunksAsyncCmdExe,
            ReindexAsyncCmdExe reindexAsyncCmdExe,
            ExtractEnhanceAsyncCmdExe extractEnhanceAsyncCmdExe,
            IndexProgressStore indexProgressStore) {
        this.documentService = documentService;
        this.ingestionService = ingestionService;
        this.indexAsyncCmdExe = indexAsyncCmdExe;
        this.indexCustomChunksAsyncCmdExe = indexCustomChunksAsyncCmdExe;
        this.reindexAsyncCmdExe = reindexAsyncCmdExe;
        this.extractEnhanceAsyncCmdExe = extractEnhanceAsyncCmdExe;
        this.indexProgressStore = indexProgressStore;
    }

    /** 获取文档详情 */
    @GetMapping("/{documentKey}")
    public ResponseEntity<DocumentVO> getDocument(@PathVariable String documentKey) {
        return ResponseEntity.ok(documentService.getDocument(documentKey));
    }

    /** 按所有者查询文档列表 */
    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<List<DocumentVO>> listByOwner(
            @PathVariable String ownerId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(documentService.listByOwner(ownerId, page, size));
    }

    /** 按部门查询文档列表 */
    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<DocumentVO>> listByDepartment(
            @PathVariable String departmentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(documentService.listByDepartment(departmentId, page, size));
    }

    /** 查询全部文档列表 */
    @GetMapping("/list")
    public ResponseEntity<List<DocumentVO>> listAll() {
        return ResponseEntity.ok(documentService.listDocuments());
    }

    /** 按状态查询文档列表 */
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

    /** 获取知识库统计 */
    @GetMapping("/stats")
    public ResponseEntity<KnowledgeStatsVO> getStats() {
        return ResponseEntity.ok(documentService.getStats());
    }

    /** 获取各分类文档数量统计 */
    @GetMapping("/category-stats")
    public ResponseEntity<Map<String, Long>> getCategoryStats() {
        return ResponseEntity.ok(documentService.getCategoryStats());
    }

    /** 归档文档 */
    @PutMapping("/{documentKey}/archive")
    public ResponseEntity<Map<String, String>> archive(@PathVariable String documentKey) {
        documentService.archive(documentKey);
        return ResponseEntity.ok(Map.of("message", "Document archived: " + documentKey));
    }

    /**
     * 重新入库（异步，通过 SSE 推送进度）
     *
     * <p>清理旧向量 / 分块 / 配图并重新提取、分块、向量化存储，进度复用 {@code /index/progress}。
     */
    @PostMapping("/{documentKey}/reindex")
    public ResponseEntity<Map<String, Object>> reindex(
            @PathVariable String documentKey, ChunkingRequest chunkingRequest) {

        log.info(
                "Async reindexing document {}: chunkSize={}, strategy={}",
                documentKey,
                chunkingRequest.getChunkSize(),
                chunkingRequest.getStrategy());

        var callback = indexProgressStore.createCallback(documentKey);
        reindexAsyncCmdExe.execute(
                documentKey,
                chunkingRequest.getChunkSize(),
                chunkingRequest.getOverlap(),
                chunkingRequest.getStrategy(),
                null,
                callback);

        return ResponseEntity.ok(
                Map.of(
                        FIELD_DOCUMENT_KEY,
                        documentKey,
                        FIELD_STATUS,
                        STATUS_STARTED,
                        "message",
                        "Reindexing started. Connect to SSE for progress."));
    }

    /**
     * 使用自定义分块重新入库（异步，通过 SSE 推送进度）
     *
     * <p>先清理旧向量 / 分块 / 配图，再以手动调整后的分块重新向量化存储。
     */
    @PostMapping("/{documentKey}/reindex-custom")
    public ResponseEntity<Map<String, Object>> reindexWithCustomChunks(
            @PathVariable String documentKey, @RequestBody List<CustomChunkInput> customChunks) {

        log.info(
                "Async reindexing document {} with {} custom chunks",
                documentKey,
                customChunks != null ? customChunks.size() : 0);

        var callback = indexProgressStore.createCallback(documentKey);
        reindexAsyncCmdExe.execute(documentKey, 0, 0, null, customChunks, callback);

        return ResponseEntity.ok(
                Map.of(
                        FIELD_DOCUMENT_KEY,
                        documentKey,
                        FIELD_STATUS,
                        STATUS_STARTED,
                        "message",
                        "Reindexing started. Connect to SSE for progress."));
    }

    /**
     * 重新解析（异步，不阻塞）。
     *
     * <p>依当前配置（或 {@code strategy} 强制策略栈）在后台重跑文本提取：命中 PDF 混合策略时按页补全视觉结果并回填缓存。 立即返回 {@code 202
     * ENQUEUED}；解析为长耗时外部 IO，不落库事务（红线 #8）。{@code strategy} 为 {@code ExtractionStrategyEnum} 枚举名
     * CSV，非法值按白名单丢弃，留空/{@code auto} 走配置默认路由。
     */
    @PostMapping("/{documentKey}/reparse")
    public ResponseEntity<Map<String, String>> reparse(
            @PathVariable String documentKey,
            @RequestParam(defaultValue = ExtractionStrategyStackParser.AUTO) String strategy) {
        List<String> forcedStack = ExtractionStrategyStackParser.parse(strategy);
        log.info(
                "Reparse enqueued for document {}: strategy={}, forcedStack={}",
                documentKey,
                strategy,
                forcedStack);
        extractEnhanceAsyncCmdExe.execute(documentKey, forcedStack);
        return ResponseEntity.accepted()
                .body(Map.of(FIELD_DOCUMENT_KEY, documentKey, FIELD_STATUS, "ENQUEUED"));
    }

    /** 解析预览（基于已上传文档，不入库） */
    @GetMapping("/{documentKey}/preview")
    public ResponseEntity<PreviewResult> preview(
            @PathVariable String documentKey, ChunkingRequest chunkingRequest) {

        log.info(
                "Preview document {}: chunkSize={}, strategy={}",
                documentKey,
                chunkingRequest.getChunkSize(),
                chunkingRequest.getStrategy());
        PreviewDocumentQuery query = new PreviewDocumentQuery();
        query.setDocumentKey(documentKey);
        query.setChunkSize(chunkingRequest.getChunkSize());
        query.setOverlap(chunkingRequest.getOverlap());
        query.setStrategy(chunkingRequest.getStrategy());
        PreviewResult result = ingestionService.previewFromDocument(query);
        return ResponseEntity.ok(result);
    }

    /** 确认入库（异步，通过 SSE 推送进度） */
    @PostMapping("/{documentKey}/index")
    public ResponseEntity<Map<String, Object>> indexDocument(
            @PathVariable String documentKey, ChunkingRequest chunkingRequest) {

        log.info(
                "Async indexing document {}: chunkSize={}, strategy={}",
                documentKey,
                chunkingRequest.getChunkSize(),
                chunkingRequest.getStrategy());

        var callback = indexProgressStore.createCallback(documentKey);
        indexAsyncCmdExe.execute(
                documentKey,
                chunkingRequest.getChunkSize(),
                chunkingRequest.getOverlap(),
                chunkingRequest.getStrategy(),
                callback);

        return ResponseEntity.ok(
                Map.of(
                        FIELD_DOCUMENT_KEY,
                        documentKey,
                        FIELD_STATUS,
                        STATUS_STARTED,
                        "message",
                        "Indexing started. Connect to SSE for progress."));
    }

    /** 使用自定义分块入库（异步，通过 SSE 推送进度） */
    @PostMapping("/{documentKey}/index-custom")
    public ResponseEntity<Map<String, Object>> indexWithCustomChunks(
            @PathVariable String documentKey, @RequestBody List<CustomChunkInput> customChunks) {

        log.info(
                "Async indexing document {} with {} custom chunks",
                documentKey,
                customChunks != null ? customChunks.size() : 0);

        var callback = indexProgressStore.createCallback(documentKey);
        indexCustomChunksAsyncCmdExe.execute(documentKey, customChunks, callback);

        return ResponseEntity.ok(
                Map.of(
                        FIELD_DOCUMENT_KEY,
                        documentKey,
                        FIELD_STATUS,
                        STATUS_STARTED,
                        "message",
                        "Indexing started. Connect to SSE for progress."));
    }

    /** 入库进度 SSE 端点 */
    @GetMapping("/{documentKey}/index/progress")
    public SseEmitter indexProgress(@PathVariable String documentKey) {
        return indexProgressStore.createEmitter(documentKey);
    }

    /** 删除文档 */
    @DeleteMapping("/{documentKey}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable String documentKey) {
        documentService.delete(documentKey);
        return ResponseEntity.ok(Map.of("message", "Document deleted: " + documentKey));
    }
}
