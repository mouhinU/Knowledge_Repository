package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 文章生成请求
 *
 * @author mouhinU
 * @date 2026-09-12
 */
@Getter
@Setter
public class ArticleGenerationRequest {

    /** 前端预分配的会话 ID（与 SSE 连接关联） */
    private String sessionId;

    /** 用户问题 */
    private String question;

    /** 用户 ID */
    private String userId;

    /** 用户部门 ID */
    private String departmentId;

    /** 用户角色（逗号分隔） */
    private String roles;

    /** 是否管理员 */
    private Boolean admin;

    /** 知识库分类过滤 */
    private String category;
}
