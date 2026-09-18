package com.mouhin.knowledge.repository.application.converter;

import com.mouhin.knowledge.repository.client.dto.ExamHistoryDTO;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;

/**
 * 出卷历史 领域实体 → 视图对象 转换器（app 层）
 *
 * <p>22 个属性逐一映射，力求与原实体 Jackson 序列化结果保持键集合、顺序与取值一致。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
public final class ExamGenerationConverter {

    private ExamGenerationConverter() {
    }

    public static ExamHistoryDTO toHistoryDTO(ExamHistory history) {
        if (history == null) {
            return null;
        }
        ExamHistoryDTO dto = new ExamHistoryDTO();
        dto.setId(history.getId());
        dto.setSessionId(history.getSessionId());
        dto.setTopic(history.getTopic());
        dto.setDifficulty(history.getDifficulty());
        dto.setDurationMinutes(history.getDurationMinutes());
        dto.setQuestionConfig(history.getQuestionConfig());
        dto.setExamPlan(history.getExamPlan());
        dto.setExamPaper(history.getExamPaper());
        dto.setAnswerKey(history.getAnswerKey());
        dto.setQualityScore(history.getQualityScore());
        dto.setScoreDetail(history.getScoreDetail());
        dto.setRetrievedChunks(history.getRetrievedChunks());
        dto.setKeyFindings(history.getKeyFindings());
        dto.setReviewFeedback(history.getReviewFeedback());
        dto.setDifficultyAssessment(history.getDifficultyAssessment());
        dto.setDeduplicationReport(history.getDeduplicationReport());
        dto.setUserId(history.getUserId());
        dto.setDepartmentId(history.getDepartmentId());
        dto.setCategory(history.getCategory());
        dto.setStatus(history.getStatus());
        dto.setErrorMessage(history.getErrorMessage());
        dto.setCreateTime(history.getCreateTime());
        dto.setUpdateTime(history.getUpdateTime());
        return dto;
    }
}
