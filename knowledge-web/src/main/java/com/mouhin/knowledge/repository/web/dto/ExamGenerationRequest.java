package com.mouhin.knowledge.repository.web.dto;

/**
 * 试卷生成请求
 *
 * @author Knowledge-Repository
 * @date 2026-09-13
 */
public class ExamGenerationRequest {

    /**
     * 会话 ID（前端预分配，用于 SSE 关联）
     */
    private String sessionId;

    /**
     * 考试主题/科目
     */
    private String topic;

    /**
     * 难度：EASY / MEDIUM / HARD
     */
    private String difficulty;

    /**
     * 单选题数量
     */
    private Integer singleChoiceCount;

    /**
     * 多选题数量
     */
    private Integer multiChoiceCount;

    /**
     * 判断题数量
     */
    private Integer trueFalseCount;

    /**
     * 填空题数量
     */
    private Integer fillBlankCount;

    /**
     * 简答题数量
     */
    private Integer shortAnswerCount;

    /**
     * 论述题数量
     */
    private Integer essayCount;

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

    /**
     * 学段：PRIMARY（小学）/ JUNIOR（初中）/ SENIOR（高中），留空则自动识别
     */
    private String schoolLevel;

    /**
     * 页面确认的题型分布方案（ExamPlan 的 JSON 序列化结果）。
     * 两步式流程：先由后端 Agent 生成方案，用户在页面调整后回传此字段用于出卷。
     */
    private String distribution;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public Integer getSingleChoiceCount() {
        return singleChoiceCount;
    }

    public void setSingleChoiceCount(Integer singleChoiceCount) {
        this.singleChoiceCount = singleChoiceCount;
    }

    public Integer getMultiChoiceCount() {
        return multiChoiceCount;
    }

    public void setMultiChoiceCount(Integer multiChoiceCount) {
        this.multiChoiceCount = multiChoiceCount;
    }

    public Integer getTrueFalseCount() {
        return trueFalseCount;
    }

    public void setTrueFalseCount(Integer trueFalseCount) {
        this.trueFalseCount = trueFalseCount;
    }

    public Integer getFillBlankCount() {
        return fillBlankCount;
    }

    public void setFillBlankCount(Integer fillBlankCount) {
        this.fillBlankCount = fillBlankCount;
    }

    public Integer getShortAnswerCount() {
        return shortAnswerCount;
    }

    public void setShortAnswerCount(Integer shortAnswerCount) {
        this.shortAnswerCount = shortAnswerCount;
    }

    public Integer getEssayCount() {
        return essayCount;
    }

    public void setEssayCount(Integer essayCount) {
        this.essayCount = essayCount;
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

    public String getSchoolLevel() {
        return schoolLevel;
    }

    public void setSchoolLevel(String schoolLevel) {
        this.schoolLevel = schoolLevel;
    }

    public String getDistribution() {
        return distribution;
    }

    public void setDistribution(String distribution) {
        this.distribution = distribution;
    }

    /**
     * 计算总题目数
     */
    public int getTotalCount() {
        return (singleChoiceCount != null ? singleChoiceCount : 0)
                + (multiChoiceCount != null ? multiChoiceCount : 0)
                + (trueFalseCount != null ? trueFalseCount : 0)
                + (fillBlankCount != null ? fillBlankCount : 0)
                + (shortAnswerCount != null ? shortAnswerCount : 0)
                + (essayCount != null ? essayCount : 0);
    }
}
