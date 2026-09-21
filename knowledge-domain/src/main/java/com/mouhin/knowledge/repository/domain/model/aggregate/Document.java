package com.mouhin.knowledge.repository.domain.model.aggregate;

import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingConfig;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 文档聚合根
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
public class Document {

    private Long id;

    /** 文档唯一标识（UUID） */
    private String documentKey;

    /** 原始文件名 */
    private String fileName;

    /** 文件 MIME 类型 */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件存储路径（对象存储 / 本地） */
    private String storagePath;

    /** 文件 MD5 校验值（用于去重） */
    private String fileChecksum;

    /** 文档总页数 */
    private Integer totalPages;

    /** 处理状态 */
    private DocumentStatusEnum status;

    /** 可见性 */
    private DocumentVisibilityEnum visibility;

    /** 所有者用户 ID */
    private String ownerId;

    /** 所属部门 ID */
    private String departmentId;

    /** 允许访问的角色（JSON 数组字符串，如 ["ADMIN","MANAGER"]） */
    private String allowedRoles;

    /** 文档摘要（AI 生成或手动填写） */
    private String summary;

    /** 分块配置 */
    private ChunkingConfig chunkingConfig;

    /** 错误信息（处理失败时记录） */
    private String errorMessage;

    /** 标签（逗号分隔） */
    private String tags;

    /** 文档分类（如：工作、学习、休闲） */
    private String category;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    // ==================== 业务方法 ====================

    /** 标记为处理中 */
    public void markProcessing() {
        if (this.status != DocumentStatusEnum.UPLOADED
                && this.status != DocumentStatusEnum.FAILED) {
            throw new IllegalStateException("Cannot start processing from status: " + this.status);
        }
        this.status = DocumentStatusEnum.PROCESSING;
        this.errorMessage = null;
    }

    /** 标记为已索引 */
    public void markIndexed(int totalPages) {
        if (this.status != DocumentStatusEnum.PROCESSING) {
            throw new IllegalStateException("Cannot mark indexed from status: " + this.status);
        }
        this.status = DocumentStatusEnum.INDEXED;
        this.totalPages = totalPages;
    }

    /** 标记为处理失败 */
    public void markFailed(String errorMessage) {
        this.status = DocumentStatusEnum.FAILED;
        this.errorMessage = errorMessage;
    }

    /** 归档文档 */
    public void archive() {
        if (this.status != DocumentStatusEnum.INDEXED) {
            throw new IllegalStateException("Only indexed documents can be archived");
        }
        this.status = DocumentStatusEnum.ARCHIVED;
    }

    /** 校验文档创建参数 */
    public void validateForCreate() {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be blank");
        }
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("storagePath must not be blank");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        if (departmentId == null || departmentId.isBlank()) {
            throw new IllegalArgumentException("departmentId must not be blank");
        }
    }

    /** 判断文档是否可被检索 */
    public boolean isSearchable() {
        return this.status == DocumentStatusEnum.INDEXED;
    }

    // ==================== Getters & Setters ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDocumentKey() {
        return documentKey;
    }

    public void setDocumentKey(String documentKey) {
        this.documentKey = documentKey;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getFileChecksum() {
        return fileChecksum;
    }

    public void setFileChecksum(String fileChecksum) {
        this.fileChecksum = fileChecksum;
    }

    public Integer getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(Integer totalPages) {
        this.totalPages = totalPages;
    }

    public DocumentStatusEnum getStatus() {
        return status;
    }

    public void setStatus(DocumentStatusEnum status) {
        this.status = status;
    }

    public DocumentVisibilityEnum getVisibility() {
        return visibility;
    }

    public void setVisibility(DocumentVisibilityEnum visibility) {
        this.visibility = visibility;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getAllowedRoles() {
        return allowedRoles;
    }

    public void setAllowedRoles(String allowedRoles) {
        this.allowedRoles = allowedRoles;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public ChunkingConfig getChunkingConfig() {
        return chunkingConfig;
    }

    public void setChunkingConfig(ChunkingConfig chunkingConfig) {
        this.chunkingConfig = chunkingConfig;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }

    // ==================== equals / hashCode / toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Document document = (Document) o;
        return Objects.equals(id, document.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Document{"
                + "id="
                + id
                + ", documentKey='"
                + documentKey
                + '\''
                + ", fileName='"
                + fileName
                + '\''
                + ", status="
                + status
                + ", visibility="
                + visibility
                + ", ownerId='"
                + ownerId
                + '\''
                + ", departmentId='"
                + departmentId
                + '\''
                + '}';
    }
}
