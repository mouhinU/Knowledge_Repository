package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 文档内嵌图片数据对象
 *
 * @author Knowledge-Repository
 * @date 2026-09-20
 */
@Getter
@Setter
@TableName("kb_document_image")
public class DocumentImageDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("asset_key")
    private String assetKey;

    @TableField("document_id")
    private Long documentId;

    @TableField("document_key")
    private String documentKey;

    @TableField("storage_path")
    private String storagePath;

    @TableField("sha256")
    private String sha256;

    @TableField("mime_type")
    private String mimeType;

    @TableField("page_no")
    private Integer pageNo;

    @TableField("seq_on_page")
    private Integer seqOnPage;

    @TableField("width")
    private Integer width;

    @TableField("height")
    private Integer height;

    @TableField("byte_size")
    private Long byteSize;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
