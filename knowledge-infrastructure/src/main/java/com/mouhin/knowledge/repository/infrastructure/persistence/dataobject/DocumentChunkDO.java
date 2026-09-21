package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 文档分块数据对象
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@Getter
@Setter
@TableName("kb_document_chunk")
public class DocumentChunkDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("chunk_key")
    private String chunkKey;

    @TableField("document_id")
    private Long documentId;

    @TableField("document_key")
    private String documentKey;

    @TableField("chunk_index")
    private Integer chunkIndex;

    @TableField("start_page")
    private Integer startPage;

    @TableField("end_page")
    private Integer endPage;

    @TableField("content")
    private String content;

    @TableField("token_count")
    private Integer tokenCount;

    @TableField("vector_id")
    private String vectorId;

    @TableField("department_id")
    private String departmentId;

    @TableField("visibility")
    private String visibility;

    @TableField("allowed_roles")
    private String allowedRoles;

    @TableField("owner_id")
    private String ownerId;

    @TableField("category")
    private String category;

    @TableField("create_time")
    private LocalDateTime createTime;
}
