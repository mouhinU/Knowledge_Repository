package com.mouhin.knowledge.repository.client.dto;

/**
 * 文章生成请求
 *
 * @author Knowledge-Repository
 * @date 2026-09-12
 */
public class ArticleGenerationRequest {

    /**
     * 前端预分配的会话 ID（与 SSE 连接关联）
     */
    private String sessionId;

    /**
     * 用户问题
     */
    private String question;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 用户部门 ID
     */
    private String departmentId;

    /**
     * 用户角色（逗号分隔）
     */
    private String roles;

    /**
     * 是否管理员
     */
    private Boolean admin;

    /**
     * 知识库分类过滤
     */
    private String category;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getRoles() {
        return roles;
    }

    public void setRoles(String roles) {
        this.roles = roles;
    }

    public Boolean getAdmin() {
        return admin;
    }

    public void setAdmin(Boolean admin) {
        this.admin = admin;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
