package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 租户 LLM 配置数据对象（P4-T4）。
 *
 * <p>映射 {@code sys_tenant_llm_profile} 表。{@code apiKey} 字段在 {@code mode=local} 时存 AES-256-GCM 密文，
 * {@code mode=none} 时存明文。
 *
 * @author mouhinU
 * @date 2026-10-11
 */
@Getter
@Setter
@TableName("sys_tenant_llm_profile")
public class LlmProfileDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属租户 id（UNIQUE 约束）。
     */
    private Long tenantId;

    /**
     * API Key（密文或明文，取决于 knowledge.secret.cipher.mode）。
     */
    private String apiKey;

    /**
     * LLM API base URL。
     */
    private String baseUrl;

    /**
     * 对话模型名称。
     */
    private String model;

    /**
     * 向量模型名称。
     */
    private String embeddingModel;

    /**
     * 向量维度。
     */
    private Integer dimension;

    /**
     * 最后更新时间。
     */
    private LocalDateTime updatedAt;
}
