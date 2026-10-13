package com.mouhin.knowledge.repository.application.converter;

import com.mouhin.knowledge.repository.client.dto.WritingHistoryDTO;
import com.mouhin.knowledge.repository.domain.model.entity.WritingHistory;

/**
 * 写作历史 领域实体 → 视图对象 转换器（app 层）
 *
 * <p>15 个属性逐一映射，力求与原实体 Jackson 序列化结果保持键集合、顺序与取值一致。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
public final class ArticleConverter {

    private ArticleConverter() {}

    public static WritingHistoryDTO toHistoryDTO(WritingHistory history) {
        if (history == null) {
            return null;
        }
        WritingHistoryDTO dto = new WritingHistoryDTO();
        dto.setId(history.getId());
        dto.setSessionId(history.getSessionId());
        dto.setQuestion(history.getQuestion());
        dto.setFinalArticle(history.getFinalArticle());
        dto.setDraftArticle(history.getDraftArticle());
        dto.setQualityScore(history.getQualityScore());
        dto.setRetrievedChunks(history.getRetrievedChunks());
        dto.setKeyFindings(history.getKeyFindings());
        dto.setReviewFeedback(history.getReviewFeedback());
        dto.setUserId(history.getUserId());
        dto.setDepartmentId(history.getDepartmentId());
        dto.setStatus(history.getStatus());
        dto.setErrorMessage(history.getErrorMessage());
        dto.setCreateTime(history.getCreateTime());
        dto.setUpdateTime(history.getUpdateTime());
        return dto;
    }
}
