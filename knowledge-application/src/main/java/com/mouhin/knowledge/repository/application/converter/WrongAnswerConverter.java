package com.mouhin.knowledge.repository.application.converter;

import com.mouhin.knowledge.repository.application.util.ExamImages;
import com.mouhin.knowledge.repository.client.dto.StudentOptionVO;
import com.mouhin.knowledge.repository.client.dto.WrongAnswerVO;
import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.domain.model.entity.Student;

/**
 * 错题 / 考生选项 领域对象 → 视图对象 转换器（app 层）
 *
 * <p>映射与原 {@code WrongAnswerApplicationService.buildWrongAnswerMap} 保持一致（含 correctAnswer
 * 回退、analysis / scoringCriteria 字段顺序）。自「出卷即切分」纯结构化收尾起，答案 / 解析 / 评分标准 统一取自结构化题目行 {@link
 * ExamQuestion}，不再解析 {@code answer_key} 自由文本。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
public final class WrongAnswerConverter {

    private WrongAnswerConverter() {}

    /**
     * @param studentName 已解析的考生显示名（缺省 "未知考生"）
     * @param question 该题的结构化题目行（标准答案 / 解析 / 评分标准，可为 null）
     */
    public static WrongAnswerVO toVO(
            ExamAnswer answer, ExamSession session, String studentName, ExamQuestion question) {
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
        if ((correctAnswer == null || correctAnswer.isBlank())
                && question != null
                && question.getCorrectAnswer() != null) {
            correctAnswer = question.getCorrectAnswer();
        }
        vo.setCorrectAnswer(correctAnswer);
        vo.setStudentAnswer(answer.getStudentAnswer());
        vo.setAiFeedback(answer.getAiFeedback());
        vo.setAnalysis(question != null ? question.getAnalysis() : null);
        vo.setScoringCriteria(question != null ? question.getScoringCriteria() : null);
        vo.setImages(question != null ? ExamImages.parseOrNull(question.getImagesJson()) : null);
        vo.setCorrect(answer.getCorrect());
        vo.setSubmitTime(
                session.getSubmitTime() != null ? session.getSubmitTime().toString() : null);
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
