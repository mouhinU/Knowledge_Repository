package com.mouhin.knowledge.repository.application.converter;

import com.mouhin.knowledge.repository.application.util.AnswerKeyParser;
import com.mouhin.knowledge.repository.client.dto.StudentOptionVO;
import com.mouhin.knowledge.repository.client.dto.WrongAnswerVO;
import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.domain.model.entity.Student;

/**
 * 错题 / 考生选项 领域对象 → 视图对象 转换器（app 层）
 *
 * <p>映射与原 {@code WrongAnswerApplicationService.buildWrongAnswerMap} 及
 * {@code WrongAnswerController.listStudents} 完全一致（含 correctAnswer 回退、analysis /
 * scoringCriteria 来自答案键、字段顺序）。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
public final class WrongAnswerConverter {

    private WrongAnswerConverter() {
    }

    /**
     * @param studentName 已解析的考生显示名（缺省 "未知考生"）
     * @param key         该题的标准答案 / 解析 / 评分标准（可为 null）
     */
    public static WrongAnswerVO toVO(ExamAnswer answer, ExamSession session,
                                     String studentName, AnswerKeyParser.QuestionKey key) {
        WrongAnswerVO vo = new WrongAnswerVO();
        vo.setAnswerId(answer.getId());
        vo.setSessionId(answer.getSessionId());
        vo.setSessionKey(session.getSessionKey());
        vo.setTopic(session.getTopic());
        vo.setDifficulty(session.getDifficulty());
        vo.setStudentId(session.getStudentId());
        vo.setStudentName(studentName);
        vo.setQuestionIndex(answer.getQuestionIndex());
        vo.setQuestionType(answer.getQuestionType());
        vo.setQuestionContent(answer.getQuestionContent());
        vo.setOptionsJson(answer.getOptionsJson());
        vo.setMaxScore(answer.getMaxScore());
        vo.setEffectiveScore(answer.getEffectiveScore());
        String correctAnswer = answer.getCorrectAnswer();
        if ((correctAnswer == null || correctAnswer.isBlank()) && key != null && key.answer() != null) {
            correctAnswer = key.answer();
        }
        vo.setCorrectAnswer(correctAnswer);
        vo.setStudentAnswer(answer.getStudentAnswer());
        vo.setAiFeedback(answer.getAiFeedback());
        vo.setAnalysis(key != null ? key.analysis() : null);
        vo.setScoringCriteria(key != null ? key.scoringCriteria() : null);
        vo.setCorrect(answer.getCorrect());
        vo.setSubmitTime(session.getSubmitTime() != null ? session.getSubmitTime().toString() : null);
        return vo;
    }

    public static StudentOptionVO toOptionVO(Student student) {
        StudentOptionVO vo = new StudentOptionVO();
        vo.setId(student.getId());
        vo.setUsername(student.getUsername());
        vo.setDisplayName(student.getDisplayName());
        vo.setStudentNo(student.getStudentNo());
        return vo;
    }
}
