package com.mouhin.knowledge.repository.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识库语义检索命令
 *
 * <p>P0-T1 安全加固：{@code userId}/{@code departmentId}/{@code roles}/{@code admin} 属于调用方身份， 只能由
 * adapter 层从 {@code AdminPrincipalSupport} 回读后再通过 setter 显式写入 DTO；此处对 Jackson 标记 {@code
 * Access.READ_ONLY}，即使将来出现 {@code @RequestBody SearchCmd} 直接绑定的端点， 请求体伪造的身份字段也会被忽略，越权路径被结构性封堵。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Getter
@Setter
public class SearchCmd {

    /**
     * 查询文本
     */
    private String query;

    /**
     * 用户 ID —— 服务端令牌回读，禁止反序列化
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String userId;

    /**
     * 部门 ID —— 服务端令牌回读，禁止反序列化
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String departmentId;

    /**
     * 角色（逗号分隔）—— 服务端令牌回读，禁止反序列化
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String roles;

    /**
     * 是否超级管理员 —— 服务端令牌回读，禁止反序列化
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private boolean admin;

    /**
     * 最大返回数量（null 用默认）
     */
    private Integer maxResults;

    /**
     * 最低相似度（null 用默认）
     */
    private Double minScore;

    /**
     * 分类过滤（null / 空表示不过滤）
     */
    private String category;
}
