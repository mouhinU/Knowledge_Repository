package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.ExamAnswerDO;

/**
 * 答题记录 DO ↔ 领域对象转换器
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
public final class ExamAnswerConverter {

    private ExamAnswerConverter() {}

    public static ExamAnswer toDomain(ExamAnswerDO doObj) {
        if (doObj == null) {
            return null;
        }
        ExamAnswer domain = new ExamAnswer();
        domain.setId(doObj.getId());
        domain.setSessionId(doObj.getSessionId());
        domain.setQuestionIndex(doObj.getQuestionIndex());
        domain.setQuestionNumber(doObj.getQuestionNumber());
        domain.setQuestionType(doObj.getQuestionType());
        domain.setQuestionContent(doObj.getQuestionContent());
        domain.setOptionsJson(doObj.getOptionsJson());
        domain.setMaxScore(doObj.getMaxScore());
        domain.setCorrectAnswer(doObj.getCorrectAnswer());
        domain.setStudentAnswer(doObj.getStudentAnswer());
        domain.setCorrect(doObj.getCorrect());
        domain.setAiScore(doObj.getAiScore());
        domain.setAiFeedback(doObj.getAiFeedback());
        domain.setAiInput(doObj.getAiInput());
        domain.setAiRawOutput(doObj.getAiRawOutput());
        domain.setReviewScore(doObj.getReviewScore());
        domain.setReviewFeedback(doObj.getReviewFeedback());
        domain.setReviewedBy(doObj.getReviewedBy());
        domain.setReviewTime(doObj.getReviewTime());
        domain.setCreateTime(doObj.getCreateTime());
        domain.setUpdateTime(doObj.getUpdateTime());
        return domain;
    }

    public static ExamAnswerDO toDO(ExamAnswer domain) {
        if (domain == null) {
            return null;
        }
        ExamAnswerDO doObj = new ExamAnswerDO();
        doObj.setId(domain.getId());
        doObj.setSessionId(domain.getSessionId());
        doObj.setQuestionIndex(domain.getQuestionIndex());
        doObj.setQuestionNumber(domain.getQuestionNumber());
        doObj.setQuestionType(domain.getQuestionType());
        doObj.setQuestionContent(domain.getQuestionContent());
        doObj.setOptionsJson(domain.getOptionsJson());
        doObj.setMaxScore(domain.getMaxScore());
        doObj.setCorrectAnswer(domain.getCorrectAnswer());
        doObj.setStudentAnswer(domain.getStudentAnswer());
        doObj.setCorrect(domain.getCorrect());
        doObj.setAiScore(domain.getAiScore());
        doObj.setAiFeedback(domain.getAiFeedback());
        doObj.setAiInput(domain.getAiInput());
        doObj.setAiRawOutput(domain.getAiRawOutput());
        doObj.setReviewScore(domain.getReviewScore());
        doObj.setReviewFeedback(domain.getReviewFeedback());
        doObj.setReviewedBy(domain.getReviewedBy());
        doObj.setReviewTime(domain.getReviewTime());
        doObj.setCreateTime(domain.getCreateTime());
        doObj.setUpdateTime(domain.getUpdateTime());
        return doObj;
    }
}
