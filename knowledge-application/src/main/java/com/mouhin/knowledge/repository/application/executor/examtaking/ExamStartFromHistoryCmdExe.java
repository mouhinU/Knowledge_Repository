package com.mouhin.knowledge.repository.application.executor.examtaking;

import com.mouhin.knowledge.repository.application.converter.ExamTakingConverter;
import com.mouhin.knowledge.repository.client.dto.ExamSessionDTO;
import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 从历史试卷开考命令执行器（app 层用例，事务边界）
 *
 * <p>系统级门禁：（1）试卷未发布不可开考；（2）试卷已作废（VOIDED）不可开考； （3）同一考生对同一份试卷仅允许开考一次——已存在任意状态的历史场次即拒绝再次开考，
 * 需重考请由管理员走「重新开考」流程或联系技术支持。三道门禁均在写入前抛出，避免脏场次。
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

    public ExamStartFromHistoryCmdExe(
            ExamHistoryGateway examHistoryGateway,
            ExamSessionGateway examSessionGateway,
            ExamTakingSupport support) {
        this.examHistoryGateway = examHistoryGateway;
        this.examSessionGateway = examSessionGateway;
        this.support = support;
    }

    @Transactional
    public ExamSessionDTO execute(String studentToken, String historySessionId) {
        Student student = support.resolveStudent(studentToken);

        ExamHistory history =
                examHistoryGateway
                        .findBySessionId(historySessionId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("试卷不存在: " + historySessionId));

        if (history.getExamPaper() == null || history.getExamPaper().isBlank()) {
            throw new IllegalArgumentException("试卷内容为空");
        }

        // 作废门禁（优先）：VOIDED 属终态，任何学生均不可再开考此卷
        if (history.isVoided()) {
            throw new IllegalStateException("试卷已作废，不可开考");
        }

        // 发布门禁（阶段 1-D）：仅已发布（校对通过）的试卷可开考，未发布 / 校验不通过的卷一律拒绝
        if (!history.isPublished()) {
            throw new IllegalStateException("试卷尚未发布（未通过校对或校验不通过），暂不可开考");
        }

        // 一次开考门禁：同一考生对同一份试卷仅允许开考一次（IN_PROGRESS / SUBMITTED / AI_GRADED 等任意状态均计入）
        if (examSessionGateway.existsByStudentIdAndExamHistoryId(
                student.getId(), history.getId())) {
            logger.warn("考生尝试重复开考同一试卷 [student={}, history={}]", student.getId(), historySessionId);
            throw new IllegalStateException("该试卷仅允许考试一次，您已完成本次考试，如需重考请联系教师");
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
        // 看图题配图注入（Phase 3）：校对页人工绑定的图片存于 kb_exam_question.images_json，
        // 快照由 Markdown 渲染而来不含图片，此处按印刷题号回填 assetKey 数组供学生答题页渲染。
        String withImages =
                support.injectImagesIntoSnapshot(rendered, support.listPaperQuestions(session));
        session.setQuestionsJson(withImages);
        session.setTotalScore(support.sumMaxScore(withImages));
        session.setDurationMinutes(history.getDurationMinutes());
        session.setStatus(STATUS_IN_PROGRESS);
        session.setVoided(false);
        session.setStartTime(LocalDateTime.now());
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());

        examSessionGateway.save(session);
        logger.info(
                "考生开始考试 [student={}, history={}, session={}]",
                student.getId(),
                historySessionId,
                session.getSessionKey());
        return ExamTakingConverter.toSessionDTO(session);
    }
}
