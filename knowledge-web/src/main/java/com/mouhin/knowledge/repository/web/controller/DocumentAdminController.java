package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.application.service.DocumentManagementApplicationService;
import com.mouhin.knowledge.repository.application.service.DocumentManagementApplicationService.KnowledgeStats;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    public DocumentAdminController(DocumentManagementApplicationService managementService) {
        this.managementService = managementService;
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
     * 归档文档
     */
    @PutMapping("/{documentKey}/archive")
    public ResponseEntity<Map<String, String>> archive(@PathVariable String documentKey) {
        managementService.archive(documentKey);
        return ResponseEntity.ok(Map.of("message", "Document archived: " + documentKey));
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/{documentKey}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable String documentKey) {
        managementService.delete(documentKey);
        return ResponseEntity.ok(Map.of("message", "Document deleted: " + documentKey));
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
