package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.ExamQuestionDO;

/**
 * 结构化题目 DO ↔ 领域对象转换器
 *
 * @author Knowledge-Repository
 * @date 2026-09-18
 */
public final class ExamQuestionConverter {

    private ExamQuestionConverter() {
    }

    public static ExamQuestion toDomain(ExamQuestionDO doObj) {
        if (doObj == null) {
            return null;
        }
        ExamQuestion domain = new ExamQuestion();
        domain.setId(doObj.getId());
        domain.setSessionKey(doObj.getSessionKey());
        domain.setQuestionNumber(doObj.getQuestionNumber());
        domain.setSectionLabel(doObj.getSectionLabel());
        domain.setQuestionType(doObj.getQuestionType());
        domain.setStem(doObj.getStem());
        domain.setOptionsJson(doObj.getOptionsJson());
        domain.setBlankCount(doObj.getBlankCount());
        domain.setMaxScore(doObj.getMaxScore());
        domain.setCorrectAnswer(doObj.getCorrectAnswer());
        domain.setAnalysis(doObj.getAnalysis());
        domain.setScoringCriteria(doObj.getScoringCriteria());
        domain.setCreateTime(doObj.getCreateTime());
        domain.setUpdateTime(doObj.getUpdateTime());
        return domain;
    }

    public static ExamQuestionDO toDO(ExamQuestion domain) {
        if (domain == null) {
            return null;
        }
        ExamQuestionDO doObj = new ExamQuestionDO();
        doObj.setId(domain.getId());
        doObj.setSessionKey(domain.getSessionKey());
        doObj.setQuestionNumber(domain.getQuestionNumber());
        doObj.setSectionLabel(domain.getSectionLabel());
        doObj.setQuestionType(domain.getQuestionType());
        doObj.setStem(domain.getStem());
        doObj.setOptionsJson(domain.getOptionsJson());
        doObj.setBlankCount(domain.getBlankCount());
        doObj.setMaxScore(domain.getMaxScore());
        doObj.setCorrectAnswer(domain.getCorrectAnswer());
        doObj.setAnalysis(domain.getAnalysis());
        doObj.setScoringCriteria(domain.getScoringCriteria());
        doObj.setCreateTime(domain.getCreateTime());
        doObj.setUpdateTime(domain.getUpdateTime());
        return doObj;
    }
}
