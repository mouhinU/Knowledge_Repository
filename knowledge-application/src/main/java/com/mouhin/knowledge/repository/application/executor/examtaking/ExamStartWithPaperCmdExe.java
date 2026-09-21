package com.mouhin.knowledge.repository.application.executor.examtaking;

import com.mouhin.knowledge.repository.application.converter.ExamTakingConverter;
import com.mouhin.knowledge.repository.application.executor.examgeneration.ExamQuestionSplitSupport;
import com.mouhin.knowledge.repository.application.util.ExamPaperParser;
import com.mouhin.knowledge.repository.client.dto.ExamSessionDTO;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 使用即时试卷开考命令执行器（app 层用例，事务边界）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
@Slf4j
public class ExamStartWithPaperCmdExe {

    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    private static final String DEFAULT_TOPIC = "在线考试";

    private final ExamSessionGateway examSessionGateway;
    private final ExamTakingSupport support;
    private final ExamQuestionSplitSupport examQuestionSplitSupport;

    public ExamStartWithPaperCmdExe(
            ExamSessionGateway examSessionGateway,
            ExamTakingSupport support,
            ExamQuestionSplitSupport examQuestionSplitSupport) {
        this.examSessionGateway = examSessionGateway;
        this.support = support;
        this.examQuestionSplitSupport = examQuestionSplitSupport;
    }

    @Transactional
    public ExamSessionDTO execute(
            String studentToken,
            String examPaper,
            String answerKey,
            String topic,
            String difficulty) {
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

        // 出卷即切分 · 开考回填（阶段 1-F）：即时试卷未经生成 / 校对切分，此处按本场唯一
        // sessionKey 回灌结构化题目行，供答题保存 / 评分纯读结构化数据。回灌失败不阻断开考，
        // 评分阶段仍有答案键解析兜底。
        try {
            ExamQuestionSplitSupport.SplitOutcome outcome =
                    examQuestionSplitSupport.splitAndPersist(
                            session.getSessionKey(), examPaper, answerKey, null);
            log.info("即时试卷结构化回灌完成 [session={}, rows={}]", session.getSessionKey(), outcome.count());
        } catch (Exception e) {
            log.warn(
                    "即时试卷结构化回灌失败（不影响开考，评分阶段回退答案键解析） [session={}]: {}",
                    session.getSessionKey(),
                    e.getMessage());
        }

        log.info("考生开始考试（即时试卷）[student={}, session={}]", student.getId(), session.getSessionKey());
        return ExamTakingConverter.toSessionDTO(session);
    }
}
