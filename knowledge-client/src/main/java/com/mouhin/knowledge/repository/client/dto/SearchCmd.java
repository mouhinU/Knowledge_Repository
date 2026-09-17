package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 知识库语义检索命令
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Getter
@Setter
public class SearchCmd {

    /** 查询文本 */
    private String query;
    /** 用户 ID */
    private String userId;
    /** 部门 ID */
    private String departmentId;
    /** 角色（逗号分隔） */
    private String roles;
    /** 是否超级管理员 */
    private boolean admin;
    /** 最大返回数量（null 用默认） */
    private Integer maxResults;
    /** 最低相似度（null 用默认） */
    private Double minScore;
    /** 分类过滤（null / 空表示不过滤） */
    private String category;
}
