package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;

import java.util.List;
import java.util.Optional;

/**
 * 答题记录仓储接口
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
public interface ExamAnswerGateway {

    void save(ExamAnswer answer);

    void update(ExamAnswer answer);

    /**
     * 清除某题的人工复核覆盖（review_score / review_feedback / reviewed_by / review_time 置 NULL）。
     * <p>
     * 复核人将分数改回 {@code null} 表示"放弃人工改分、回落到 AI 评分"。但 {@link #update}
     * 走 MyBatis-Plus {@code updateById}，默认 {@code FieldStrategy=NOT_NULL} 会跳过所有 null 字段，
     * 无法把这几列真正清成 NULL。故此处用 {@code LambdaUpdateWrapper.set(col, null)} 显式清空。
     * </p>
     *
     * @param answerId 答题记录 ID
     */
    void clearReviewOverride(Long answerId);

    /**
     * 清除某题的 AI 评分 trace（ai_input / ai_raw_output 置 NULL）。
     * <p>
     * 重跑评分时，若本轮某题（如客观题）不再产生 trace，{@link #update} 走 {@code updateById}
     * 因默认 {@code FieldStrategy=NOT_NULL} 会跳过 null 的 ai_input / ai_raw_output，
     * 导致上一轮残留的旧 trace 无法被清掉。故用 {@code LambdaUpdateWrapper.set(col, null)} 显式清空。
     * </p>
     *
     * @param answerId 答题记录 ID
     */
    void clearAiTrace(Long answerId);

    Optional<ExamAnswer> findById(Long id);

    /**
     * 批量保存答题记录
     */
    void saveAll(List<ExamAnswer> answers);

    /**
     * 查询某场考试的所有答题记录（按题号排序）
     */
    List<ExamAnswer> listBySessionId(Long sessionId);

    /**
     * 查询某场考试中需要复核的题目
     */
    List<ExamAnswer> listNeedsReview(Long sessionId);

    /**
     * 统计某场考试中需要复核的题目数量
     */
    long countNeedsReview(Long sessionId);

    /**
     * 删除某场考试的所有答题记录
     */
    void deleteBySessionId(Long sessionId);

    /**
     * 查询指定场次列表中的所有答题记录（按场次 ID、题号排序）
     *
     * @param sessionIds 场次 ID 列表
     * @return 答题记录列表
     */
    List<ExamAnswer> listBySessionIds(List<Long> sessionIds);
}
