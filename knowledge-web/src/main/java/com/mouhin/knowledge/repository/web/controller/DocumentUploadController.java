package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.application.service.DocumentIngestionApplicationService;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import com.mouhin.knowledge.repository.web.dto.DocumentUploadRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文档上传控制器
 * <p>
 * 仅负责文件上传与文本提取，分块与向量化由管理端确认后执行。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@RestController
@RequestMapping("/api/document")
public class DocumentUploadController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentUploadController.class);

    private final DocumentIngestionApplicationService ingestionService;

    public DocumentUploadController(DocumentIngestionApplicationService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * 上传文档（仅提取文本，不分块、不向量化）
     * <p>
     * 上传成功后文档状态为 UPLOADED，用户可在管理端预览解析效果并确认入库。
     * </p>
     *
     * @param file         文档文件（支持 PDF/Word/Excel/PPT/TXT/CSV/HTML）
     * @param ownerId      所有者用户 ID
     * @param departmentId 所属部门 ID
     * @param visibility   可见性（PUBLIC / INTERNAL / RESTRICTED / PRIVATE）
     * @param allowedRoles 允许访问的角色（逗号分隔）
     * @param tags         标签（逗号分隔）
     */
    @PostMapping(value = "/upload", consumes = {
            MediaType.MULTIPART_FORM_DATA_VALUE,
            MediaType.APPLICATION_PDF_VALUE,
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            MediaType.TEXT_PLAIN_VALUE,
            "text/csv",
            MediaType.TEXT_HTML_VALUE
    })
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            DocumentUploadRequest request) {

        String ownerId = request.getOwnerId();
        String departmentId = request.getDepartmentId();
        String visibility = request.getVisibility() != null ? request.getVisibility() : "INTERNAL";

        logger.info("Uploading document: {}, owner={}, dept={}", file.getOriginalFilename(), ownerId, departmentId);

        String contentType = file.getContentType();
        if (contentType == null || !isSupportedFileType(contentType)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "errorCode", "BAD_REQUEST",
                    "errorMessage", "Unsupported file type. Supported formats: PDF, Word, Excel, PPT, TXT, CSV, HTML"
            ));
        }

        DocumentVisibilityEnum vis;
        try {
            vis = DocumentVisibilityEnum.valueOf(visibility.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "errorCode", "BAD_REQUEST",
                    "errorMessage", "Invalid visibility: " + visibility
            ));
        }

        Document document = ingestionService.uploadOnly(
                file, ownerId, departmentId, vis, request.getAllowedRoles(), request.getTags());

        return ResponseEntity.ok(Map.of(
                "documentKey", document.getDocumentKey(),
                "fileName", document.getFileName(),
                "status", document.getStatus().name(),
                "message", "Document uploaded. Please preview and confirm indexing."
        ));
    }

    private boolean isSupportedFileType(String contentType) {
        return contentType.equals("application/pdf")
                || contentType.equals("application/msword")
                || contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                || contentType.equals("application/vnd.ms-excel")
                || contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                || contentType.equals("application/vnd.ms-powerpoint")
                || contentType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")
                || contentType.equals("text/plain")
                || contentType.equals("text/csv")
                || contentType.equals("text/html");
    }
}
