package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.application.executor.docingestion.UploadFromFileCmdExe;
import com.mouhin.knowledge.repository.application.executor.docingestion.UploadOnlyCmdExe;
import com.mouhin.knowledge.repository.client.dto.ChunkUploadRequest;
import com.mouhin.knowledge.repository.client.dto.DocumentUploadRequest;
import com.mouhin.knowledge.repository.client.dto.DocumentVO;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import com.mouhin.knowledge.repository.infrastructure.upload.UploadSessionManager;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档上传控制器
 *
 * <p>仅负责文件上传与文本提取，分块与向量化由管理端确认后执行。
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@RestController
@RequestMapping("/api/document")
@Slf4j
public class DocumentUploadController {

    /** 统一响应 map 的 error message 字段名（java:S1192：抽公共 key 防字面量漂移）。 */
    private static final String FIELD_ERROR_MESSAGE = "errorMessage";

    private final UploadOnlyCmdExe uploadOnlyCmdExe;
    private final UploadFromFileCmdExe uploadFromFileCmdExe;
    private final UploadSessionManager uploadSessionManager;

    public DocumentUploadController(
            UploadOnlyCmdExe uploadOnlyCmdExe,
            UploadFromFileCmdExe uploadFromFileCmdExe,
            UploadSessionManager uploadSessionManager) {
        this.uploadOnlyCmdExe = uploadOnlyCmdExe;
        this.uploadFromFileCmdExe = uploadFromFileCmdExe;
        this.uploadSessionManager = uploadSessionManager;
    }

    /**
     * 上传文档（仅提取文本，不分块、不向量化）
     *
     * <p>上传成功后文档状态为 UPLOADED，用户可在管理端预览解析效果并确认入库。
     *
     * @param file 文档文件（支持 PDF/Word/Excel/PPT/TXT/CSV/HTML）
     * @param ownerId 所有者用户 ID
     * @param departmentId 所属部门 ID
     * @param visibility 可见性（PUBLIC / INTERNAL / RESTRICTED / PRIVATE）
     * @param allowedRoles 允许访问的角色（逗号分隔）
     * @param tags 标签（逗号分隔）
     */
    @PostMapping(
            value = "/upload",
            consumes = {
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
            @RequestParam("file") MultipartFile file, DocumentUploadRequest request) {

        String ownerId = request.getOwnerId();
        String departmentId = request.getDepartmentId();
        String visibility = request.getVisibility() != null ? request.getVisibility() : "INTERNAL";

        log.info(
                "Uploading document: {}, owner={}, dept={}",
                file.getOriginalFilename(),
                ownerId,
                departmentId);

        String contentType = file.getContentType();
        if (contentType == null || !isSupportedFileType(contentType)) {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "errorCode",
                                    "BAD_REQUEST",
                                    FIELD_ERROR_MESSAGE,
                                    "Unsupported file type. Supported formats: PDF, Word, Excel, PPT, TXT, CSV, HTML"));
        }

