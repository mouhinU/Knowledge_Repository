package com.mouhin.knowledge.repository.domain.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * 文档分块实体
 *
 * @author mouhinU
 * @date 2026-09-02
 */
@Getter
@Setter
public class DocumentChunk {

    private Long id;

    /** 分块唯一标识（UUID） */
    private String chunkKey;

    /** 所属文档 ID */
    private Long documentId;

    /** 所属文档 Key（冗余，方便 Milvus 过滤） */
    private String documentKey;

    /** 分块在文档内的序号（从 0 开始） */
    private Integer chunkIndex;

    /** 起始页码（从 1 开始） */
    private Integer startPage;

    /** 结束页码 */
    private Integer endPage;

    /** 分块文本内容 */
    private String content;

    /** 文本 token 数（估算） */
    private Integer tokenCount;

    /** Milvus 中的向量 ID */
    private String vectorId;

    /** 所属部门 ID（冗余，用于权限过滤） */
    private String departmentId;

    /** 文档可见性（冗余，用于权限过滤） */
    private String visibility;

    /** 允许角色（冗余，JSON 数组字符串） */
    private String allowedRoles;

    /** 文档所有者 ID（冗余） */
    private String ownerId;

    /** 文档名称（冗余，用于 Milvus 元数据展示） */
    private String documentName;

    /** 文件类型（冗余，用于 Milvus 元数据过滤） */
    private String fileType;

    /** 标签（冗余，用于 Milvus 元数据过滤） */
    private String tags;

    /** 文档分类（冗余，用于 Milvus 元数据过滤） */
    private String category;

    /**
     * 租户业务键（P2-T2，SaaS 向量隔离）。
     *
     * <p>由 app / domain 构造点从 {@link
     * com.mouhin.knowledge.repository.domain.context.TenantContext#getOrDefault()} 读入，用于 {@code
     * MilvusVectorStoreService.storeChunks} 在写入 metadata 前解析数值 {@code tenant_id}。DB 侧不持久化本字段（{@code
     * kb_document_chunk.tenant_id} 由 MyBatis-Plus 拦截器自动注入），此处只作为向量写入路径的"随行标识"。
     */
    private String tenantKey;

    private LocalDateTime createdTime;

    // ==================== 业务方法 ====================

    /** 估算文本 token 数（中文约 1.5 字/token，英文约 4 字符/token） */
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
        return "DocumentChunk{"
                + "id="
                + id
                + ", chunkKey='"
                + chunkKey
                + '\''
                + ", documentId="
                + documentId
                + ", chunkIndex="
                + chunkIndex
                + ", startPage="
                + startPage
                + ", endPage="
                + endPage
                + ", tokenCount="
                + tokenCount
                + '}';
    }
}
