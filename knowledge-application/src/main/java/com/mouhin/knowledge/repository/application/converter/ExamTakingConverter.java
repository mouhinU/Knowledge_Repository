package com.mouhin.knowledge.repository.application.converter;

import com.mouhin.knowledge.repository.client.dto.ExamAnswerDTO;
import com.mouhin.knowledge.repository.client.dto.ExamSessionDTO;
import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;

/**
 * 考试场次 / 答题记录 领域实体 → 视图对象 转换器（app 层）
 *
 * <p>字段逐一映射，力求与原实体 Jackson 序列化结果保持键集合与取值一致： 答题记录额外求值 {@code effectiveScore}、{@code objective}
 * 两个派生属性。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
public final class ExamTakingConverter {

    private ExamTakingConverter() {}

    public static ExamSessionDTO toSessionDTO(ExamSession session) {
        ExamSessionDTO dto = new ExamSessionDTO();
        dto.setId(session.getId());
        dto.setSessionKey(session.getSessionKey());
        dto.setStudentId(session.getStudentId());
        dto.setExamHistoryId(session.getExamHistoryId());
        dto.setTopic(session.getTopic());
        dto.setDifficulty(session.getDifficulty());
        dto.setDurationMinutes(session.getDurationMinutes());
        dto.setExamPaper(session.getExamPaper());
        dto.setAnswerKey(session.getAnswerKey());
        dto.setQuestionsJson(session.getQuestionsJson());
        dto.setExamPlan(session.getExamPlan());
        dto.setTotalScore(session.getTotalScore());
        dto.setAiScore(session.getAiScore());
        dto.setFinalScore(session.getFinalScore());
        dto.setStatus(session.getStatus());
        dto.setVoided(session.isVoided());
        dto.setStartTime(session.getStartTime());
        dto.setSubmitTime(session.getSubmitTime());
        dto.setGradeTime(session.getGradeTime());
        dto.setPublishTime(session.getPublishTime());
        dto.setCreateTime(session.getCreateTime());
        dto.setUpdateTime(session.getUpdateTime());
        return dto;
    }

    public static ExamAnswerDTO toAnswerDTO(ExamAnswer answer) {
        ExamAnswerDTO dto = new ExamAnswerDTO();
        dto.setId(answer.getId());
        dto.setSessionId(answer.getSessionId());
        dto.setQuestionIndex(answer.getQuestionIndex());
        dto.setQuestionType(answer.getQuestionType());
        dto.setQuestionContent(answer.getQuestionContent());
        dto.setOptionsJson(answer.getOptionsJson());
        dto.setMaxScore(answer.getMaxScore());
        dto.setCorrectAnswer(answer.getCorrectAnswer());
        dto.setStudentAnswer(answer.getStudentAnswer());
        dto.setCorrect(answer.getCorrect());
        dto.setAiScore(answer.getAiScore());
        dto.setAiFeedback(answer.getAiFeedback());
        dto.setAiInput(answer.getAiInput());
        dto.setAiRawOutput(answer.getAiRawOutput());
        dto.setReviewScore(answer.getReviewScore());
        dto.setReviewFeedback(answer.getReviewFeedback());
        dto.setReviewedBy(answer.getReviewedBy());
        dto.setReviewTime(answer.getReviewTime());
        dto.setCreateTime(answer.getCreateTime());
        dto.setUpdateTime(answer.getUpdateTime());
        // 派生属性：与实体 getter 计算逻辑保持一致
        dto.setObjective(answer.isObjective());
        dto.setEffectiveScore(answer.getEffectiveScore());
        return dto;
    }
}
