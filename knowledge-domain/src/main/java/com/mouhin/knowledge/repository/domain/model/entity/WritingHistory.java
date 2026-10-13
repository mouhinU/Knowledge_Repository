package com.mouhin.knowledge.repository.domain.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 写作历史记录实体
 *
 * @author mouhinU
 * @date 2026-09-13
 */
@Getter
@Setter
public class WritingHistory {

    private Long id;

    /** 会话 ID */
    private String sessionId;

    /** 用户提问 */
    private String question;

    /** 最终文章 */
    private String finalArticle;

    /** 草稿文章（审核前） */
    private String draftArticle;

    /** 质量评分 */
    private Integer qualityScore;

    /** 检索到的知识块数量 */
    private Integer retrievedChunks;

    /** 关键发现 */
    private String keyFindings;

    /** 审核反馈 */
    private String reviewFeedback;

    /** 用户 ID */
    private String userId;

    /** 部门 ID */
    private String departmentId;

    /** 状态：COMPLETED / FAILED */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== equals / hashCode ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WritingHistory that = (WritingHistory) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
