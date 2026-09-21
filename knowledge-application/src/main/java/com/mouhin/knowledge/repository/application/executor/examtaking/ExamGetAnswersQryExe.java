package com.mouhin.knowledge.repository.application.executor.examtaking;

import com.mouhin.knowledge.repository.application.converter.ExamTakingConverter;
import com.mouhin.knowledge.repository.client.dto.ExamAnswerDTO;
import com.mouhin.knowledge.repository.domain.gateway.ExamAnswerGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 考试答题记录查询执行器（app 层用例）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class ExamGetAnswersQryExe {

    private final ExamAnswerGateway examAnswerGateway;
    private final ExamTakingSupport support;

    public ExamGetAnswersQryExe(ExamAnswerGateway examAnswerGateway, ExamTakingSupport support) {
        this.examAnswerGateway = examAnswerGateway;
        this.support = support;
    }

    public List<ExamAnswerDTO> execute(String sessionKey, String studentToken) {
        ExamSession session = support.resolveSession(sessionKey, studentToken);
        return examAnswerGateway.listBySessionId(session.getId()).stream()
                .map(ExamTakingConverter::toAnswerDTO)
                .toList();
    }
}
