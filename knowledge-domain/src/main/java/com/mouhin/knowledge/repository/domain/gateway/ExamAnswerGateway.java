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
