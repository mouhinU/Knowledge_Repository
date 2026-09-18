package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.ExamHistoryDO;

/**
 * AI 出卷历史 DO ↔ 领域对象转换器
 *
 * @author Knowledge-Repository
 * @date 2026-09-14
 */
public final class ExamHistoryConverter {

    private ExamHistoryConverter() {
    }

    public static ExamHistory toDomain(ExamHistoryDO doObj) {
        if (doObj == null) {
            return null;
        }
        ExamHistory domain = new ExamHistory();
        domain.setId(doObj.getId());
        domain.setSessionId(doObj.getSessionId());
        domain.setTopic(doObj.getTopic());
        domain.setDifficulty(doObj.getDifficulty());
        domain.setDurationMinutes(doObj.getDurationMinutes());
        domain.setQuestionConfig(doObj.getQuestionConfig());
        domain.setExamPlan(doObj.getExamPlan());
        domain.setExamPaper(doObj.getExamPaper());
        domain.setAnswerKey(doObj.getAnswerKey());
        domain.setQualityScore(doObj.getQualityScore());
        domain.setScoreDetail(doObj.getScoreDetail());
        domain.setRetrievedChunks(doObj.getRetrievedChunks());
        domain.setKeyFindings(doObj.getKeyFindings());
        domain.setReviewFeedback(doObj.getReviewFeedback());
        domain.setDifficultyAssessment(doObj.getDifficultyAssessment());
        domain.setDeduplicationReport(doObj.getDeduplicationReport());
        domain.setUserId(doObj.getUserId());
        domain.setDepartmentId(doObj.getDepartmentId());
        domain.setCategory(doObj.getCategory());
        domain.setStatus(doObj.getStatus());
        domain.setErrorMessage(doObj.getErrorMessage());
        domain.setCreateTime(doObj.getCreateTime());
        domain.setUpdateTime(doObj.getUpdateTime());
        return domain;
    }

    public static ExamHistoryDO toDO(ExamHistory domain) {
        if (domain == null) {
            return null;
        }
        ExamHistoryDO doObj = new ExamHistoryDO();
        doObj.setId(domain.getId());
        doObj.setSessionId(domain.getSessionId());
        doObj.setTopic(domain.getTopic());
        doObj.setDifficulty(domain.getDifficulty());
        doObj.setDurationMinutes(domain.getDurationMinutes());
        doObj.setQuestionConfig(domain.getQuestionConfig());
        doObj.setExamPlan(domain.getExamPlan());
        doObj.setExamPaper(domain.getExamPaper());
        doObj.setAnswerKey(domain.getAnswerKey());
        doObj.setQualityScore(domain.getQualityScore());
        doObj.setScoreDetail(domain.getScoreDetail());
        doObj.setRetrievedChunks(domain.getRetrievedChunks());
        doObj.setKeyFindings(domain.getKeyFindings());
        doObj.setReviewFeedback(domain.getReviewFeedback());
        doObj.setDifficultyAssessment(domain.getDifficultyAssessment());
        doObj.setDeduplicationReport(domain.getDeduplicationReport());
        doObj.setUserId(domain.getUserId());
        doObj.setDepartmentId(domain.getDepartmentId());
        doObj.setCategory(domain.getCategory());
        doObj.setStatus(domain.getStatus());
        doObj.setErrorMessage(domain.getErrorMessage());
        doObj.setCreateTime(domain.getCreateTime());
        doObj.setUpdateTime(domain.getUpdateTime());
        return doObj;
    }
}
