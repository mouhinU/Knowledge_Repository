package com.mouhin.knowledge.repository.application.executor.examgrading;

import com.mouhin.knowledge.repository.application.converter.ExamTakingConverter;
import com.mouhin.knowledge.repository.application.service.ExamStructuredQuestionSupport;
import com.mouhin.knowledge.repository.client.dto.ExamAnswerDTO;
import com.mouhin.knowledge.repository.domain.gateway.ExamAnswerGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 查询某场考试答题记录（含评分详情）执行器
 * <p>
 * 答题记录的 {@code correctAnswer} 列在保存阶段并不落库（仅评分流程按需写入），
 * 因此对复核视图而言常为空。这里以「出卷即切分」的结构化题目行
 * （{@code kb_exam_question}，经 {@link ExamStructuredQuestionSupport} 纯读）为唯一权威来源，
 * 按印刷题号回填缺失的正确答案，供管理端「成绩复核详情」展示——不再解析 {@code answer_key}
 * 自由文本，杜绝与评分链路的口径漂移。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class ListAnswersWithGradingQryExe {

    private final ExamAnswerGateway examAnswerGateway;
    private final ExamSessionGateway examSessionGateway;
    private final ExamStructuredQuestionSupport structuredQuestionSupport;

    public ListAnswersWithGradingQryExe(ExamAnswerGateway examAnswerGateway,
                                        ExamSessionGateway examSessionGateway,
                                        ExamStructuredQuestionSupport structuredQuestionSupport) {
        this.examAnswerGateway = examAnswerGateway;
        this.examSessionGateway = examSessionGateway;
        this.structuredQuestionSupport = structuredQuestionSupport;
    }

    public List<ExamAnswerDTO> execute(Long sessionId) {
        List<ExamAnswer> answers = examAnswerGateway.listBySessionId(sessionId);
        Map<Integer, ExamQuestion> questionMap = examSessionGateway.findById(sessionId)
                .map(structuredQuestionSupport::loadByQuestionNumber)
                .orElseGet(Map::of);
        return answers.stream()
                .map(answer -> {
                    ExamAnswerDTO dto = ExamTakingConverter.toAnswerDTO(answer);
                    if (dto.getCorrectAnswer() == null || dto.getCorrectAnswer().isBlank()) {
                        ExamQuestion question = lookup(questionMap, answer);
                        if (question != null && question.getCorrectAnswer() != null
                                && !question.getCorrectAnswer().isBlank()) {
                            dto.setCorrectAnswer(question.getCorrectAnswer());
                        }
                    }
                    return dto;
                })
                .toList();
    }

    /**
     * 按权威题号定位结构化题目行：优先印刷题号（{@code question_number}），
     * 缺失时回退全局位置序号（与答案键题序一致）。
     */
    private ExamQuestion lookup(Map<Integer, ExamQuestion> questionMap, ExamAnswer answer) {
        Integer number = answer.getQuestionNumber() != null
                ? answer.getQuestionNumber() : answer.getQuestionIndex();
        return number != null ? questionMap.get(number) : null;
    }
}
