package com.mouhin.knowledge.repository.client.api;

import com.alibaba.cola.dto.MultiResponse;
import com.alibaba.cola.dto.Response;
import com.alibaba.cola.dto.SingleResponse;
import com.mouhin.knowledge.repository.client.dto.ExamAnswerDTO;
import com.mouhin.knowledge.repository.client.dto.ExamSessionDTO;
import java.util.List;
import java.util.Map;

/**
 * 在线做题应用服务契约（client 层）
 *
 * <p>返回值统一采用 COLA 契约类型（{@link SingleResponse} / {@link MultiResponse} / {@link Response}），
 * 适配层负责将其还原为前端所需的 JSON 形状。开考 / 交卷等校验失败仍以 {@link IllegalArgumentException} / {@link
 * IllegalStateException} 抛出，由适配层转换为 400 / 409 响应。 {@link #validateReport} 的 data 为可直接序列化的 Map（等价于原
 * {@code PaperValidationReport#toMap}）， 避免 client 层引用 app 内部类型。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
public interface ExamTakingServiceI {

    /**
     * 基于历史出卷记录重新开考。
     *
     * @param studentToken 学生访问令牌
     * @param historySessionId 历史出卷会话 ID
     * @return 新创建的考试场次 DTO
     * @throws IllegalArgumentException 历史记录不存在或令牌无效
     */
    SingleResponse<ExamSessionDTO> startFromHistory(String studentToken, String historySessionId);

    /**
     * 使用指定试卷开始考试。
     *
     * @param studentToken 学生访问令牌
     * @param examPaper 试卷内容（JSON 格式）
     * @param answerKey 参考答案（JSON 格式）
     * @param topic 试卷主题
     * @param difficulty 难度等级
     * @return 新创建的考试场次 DTO
     * @throws IllegalArgumentException 参数非法或令牌无效
     */
    SingleResponse<ExamSessionDTO> startWithPaper(
            String studentToken,
            String examPaper,
            String answerKey,
            String topic,
            String difficulty);

    /**
     * 校验试卷内容的合法性（题目完整性、格式正确性等）。
     *
     * @param questionsJson 试卷题目 JSON 字符串
     * @return 校验报告，包含校验结果与问题明细
     */
    SingleResponse<Map<String, Object>> validateReport(String questionsJson);

    /**
     * 暂存答题答案（考试中途保存）。
     *
     * @param sessionKey 考试场次唯一标识
     * @param studentToken 学生访问令牌
     * @param answers 答题列表，每题包含题目标识与所选答案
     * @return 操作结果响应
     * @throws IllegalArgumentException 场次不存在或令牌无效
     */
    Response saveAnswers(String sessionKey, String studentToken, List<Map<String, String>> answers);

    /**
     * 提交试卷（交卷，触发评分流程）。
     *
     * @param sessionKey 考试场次唯一标识
     * @param studentToken 学生访问令牌
     * @return 操作结果响应
     * @throws IllegalArgumentException 场次不存在或令牌无效
     * @throws IllegalStateException 场次状态不允许提交
     */
    Response submitExam(String sessionKey, String studentToken);

    /**
     * 查询考试场次详情。
     *
     * @param sessionKey 考试场次唯一标识
     * @param studentToken 学生访问令牌
     * @return 考试场次 DTO，未命中时 {@code data} 为 null
     */
    SingleResponse<ExamSessionDTO> getSession(String sessionKey, String studentToken);

    /**
     * 查询指定场次的全部答题记录。
     *
     * @param sessionKey 考试场次唯一标识
     * @param studentToken 学生访问令牌
     * @return 该场次下所有答题 DTO 列表
     */
    MultiResponse<ExamAnswerDTO> getAnswers(String sessionKey, String studentToken);

    /**
     * 查询当前学生的全部考试场次列表。
     *
     * @param studentToken 学生访问令牌
     * @return 该学生的考试场次 DTO 列表
     */
    MultiResponse<ExamSessionDTO> listMySessions(String studentToken);

    /**
     * 更新场次中的试卷题目 JSON（用于考试中途修正试卷）。
     *
     * @param sessionKey 考试场次唯一标识
     * @param studentToken 学生访问令牌
     * @param questionsJson 更新后的试卷题目 JSON 字符串
     * @return 操作结果响应
     * @throws IllegalArgumentException 场次不存在或令牌无效
     */
    Response updateQuestionsJson(String sessionKey, String studentToken, String questionsJson);

    /**
     * 更新考试时长。
     *
     * @param sessionKey 考试场次唯一标识
     * @param durationMinutes 考试时长（分钟）
     * @return 操作结果响应
     * @throws IllegalArgumentException 场次不存在或时长非法
     */
    Response updateDuration(String sessionKey, Integer durationMinutes);
}
