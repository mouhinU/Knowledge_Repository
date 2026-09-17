package com.mouhin.knowledge.repository.client.api;

import com.mouhin.knowledge.repository.client.dto.StudentOptionVO;
import com.mouhin.knowledge.repository.client.dto.WrongAnswerPageVO;

import java.util.List;

/**
 * 错题本应用服务契约（client 层）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
public interface WrongAnswerServiceI {

    WrongAnswerPageVO pageWrongAnswers(Long studentId, String topic, String questionType, int page, int size);

    String generateAiSummary(Long studentId, String topic, String questionType);

    List<StudentOptionVO> listStudentOptions();
}
