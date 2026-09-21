package com.mouhin.knowledge.repository.application.executor.examreview;

import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 作废试卷命令执行器（app 层用例，事务边界）
 *
 * <p>教师对某份 AI 试卷点击「作废」时调用：
 *
 * <ol>
 *   <li>把 {@code kb_exam_history.status} 由任意非 VOIDED 状态置为 {@link ExamHistory#STATUS_VOIDED}，
 *       并记录作废人与时间（复用 {@code reviewed_by / reviewed_time} 审计列）；
 *   <li>级联把该试卷下所有考试场次 {@code kb_exam_session.voided} 置为 true，
 *       前端据此显示「试卷已作废」徽标；场次仍可显示与查阅，不影响答题记录与评分轨迹。
 * </ol>
 *
 * 若目标卷已是 VOIDED 则幂等直接返回。作废后学生不可再开考此卷，可用列表亦会自动移除（{@code listPublished} 仅返回 PUBLISHED）。
 *
 * @author Knowledge-Repository
 * @date 2026-09-20
 */
@Component
public class VoidPaperCmdExe {

    private static final Logger logger = LoggerFactory.getLogger(VoidPaperCmdExe.class);

    private final ExamHistoryGateway examHistoryGateway;
    private final ExamSessionGateway examSessionGateway;

    public VoidPaperCmdExe(
            ExamHistoryGateway examHistoryGateway, ExamSessionGateway examSessionGateway) {
        this.examHistoryGateway = examHistoryGateway;
        this.examSessionGateway = examSessionGateway;
    }

    /**
     * 作废试卷并级联标注其下所有考试场次。
     *
     * @param sessionId 出卷会话标识（{@code kb_exam_history.session_id}）
     * @param operator 作废操作人（管理员 / 出题人）
     * @return 本次级联标注的场次数量
     */
    @Transactional
    public int execute(String sessionId, String operator) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("试卷标识不能为空");
        }
        ExamHistory history =
                examHistoryGateway
                        .findBySessionId(sessionId)
                        .orElseThrow(() -> new IllegalArgumentException("试卷不存在: " + sessionId));
        if (history.isVoided()) {
            logger.info("试卷已处于作废态，幂等返回 [session={}]", sessionId);
            return examSessionGateway.markVoidedByExamHistoryId(history.getId(), true);
        }
        String actor = (operator != null && !operator.isBlank()) ? operator : "admin";
        history.markVoided(actor);
        history.setUpdateTime(LocalDateTime.now());
        examHistoryGateway.update(history);
        int cascaded = examSessionGateway.markVoidedByExamHistoryId(history.getId(), true);
        logger.info(
                "试卷已作废并级联场次 [session={}, operator={}, cascadedSessions={}]",
                sessionId,
                actor,
                cascaded);
        return cascaded;
    }
}
