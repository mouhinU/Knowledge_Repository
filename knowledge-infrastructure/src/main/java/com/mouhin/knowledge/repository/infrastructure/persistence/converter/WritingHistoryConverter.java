package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.mouhin.knowledge.repository.domain.model.entity.WritingHistory;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.WritingHistoryDO;

/**
 * AI 写作历史 DO ↔ 领域对象转换器
 *
 * @author Knowledge-Repository
 * @date 2026-09-13
 */
public final class WritingHistoryConverter {

    private WritingHistoryConverter() {}

    public static WritingHistory toDomain(WritingHistoryDO doObj) {
        if (doObj == null) {
            return null;
        }
        WritingHistory domain = new WritingHistory();
        domain.setId(doObj.getId());
        domain.setSessionId(doObj.getSessionId());
        domain.setQuestion(doObj.getQuestion());
        domain.setFinalArticle(doObj.getFinalArticle());
        domain.setDraftArticle(doObj.getDraftArticle());
        domain.setQualityScore(doObj.getQualityScore());
        domain.setRetrievedChunks(doObj.getRetrievedChunks());
        domain.setKeyFindings(doObj.getKeyFindings());
        domain.setReviewFeedback(doObj.getReviewFeedback());
        domain.setUserId(doObj.getUserId());
        domain.setDepartmentId(doObj.getDepartmentId());
        domain.setStatus(doObj.getStatus());
        domain.setErrorMessage(doObj.getErrorMessage());
        domain.setCreateTime(doObj.getCreateTime());
        domain.setUpdateTime(doObj.getUpdateTime());
        return domain;
    }

    public static WritingHistoryDO toDO(WritingHistory domain) {
        if (domain == null) {
            return null;
        }
        WritingHistoryDO doObj = new WritingHistoryDO();
        doObj.setId(domain.getId());
        doObj.setSessionId(domain.getSessionId());
        doObj.setQuestion(domain.getQuestion());
        doObj.setFinalArticle(domain.getFinalArticle());
        doObj.setDraftArticle(domain.getDraftArticle());
        doObj.setQualityScore(domain.getQualityScore());
        doObj.setRetrievedChunks(domain.getRetrievedChunks());
        doObj.setKeyFindings(domain.getKeyFindings());
        doObj.setReviewFeedback(domain.getReviewFeedback());
        doObj.setUserId(domain.getUserId());
        doObj.setDepartmentId(domain.getDepartmentId());
        doObj.setStatus(domain.getStatus());
        doObj.setErrorMessage(domain.getErrorMessage());
        doObj.setCreateTime(domain.getCreateTime());
        doObj.setUpdateTime(domain.getUpdateTime());
        return doObj;
    }
}
