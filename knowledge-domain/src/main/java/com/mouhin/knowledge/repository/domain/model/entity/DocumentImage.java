package com.mouhin.knowledge.repository.domain.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 文档内嵌图片实体。
 *
 * <p>记录从知识库文档中提取并落盘的单张位图，作为看图写话 / 看图题的配图来源。二进制存于文件系统， 本实体仅承载定位与展示所需的元数据。{@code assetKey}
 * 为对外访问句柄（随机、不可枚举）， 浏览器通过它拉取图片，避免暴露自增主键。
 *
 * @author mouhinU
 * @date 2026-09-20
 */
public class DocumentImage {

    private Long id;

    /** 对外访问句柄（UUID，无连字符），浏览器经 /api/exam/assets/{assetKey} 拉取 */
    private String assetKey;

    /** 所属文档 ID */
    private Long documentId;

    /** 所属文档 Key（冗余，便于按业务标识检索） */
    private String documentKey;

    /** 磁盘存储相对 / 绝对路径 */
    private String storagePath;

    /** 图片内容 SHA-256（十六进制），用于文档内去重 */
    private String sha256;

    /** MIME 类型（image/png、image/jpeg 等） */
    private String mimeType;

    /** 来源页码 / 工作表序号 / 幻灯片序号（1 起始） */
    private Integer pageNo;

    /** 同页内图片序号（0 起始） */
    private Integer seqOnPage;

    /** 像素宽 */
    private Integer width;

    /** 像素高 */
    private Integer height;

    /** 字节数 */
    private Long byteSize;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== Getters & Setters ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAssetKey() {
        return assetKey;
    }

    public void setAssetKey(String assetKey) {
        this.assetKey = assetKey;
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

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getSeqOnPage() {
        return seqOnPage;
    }

    public void setSeqOnPage(Integer seqOnPage) {
        this.seqOnPage = seqOnPage;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Long getByteSize() {
        return byteSize;
    }

    public void setByteSize(Long byteSize) {
        this.byteSize = byteSize;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
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
        DocumentImage that = (DocumentImage) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "DocumentImage{"
                + "id="
                + id
                + ", assetKey='"
                + assetKey
                + '\''
                + ", documentId="
                + documentId
                + ", pageNo="
                + pageNo
                + ", mimeType='"
                + mimeType
                + '\''
                + ", width="
                + width
                + ", height="
                + height
                + '}';
    }
}
