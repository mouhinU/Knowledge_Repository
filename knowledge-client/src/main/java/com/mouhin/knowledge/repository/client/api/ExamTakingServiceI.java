package com.mouhin.knowledge.repository.client.api;

import com.mouhin.knowledge.repository.client.dto.ExamAnswerDTO;
import com.mouhin.knowledge.repository.client.dto.ExamSessionDTO;

import java.util.List;
import java.util.Map;

/**
 * 在线做题应用服务契约（client 层）
 *
 * <p>开考 / 交卷等校验失败以 {@link IllegalArgumentException} / {@link IllegalStateException}
 * 抛出，由适配层转换为 400 响应。{@link #validateReport} 返回可直接序列化的 Map（等价于原
 * {@code PaperValidationReport#toMap}），避免 client 层引用 app 内部类型。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
public interface ExamTakingServiceI {

    ExamSessionDTO startFromHistory(String studentToken, String historySessionId);

    ExamSessionDTO startWithPaper(String studentToken, String examPaper, String answerKey,
                                  String topic, String difficulty);

    Map<String, Object> validateReport(String questionsJson);

    void saveAnswers(String sessionKey, String studentToken, List<Map<String, String>> answers);

    void submitExam(String sessionKey, String studentToken);

    ExamSessionDTO getSession(String sessionKey, String studentToken);

    List<ExamAnswerDTO> getAnswers(String sessionKey, String studentToken);

    List<ExamSessionDTO> listMySessions(String studentToken);

    void updateQuestionsJson(String sessionKey, String studentToken, String questionsJson);

    void updateDuration(String sessionKey, Integer durationMinutes);
}
