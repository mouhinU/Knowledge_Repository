package com.mouhin.knowledge.repository.application.executor.examgrading;

import com.mouhin.knowledge.repository.application.converter.ExamTakingConverter;
import com.mouhin.knowledge.repository.application.util.AnswerKeyParser;
import com.mouhin.knowledge.repository.client.dto.ExamAnswerDTO;
import com.mouhin.knowledge.repository.domain.gateway.ExamAnswerGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 查询某场考试答题记录（含评分详情）执行器
 * <p>
 * 答题记录的 {@code correctAnswer} 列在保存阶段并不落库（仅评分流程按需写入），
 * 因此对复核视图而言常为空。这里在返回前用本场考试的「标准答案与评分标准」Markdown
 * （{@link ExamSession#getAnswerKey()}）经 {@link AnswerKeyParser} 解析出的答案键，
 * 按全局题序回填缺失的正确答案，供管理端「成绩复核详情」展示。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class ListAnswersWithGradingQryExe {

    private final ExamAnswerGateway examAnswerGateway;
    private final ExamSessionGateway examSessionGateway;

    public ListAnswersWithGradingQryExe(ExamAnswerGateway examAnswerGateway,
                                        ExamSessionGateway examSessionGateway) {
        this.examAnswerGateway = examAnswerGateway;
        this.examSessionGateway = examSessionGateway;
    }

    public List<ExamAnswerDTO> execute(Long sessionId) {
        Map<Integer, AnswerKeyParser.QuestionKey> keyMap = loadAnswerKeyMap(sessionId);
        return examAnswerGateway.listBySessionId(sessionId).stream()
                .map(answer -> {
                    ExamAnswerDTO dto = ExamTakingConverter.toAnswerDTO(answer);
                    if (dto.getCorrectAnswer() == null || dto.getCorrectAnswer().isBlank()) {
                        AnswerKeyParser.QuestionKey key = keyMap.get(dto.getQuestionIndex());
                        if (key != null && key.answer() != null && !key.answer().isBlank()) {
                            dto.setCorrectAnswer(key.answer());
                        }
                    }
                    return dto;
                })
                .toList();
    }

    /**
     * 读取并解析本场考试的答案键，返回「全局题序 → 答案」映射；缺答案键时返回空表。
     */
    private Map<Integer, AnswerKeyParser.QuestionKey> loadAnswerKeyMap(Long sessionId) {
        return examSessionGateway.findById(sessionId)
                .map(ExamSession::getAnswerKey)
                .filter(ak -> ak != null && !ak.isBlank())
                .map(AnswerKeyParser::parse)
                .orElseGet(Map::of);
    }
}
