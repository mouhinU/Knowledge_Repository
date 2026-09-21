package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import java.util.List;
import java.util.Optional;

/**
 * 结构化题目仓储接口（出卷即切分产物的持久化契约）
 *
 * <p>出入参均为领域对象，禁止出现 DO / DTO。
 *
 * @author Knowledge-Repository
 * @date 2026-09-18
 */
public interface ExamQuestionGateway {

    /**
     * 按试卷标识查询全部题目（按印刷题号升序）
     *
     * @param sessionKey 试卷标识
     * @return 结构化题目列表
     */
    List<ExamQuestion> listBySessionKey(String sessionKey);

    /**
     * 按试卷标识 + 印刷题号查询单题
     *
     * @param sessionKey 试卷标识
     * @param questionNumber 印刷题号
     * @return 结构化题目
     */
    Optional<ExamQuestion> findBySessionKeyAndNumber(String sessionKey, Integer questionNumber);

    /**
     * 批量插入题目
     *
     * @param questions 题目列表
     */
    void batchInsert(List<ExamQuestion> questions);

    /**
     * 更新标准答案（校对视图就地编辑后回写）
     *
     * @param sessionKey 试卷标识
     * @param questionNumber 印刷题号
     * @param correctAnswer 标准答案
     */
    void updateCorrectAnswer(String sessionKey, Integer questionNumber, String correctAnswer);

    /**
     * 校对就地编辑：更新某题的标准答案、解析与分值（按试卷标识 + 印刷题号定位）。
     *
     * @param sessionKey 试卷标识
     * @param questionNumber 印刷题号
     * @param correctAnswer 标准答案
     * @param analysis 解析 / 说明
     * @param maxScore 本题满分（可为 null 表示不修改）
     */
    void updateCorrection(
            String sessionKey,
            Integer questionNumber,
            String correctAnswer,
            String analysis,
            Integer maxScore);

    /**
     * 校对页绑定配图：就地更新某题的配图 assetKey 有序数组 JSON（按试卷标识 + 印刷题号定位）。
     *
     * @param sessionKey 试卷标识
     * @param questionNumber 印刷题号
     * @param imagesJson 配图 JSON（如 {@code ["k1","k2"]}）；null / 空串表示清除绑定
     */
    void updateImagesJson(String sessionKey, Integer questionNumber, String imagesJson);

    /**
     * 删除某份试卷的全部题目（重切分 / 幂等回灌前清理）
     *
     * @param sessionKey 试卷标识
     */
    void deleteBySessionKey(String sessionKey);
}
