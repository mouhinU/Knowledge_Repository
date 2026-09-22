package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 解析结果持久缓存数据对象（Phase C，表 {@code kb_extraction_cache}）。
 *
 * <p>复合唯一键 = checksum + strategy + model_name + prompt_hash + render_mode，命中即免去重复解析。 {@code
 * resultJson} 存放序列化的 {@link
 * com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult}。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Getter
@Setter
@TableName("kb_extraction_cache")
public class ExtractionCacheDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("checksum")
    private String checksum;

    @TableField("strategy")
    private String strategy;

    @TableField("model_name")
    private String modelName;

    @TableField("prompt_hash")
    private String promptHash;

    @TableField("render_mode")
    private String renderMode;

    @TableField("result_json")
    private String resultJson;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
