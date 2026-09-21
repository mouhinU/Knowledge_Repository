package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.ExamSessionDO;

/**
 * 考试场次 DO ↔ 领域对象转换器
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
public final class ExamSessionConverter {

    private ExamSessionConverter() {}

    public static ExamSession toDomain(ExamSessionDO doObj) {
        if (doObj == null) {
            return null;
        }
        ExamSession domain = new ExamSession();
        domain.setId(doObj.getId());
        domain.setSessionKey(doObj.getSessionKey());
        domain.setStudentId(doObj.getStudentId());
        domain.setExamHistoryId(doObj.getExamHistoryId());
        domain.setTopic(doObj.getTopic());
        domain.setDifficulty(doObj.getDifficulty());
        domain.setDurationMinutes(doObj.getDurationMinutes());
        domain.setExamPaper(doObj.getExamPaper());
        domain.setAnswerKey(doObj.getAnswerKey());
        domain.setQuestionsJson(doObj.getQuestionsJson());
        domain.setExamPlan(doObj.getExamPlan());
        domain.setTotalScore(doObj.getTotalScore());
        domain.setAiScore(doObj.getAiScore());
        domain.setFinalScore(doObj.getFinalScore());
        domain.setStatus(doObj.getStatus());
        domain.setVoided(Boolean.TRUE.equals(doObj.getVoided()));
        domain.setStartTime(doObj.getStartTime());
        domain.setSubmitTime(doObj.getSubmitTime());
        domain.setGradeTime(doObj.getGradeTime());
        domain.setPublishTime(doObj.getPublishTime());
        domain.setCreateTime(doObj.getCreateTime());
        domain.setUpdateTime(doObj.getUpdateTime());
        return domain;
    }

    public static ExamSessionDO toDO(ExamSession domain) {
        if (domain == null) {
            return null;
        }
        ExamSessionDO doObj = new ExamSessionDO();
        doObj.setId(domain.getId());
        doObj.setSessionKey(domain.getSessionKey());
        doObj.setStudentId(domain.getStudentId());
        doObj.setExamHistoryId(domain.getExamHistoryId());
        doObj.setTopic(domain.getTopic());
        doObj.setDifficulty(domain.getDifficulty());
        doObj.setDurationMinutes(domain.getDurationMinutes());
        doObj.setExamPaper(domain.getExamPaper());
        doObj.setAnswerKey(domain.getAnswerKey());
        doObj.setQuestionsJson(domain.getQuestionsJson());
        doObj.setExamPlan(domain.getExamPlan());
        doObj.setTotalScore(domain.getTotalScore());
        doObj.setAiScore(domain.getAiScore());
        doObj.setFinalScore(domain.getFinalScore());
        doObj.setStatus(domain.getStatus());
        doObj.setVoided(domain.isVoided());
        doObj.setStartTime(domain.getStartTime());
        doObj.setSubmitTime(domain.getSubmitTime());
        doObj.setGradeTime(domain.getGradeTime());
        doObj.setPublishTime(domain.getPublishTime());
        doObj.setCreateTime(domain.getCreateTime());
        doObj.setUpdateTime(domain.getUpdateTime());
        return doObj;
    }
}
