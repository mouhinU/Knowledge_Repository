package com.mouhin.knowledge.repository.client.api;

import com.alibaba.cola.dto.MultiResponse;
import com.alibaba.cola.dto.Response;
import com.alibaba.cola.dto.SingleResponse;
import com.mouhin.knowledge.repository.client.dto.ExamAnswerDTO;
import com.mouhin.knowledge.repository.client.dto.ExamSessionDTO;

/**
 * 考试评分应用服务契约（client 层）
 *
 * <p>仅承载传输无关的读 / 写用例：待评分与待复核列表、场次与答题详情查询、 同步触发评分、人工复核与成绩发布。返回值统一采用 COLA 契约类型 （{@link
 * SingleResponse} / {@link MultiResponse} / {@link Response}），适配层负责还原为前端 JSON 形状； 校验失败仍以 {@link
 * IllegalArgumentException} / {@link IllegalStateException} 抛出。
 *
 * <p>依赖领域回调 {@code ExamGradingProgressCallback} 的异步 / SSE 评分路径 （{@code gradeExamAsync} / {@code
 * triggerGradingAsync}）刻意不纳入本契约， 由 app 层执行器承载并供适配层直接调用，以保证 client 层不引用 domain 类型。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
public interface ExamGradingServiceI {

    /**
     * 查询待评分的考试场次列表。
     *
     * @param limit 返回的最大记录条数
     * @param offset 起始偏移量
     * @return 待评分的考试场次 DTO 列表
     */
    MultiResponse<ExamSessionDTO> listPendingGradingSessions(int limit, int offset);

    /**
     * 统计待评分的考试场次数量。
     *
     * @return 待评分场次总数
     */
    SingleResponse<Long> countPendingGrading();

    /**
     * 获取所有待评分场次的 ID 列表。
     *
     * @return 待评分的考试场次 ID 列表
     */
    MultiResponse<Long> listPendingGradingSessionIds();

    /**
     * 查询待人工复核的考试场次列表。
     *
     * @param limit 返回的最大记录条数
     * @param offset 起始偏移量
     * @return 待复核的考试场次 DTO 列表
     */
    MultiResponse<ExamSessionDTO> listPendingReview(int limit, int offset);

    /**
     * 统计待人工复核的考试场次数量。
     *
     * @return 待复核场次总数
     */
    SingleResponse<Long> countPendingReview();

    /**
     * 根据场次 ID 查询考试场次详情。
     *
     * @param sessionId 考试场次唯一标识
     * @return 考试场次 DTO，未命中时 {@code data} 为 null
     */
    SingleResponse<ExamSessionDTO> getSessionById(Long sessionId);

    /**
     * 查询指定场次的全部答题记录（含评分信息）。
     *
     * @param sessionId 考试场次唯一标识
     * @return 该场次下所有答题 DTO 列表（含评分与反馈）
     */
    MultiResponse<ExamAnswerDTO> listAnswersWithGrading(Long sessionId);

    /**
     * 同步触发指定场次的评分流程。
     *
     * @param sessionId 考试场次唯一标识
     * @return 操作结果响应
     * @throws IllegalArgumentException 场次不存在
     * @throws IllegalStateException 场次状态不允许评分
     */
    Response triggerGrading(Long sessionId);

    /**
     * 人工复核单道答题：覆盖 AI 评分并填写反馈。
     *
     * @param answerId 答题记录唯一标识
     * @param reviewScore 复核分数
     * @param reviewFeedback 复核反馈意见
     * @param reviewer 复核人标识
     * @return 操作结果响应
     * @throws IllegalArgumentException 答题记录不存在或参数非法
     */
    Response reviewAnswer(
            Long answerId, Integer reviewScore, String reviewFeedback, String reviewer);

    /**
     * 发布考试成绩（将场次状态变更为已发布，对学生可见）。
     *
     * @param sessionId 考试场次唯一标识
     * @param reviewer 发布人标识
     * @return 操作结果响应
     * @throws IllegalArgumentException 场次不存在
     * @throws IllegalStateException 场次状态不允许发布
     */
    Response publishScore(Long sessionId, String reviewer);
}
