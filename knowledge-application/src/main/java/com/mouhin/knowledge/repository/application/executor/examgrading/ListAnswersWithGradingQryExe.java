package com.mouhin.knowledge.repository.application.executor.examgrading;

import com.mouhin.knowledge.repository.application.converter.ExamTakingConverter;
import com.mouhin.knowledge.repository.client.dto.ExamAnswerDTO;
import com.mouhin.knowledge.repository.domain.gateway.ExamAnswerGateway;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 查询某场考试答题记录（含评分详情）执行器
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class ListAnswersWithGradingQryExe {

    private final ExamAnswerGateway examAnswerGateway;

    public ListAnswersWithGradingQryExe(ExamAnswerGateway examAnswerGateway) {
        this.examAnswerGateway = examAnswerGateway;
    }

    public List<ExamAnswerDTO> execute(Long sessionId) {
        return examAnswerGateway.listBySessionId(sessionId).stream()
                .map(ExamTakingConverter::toAnswerDTO)
                .toList();
    }
}