        DocumentVisibilityEnum vis;
        try {
            vis = DocumentVisibilityEnum.valueOf(visibility.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "errorCode",
                                    "BAD_REQUEST",
                                    FIELD_ERROR_MESSAGE,
                                    "Invalid visibility: " + visibility));
        }

        DocumentVO document =
                uploadOnlyCmdExe.execute(
                        file,
                        ownerId,
                        departmentId,
                        vis,
                        request.getAllowedRoles(),
                        request.getTags(),
                        request.getCategory());

        return ResponseEntity.ok(
                Map.of(
                        "documentKey", document.getDocumentKey(),
                        "fileName", document.getFileName(),
                        "status", document.getStatus(),
                        "message", "Document uploaded. Please preview and confirm indexing."));
    }

    // ==================== 分片上传（断点续传） ====================

    /** 初始化分片上传会话 */
    @PostMapping("/upload/init")
    public ResponseEntity<Map<String, Object>> initChunkedUpload(
            @RequestBody ChunkUploadRequest request) {
        try {
            String uploadId =
                    uploadSessionManager.createSession(
                            request.getFileName(), request.getFileSize(), request.getTotalChunks());

            log.info(
                    "初始化分片上传 [uploadId={}, fileName={}, chunks={}]",
                    uploadId,
                    request.getFileName(),
                    request.getTotalChunks());

            return ResponseEntity.ok(Map.of("uploadId", uploadId, "uploadedChunks", Set.of()));
        } catch (Exception e) {
            log.error("初始化分片上传失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("errorCode", "INIT_FAILED", FIELD_ERROR_MESSAGE, e.getMessage()));
        }
    }

    /** 上传单个分片 */
    @PostMapping(value = "/upload/chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadChunk(
            @RequestParam("file") MultipartFile chunk,
            @RequestParam("uploadId") String uploadId,
            @RequestParam("chunkIndex") int chunkIndex) {

        try {
            boolean complete =
                    uploadSessionManager.saveChunk(uploadId, chunkIndex, chunk.getInputStream());

            return ResponseEntity.ok(
                    Map.of(
                            "chunkIndex", chunkIndex,
                            "complete", complete));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "errorCode",
                                    "INVALID_SESSION",
                                    FIELD_ERROR_MESSAGE,
                                    e.getMessage()));
        } catch (Exception e) {
            log.error("上传分片失败 [uploadId={}, chunk={}]", uploadId, chunkIndex, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("errorCode", "CHUNK_FAILED", FIELD_ERROR_MESSAGE, e.getMessage()));
        }
    }

    /** 完成分片上传，组装文件并创建文档 */
    @PostMapping("/upload/complete")
    public ResponseEntity<Map<String, Object>> completeChunkedUpload(
            @RequestParam("uploadId") String uploadId, DocumentUploadRequest request) {

        try {
            // 先获取文件信息（assembleChunks 会清理会话）
            UploadSessionManager.UploadSession session = uploadSessionManager.getSession(uploadId);
            if (session == null) {
                return ResponseEntity.badRequest()
                        .body(
                                Map.of(
                                        "errorCode",
                                        "INVALID_SESSION",
                                        FIELD_ERROR_MESSAGE,
                                        "上传会话不存在或已过期"));
            }
            String fileName = session.getFileName();

            Path assembledFile = uploadSessionManager.assembleChunks(uploadId);

            String ownerId = request.getOwnerId() != null ? request.getOwnerId() : "anonymous";
            String departmentId = request.getDepartmentId();
            String visibility =
                    request.getVisibility() != null ? request.getVisibility() : "INTERNAL";

            DocumentVisibilityEnum vis;
            try {
                vis = DocumentVisibilityEnum.valueOf(visibility.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(
                                Map.of(
                                        "errorCode",
                                        "BAD_REQUEST",
                                        FIELD_ERROR_MESSAGE,
                                        "Invalid visibility: " + visibility));
            }

            // 从已组装文件创建文档
            DocumentVO document =
                    uploadFromFileCmdExe.execute(
                            assembledFile,
                            fileName,
                            ownerId,
                            departmentId,
                            vis,
                            request.getAllowedRoles(),
                            request.getTags(),
                            request.getCategory());

            return ResponseEntity.ok(
                    Map.of(
                            "documentKey", document.getDocumentKey(),
                            "fileName", document.getFileName(),
                            "status", document.getStatus(),
                            "message", "Document uploaded. Please preview and confirm indexing."));
        } catch (Exception e) {
            log.error("完成分片上传失败 [uploadId={}]", uploadId, e);
            return ResponseEntity.internalServerError()
                    .body(
                            Map.of(
                                    "errorCode",
                                    "COMPLETE_FAILED",
                                    FIELD_ERROR_MESSAGE,
                                    e.getMessage()));
        }
    }

    /** 查询上传会话状态（已上传的分片列表，用于断点续传） */
    @GetMapping("/upload/status/{uploadId}")
    public ResponseEntity<Map<String, Object>> getUploadStatus(@PathVariable String uploadId) {
        Set<Integer> uploadedChunks = uploadSessionManager.getUploadedChunks(uploadId);
        UploadSessionManager.UploadSession session = uploadSessionManager.getSession(uploadId);

        if (session == null && uploadedChunks.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(
                Map.of(
                        "uploadId", uploadId,
                        "totalChunks", session != null ? session.getTotalChunks() : 0,
                        "uploadedChunks", uploadedChunks));
    }

    /** 取消上传会话 */
    @PostMapping("/upload/cancel/{uploadId}")
    public ResponseEntity<Map<String, String>> cancelUpload(@PathVariable String uploadId) {
        uploadSessionManager.cancelSession(uploadId);
        return ResponseEntity.ok(Map.of("status", "cancelled"));
    }

    private boolean isSupportedFileType(String contentType) {
        return contentType.equals("application/pdf")
                || contentType.equals("application/msword")
                || contentType.equals(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                || contentType.equals("application/vnd.ms-excel")
                || contentType.equals(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                || contentType.equals("application/vnd.ms-powerpoint")
                || contentType.equals(
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation")
                || contentType.equals("text/plain")
                || contentType.equals("text/csv")
                || contentType.equals("text/html");
    }
}
