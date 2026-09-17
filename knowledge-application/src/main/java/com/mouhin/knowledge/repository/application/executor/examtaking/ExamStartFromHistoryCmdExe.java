package com.mouhin.knowledge.repository.application.executor.examtaking;

import com.mouhin.knowledge.repository.application.converter.ExamTakingConverter;
import com.mouhin.knowledge.repository.client.dto.ExamSessionDTO;
import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 从历史试卷开考命令执行器（app 层用例，事务边界）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class ExamStartFromHistoryCmdExe {

    private static final Logger logger = LoggerFactory.getLogger(ExamStartFromHistoryCmdExe.class);

    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    private final ExamHistoryGateway examHistoryGateway;
    private final ExamSessionGateway examSessionGateway;
    private final ExamTakingSupport support;

    public ExamStartFromHistoryCmdExe(ExamHistoryGateway examHistoryGateway,
                                      ExamSessionGateway examSessionGateway,
                                      ExamTakingSupport support) {
        this.examHistoryGateway = examHistoryGateway;
        this.examSessionGateway = examSessionGateway;
        this.support = support;
    }

    @Transactional
    public ExamSessionDTO execute(String studentToken, String historySessionId) {
        Student student = support.resolveStudent(studentToken);

        ExamHistory history = examHistoryGateway.findBySessionId(historySessionId)
                .orElseThrow(() -> new IllegalArgumentException("试卷不存在: " + historySessionId));

        if (history.getExamPaper() == null || history.getExamPaper().isBlank()) {
            throw new IllegalArgumentException("试卷内容为空");
        }

        ExamSession session = new ExamSession();
        session.setSessionKey(UUID.randomUUID().toString());
        session.setStudentId(student.getId());
        session.setExamHistoryId(history.getId());
        session.setTopic(history.getTopic());
        session.setDifficulty(history.getDifficulty());
        session.setExamPaper(history.getExamPaper());
        session.setAnswerKey(history.getAnswerKey());
        session.setExamPlan(history.getExamPlan());
        String rendered = support.renderWithPlan(history.getExamPaper(), history.getExamPlan());
        support.validateOrThrow(rendered);
        session.setQuestionsJson(rendered);
        session.setTotalScore(support.sumMaxScore(rendered));
        session.setDurationMinutes(history.getDurationMinutes());
        session.setStatus(STATUS_IN_PROGRESS);
        session.setStartTime(LocalDateTime.now());
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());

        examSessionGateway.save(session);
        logger.info("考生开始考试 [student={}, history={}, session={}]",
                student.getId(), historySessionId, session.getSessionKey());
        return ExamTakingConverter.toSessionDTO(session);
    }
}
