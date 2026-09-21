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
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
public interface ExamTakingServiceI {

    SingleResponse<ExamSessionDTO> startFromHistory(String studentToken, String historySessionId);

    SingleResponse<ExamSessionDTO> startWithPaper(
            String studentToken,
            String examPaper,
            String answerKey,
            String topic,
            String difficulty);

    SingleResponse<Map<String, Object>> validateReport(String questionsJson);

    Response saveAnswers(String sessionKey, String studentToken, List<Map<String, String>> answers);

    Response submitExam(String sessionKey, String studentToken);

    SingleResponse<ExamSessionDTO> getSession(String sessionKey, String studentToken);

    MultiResponse<ExamAnswerDTO> getAnswers(String sessionKey, String studentToken);

    MultiResponse<ExamSessionDTO> listMySessions(String studentToken);

    Response updateQuestionsJson(String sessionKey, String studentToken, String questionsJson);

    Response updateDuration(String sessionKey, Integer durationMinutes);
}
