package com.mouhin.knowledge.repository.application.executor.examgrading;

import com.mouhin.knowledge.repository.domain.gateway.ExamAnswerGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 完成复核并发布成绩执行器
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
@Slf4j
public class PublishScoreCmdExe {

    private final ExamSessionGateway examSessionGateway;
    private final ExamAnswerGateway examAnswerGateway;

    public PublishScoreCmdExe(
            ExamSessionGateway examSessionGateway, ExamAnswerGateway examAnswerGateway) {
        this.examSessionGateway = examSessionGateway;
        this.examAnswerGateway = examAnswerGateway;
    }

    @Transactional
    public void execute(Long sessionId, String reviewer) {
        ExamSession session =
                examSessionGateway
                        .findById(sessionId)
                        .orElseThrow(() -> new IllegalArgumentException("考试场次不存在: " + sessionId));

        // 阶段 2-D：发布门禁 —— 仅已评分 / 复核中的场次可发布，且待复核题项须全部人工确认
        String status = session.getStatus();
        if (!"AI_GRADED".equals(status) && !"REVIEWED".equals(status)) {
            throw new IllegalStateException("当前场次状态不允许发布成绩：" + status + "（须先完成 AI 评分并复核）");
        }

        List<ExamAnswer> answers = examAnswerGateway.listBySessionId(sessionId);
        long pendingReview =
                answers.stream().filter(PublishScoreCmdExe::requiresHumanConfirmation).count();
        if (pendingReview > 0) {
            throw new IllegalStateException(
                    "尚有 " + pendingReview + " 道主观题 / 缺标准答案 / 未评分题项未经人工确认，无法发布成绩");
        }

        // 重新计算最终成绩（人工复核分数优先）
        int finalScore = answers.stream().mapToInt(ExamAnswer::getEffectiveScore).sum();

        session.markReviewed(finalScore);
        session.markPublished();
        session.setUpdateTime(LocalDateTime.now());
        examSessionGateway.update(session);

        log.info("成绩已发布 [session={}, finalScore={}, reviewer={}]", sessionId, finalScore, reviewer);
    }

    /**
     * 判断某题在发布前是否必须经人工确认。
     *
     * <p>仅当"机器无法自信评分"时才拦门禁：主观题、缺少标准答案的客观题、或尚未评分的题项。 客观题若标准答案存在，则自动比对结果（无论对错）都是确定的，无需人工复核即可发布，
     * 以免学生仅因答错选择题就被迫逐题人工确认。已由人工复核过（{@code reviewScore!=null}）的题项放行。
     *
     * @param answer 答题记录
     * @return 需要人工确认返回 {@code true}
     */
    private static boolean requiresHumanConfirmation(ExamAnswer answer) {
        if (answer.getReviewScore() != null) {
            return false;
        }
        if (!answer.isObjective()) {
            return true;
        }
        if (answer.getAiScore() == null) {
            return true;
        }
        String correctAnswer = answer.getCorrectAnswer();
        return correctAnswer == null || correctAnswer.isBlank();
    }
}
