package com.mouhin.knowledge.repository.application.executor.examtaking;

import com.mouhin.knowledge.repository.application.converter.ExamTakingConverter;
import com.mouhin.knowledge.repository.application.util.ExamPaperParser;
import com.mouhin.knowledge.repository.client.dto.ExamSessionDTO;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 使用即时试卷开考命令执行器（app 层用例，事务边界）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class ExamStartWithPaperCmdExe {

    private static final Logger logger = LoggerFactory.getLogger(ExamStartWithPaperCmdExe.class);

    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    private static final String DEFAULT_TOPIC = "在线考试";

    private final ExamSessionGateway examSessionGateway;
    private final ExamTakingSupport support;

    public ExamStartWithPaperCmdExe(ExamSessionGateway examSessionGateway,
                                    ExamTakingSupport support) {
        this.examSessionGateway = examSessionGateway;
        this.support = support;
    }

    @Transactional
    public ExamSessionDTO execute(String studentToken, String examPaper, String answerKey,
                                  String topic, String difficulty) {
        Student student = support.resolveStudent(studentToken);

        ExamSession session = new ExamSession();
        session.setSessionKey(UUID.randomUUID().toString());
        session.setStudentId(student.getId());
        session.setTopic(topic != null ? topic : DEFAULT_TOPIC);
        session.setDifficulty(difficulty);
        session.setExamPaper(examPaper);
        session.setAnswerKey(answerKey);
        String rendered = support.renderNoPlan(examPaper);
        support.validateOrThrow(rendered);
        session.setQuestionsJson(rendered);
        session.setTotalScore(support.sumMaxScore(rendered));
        session.setDurationMinutes(ExamPaperParser.parseDuration(examPaper));
        session.setStatus(STATUS_IN_PROGRESS);
        session.setStartTime(LocalDateTime.now());
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());

        examSessionGateway.save(session);
        logger.info("考生开始考试（即时试卷）[student={}, session={}]",
                student.getId(), session.getSessionKey());
        return ExamTakingConverter.toSessionDTO(session);
    }
}
