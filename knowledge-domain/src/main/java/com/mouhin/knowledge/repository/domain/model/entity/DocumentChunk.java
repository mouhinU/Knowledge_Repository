package com.mouhin.knowledge.repository.domain.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 文档分块实体
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
public class DocumentChunk {

    private Long id;

    /**
     * 分块唯一标识（UUID）
     */
    private String chunkKey;

    /**
     * 所属文档 ID
     */
    private Long documentId;

    /**
     * 所属文档 Key（冗余，方便 Milvus 过滤）
     */
    private String documentKey;

    /**
     * 分块在文档内的序号（从 0 开始）
     */
    private Integer chunkIndex;

    /**
     * 起始页码（从 1 开始）
     */
    private Integer startPage;

    /**
     * 结束页码
     */
    private Integer endPage;

    /**
     * 分块文本内容
     */
    private String content;

    /**
     * 文本 token 数（估算）
     */
    private Integer tokenCount;

    /**
     * Milvus 中的向量 ID
     */
    private String vectorId;

    /**
     * 所属部门 ID（冗余，用于权限过滤）
     */
    private String departmentId;

    /**
     * 文档可见性（冗余，用于权限过滤）
     */
    private String visibility;

    /**
     * 允许角色（冗余，JSON 数组字符串）
     */
    private String allowedRoles;

    /**
     * 文档所有者 ID（冗余）
     */
    private String ownerId;

    /**
     * 文档名称（冗余，用于 Milvus 元数据展示）
     */
    private String documentName;

    /**
     * 文件类型（冗余，用于 Milvus 元数据过滤）
     */
    private String fileType;

    /**
     * 标签（冗余，用于 Milvus 元数据过滤）
     */
    private String tags;

    /**
     * 文档分类（冗余，用于 Milvus 元数据过滤）
     */
    private String category;

    private LocalDateTime createdTime;

    // ==================== 业务方法 ====================

    /**
     * 估算文本 token 数（中文约 1.5 字/token，英文约 4 字符/token）
     */
    public int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        return (int) Math.ceil(chineseChars / 1.5) + (int) Math.ceil(otherChars / 4.0);
    }

    // ==================== Getters & Setters ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getChunkKey() {
        return chunkKey;
    }

    public void setChunkKey(String chunkKey) {
        this.chunkKey = chunkKey;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getDocumentKey() {
        return documentKey;
    }

    public void setDocumentKey(String documentKey) {
        this.documentKey = documentKey;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public Integer getStartPage() {
        return startPage;
    }

    public void setStartPage(Integer startPage) {
        this.startPage = startPage;
    }

    public Integer getEndPage() {
        return endPage;
    }

    public void setEndPage(Integer endPage) {
        this.endPage = endPage;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
    }

    public String getVectorId() {
        return vectorId;
    }

    public void setVectorId(String vectorId) {
        this.vectorId = vectorId;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getAllowedRoles() {
        return allowedRoles;
    }

    public void setAllowedRoles(String allowedRoles) {
        this.allowedRoles = allowedRoles;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
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

    // ==================== equals / hashCode / toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DocumentChunk that = (DocumentChunk) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "DocumentChunk{" +
                "id=" + id +
                ", chunkKey='" + chunkKey + '\'' +
                ", documentId=" + documentId +
                ", chunkIndex=" + chunkIndex +
                ", startPage=" + startPage +
                ", endPage=" + endPage +
                ", tokenCount=" + tokenCount +
                '}';
    }
}
