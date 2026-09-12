package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 文档数据对象
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@Getter
@Setter
@TableName("kb_document")
public class DocumentDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("document_key")
    private String documentKey;

    @TableField("file_name")
    private String fileName;

    @TableField("file_type")
    private String fileType;

    @TableField("file_size")
    private Long fileSize;

    @TableField("storage_path")
    private String storagePath;

    @TableField("file_checksum")
    private String fileChecksum;

    @TableField("total_pages")
    private Integer totalPages;

    @TableField("status")
    private String status;

    @TableField("visibility")
    private String visibility;

    @TableField("owner_id")
    private String ownerId;

    @TableField("department_id")
    private String departmentId;

    @TableField("allowed_roles")
    private String allowedRoles;

    @TableField("summary")
    private String summary;

    @TableField("chunk_max_size")
    private Integer chunkMaxSize;

    @TableField("chunk_overlap")
    private Integer chunkOverlap;

    @TableField("respect_paragraph")
    private Boolean respectParagraph;

    @TableField("respect_page")
    private Boolean respectPage;

    @TableField("chunking_strategy")
    private String chunkingStrategy;

    @TableField("error_message")
    private String errorMessage;

    @TableField("tags")
    private String tags;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
