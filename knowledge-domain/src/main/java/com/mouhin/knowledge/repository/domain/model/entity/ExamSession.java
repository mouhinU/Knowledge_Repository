package com.mouhin.knowledge.repository.domain.model.entity;

import com.mouhin.knowledge.repository.domain.constant.ExamSessionStatuses;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * 考试场次实体（学生的一次答题记录）
 *
 * @author mouhinU
 * @date 2026-09-15
 */
@Getter
@Setter
public class ExamSession {

    private Long id;

    /** 场次唯一标识（UUID） */
    private String sessionKey;

    /** 考生 ID */
    private Long studentId;

    /** 关联的出卷历史 ID（可为空，表示即时生成） */
    private Long examHistoryId;

    /** 考试主题 */
    private String topic;

    /** 难度：EASY / MEDIUM / HARD */
    private String difficulty;

    /** 考试时长（分钟），为空表示不限时 */
    private Integer durationMinutes;

    /** 试卷原文（Markdown） */
    private String examPaper;

    /** 参考答案 */
    private String answerKey;

    /** 结构化题目 JSON */
    private String questionsJson;

    /** 题型分布方案（ExamPlan JSON，从出卷历史继承），考试端据此按方案渲染题型与分值 */
    private String examPlan;

    /** 总分 */
    private Integer totalScore;

    /** AI 评分 */
    private Integer aiScore;

    /** 最终成绩（人工复核后） */
    private Integer finalScore;

    /** 状态：IN_PROGRESS / SUBMITTED / AI_GRADED / REVIEWED / PUBLISHED */
    private String status;

    /**
     * 试卷是否已作废。true 表示该场次对应的 AI 试卷已被教师作废， 场次仍可正常显示与查阅，但会标注「试卷已作废」并不再计入有效成绩场景。
     *
     * <p>POJO 布尔属性不加 {@code is} 前缀（遵循编码规约 1.3）。
     */
    private boolean voided;

    /** 开始答题时间 */
    private LocalDateTime startTime;

    /** 交卷时间 */
    private LocalDateTime submitTime;

    /** 评分完成时间 */
    private LocalDateTime gradeTime;

    /** 成绩发布时间 */
    private LocalDateTime publishTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== 业务方法 ====================

    /** 标记为已提交 */
    public void submit() {
        this.status = ExamSessionStatuses.SUBMITTED;
        this.submitTime = LocalDateTime.now();
    }

    /**
     * 标记为评分中（并发认领态）
     *
     * <p>由 {@code SUBMITTED} 经 CAS 抢占进入，评分完成后转 {@code AI_GRADED}； 若评分进程崩溃停留在本态，由定时任务按超时阈值回退至 {@code
     * SUBMITTED} 重新调度。
     */
    public void markGrading() {
        this.status = ExamSessionStatuses.GRADING;
    }

    /** 标记为 AI 评分完成 */
    public void markAiGraded(int aiScore) {
        this.status = ExamSessionStatuses.AI_GRADED;
        this.aiScore = aiScore;
        this.gradeTime = LocalDateTime.now();
    }

    /** 标记为人工复核完成 */
    public void markReviewed(int finalScore) {
        this.status = ExamSessionStatuses.REVIEWED;
        this.finalScore = finalScore;
    }

    /** 标记为已发布 */
    public void markPublished() {
        this.status = ExamSessionStatuses.PUBLISHED;
        this.publishTime = LocalDateTime.now();
    }

    // ==================== equals / hashCode ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExamSession that = (ExamSession) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
