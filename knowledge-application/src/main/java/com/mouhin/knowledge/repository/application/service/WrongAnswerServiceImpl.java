package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.application.executor.wronganswer.WrongAnswerPageQryExe;
import com.mouhin.knowledge.repository.application.executor.wronganswer.WrongAnswerStudentOptionsQryExe;
import com.mouhin.knowledge.repository.application.executor.wronganswer.WrongAnswerSummaryQryExe;
import com.mouhin.knowledge.repository.client.api.WrongAnswerServiceI;
import com.mouhin.knowledge.repository.client.dto.StudentOptionVO;
import com.mouhin.knowledge.repository.client.dto.WrongAnswerPageVO;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 错题本应用服务实现（app 层，仅分发到 Executor）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Service
public class WrongAnswerServiceImpl implements WrongAnswerServiceI {

    private final WrongAnswerPageQryExe wrongAnswerPageQryExe;
    private final WrongAnswerSummaryQryExe wrongAnswerSummaryQryExe;
    private final WrongAnswerStudentOptionsQryExe wrongAnswerStudentOptionsQryExe;

    public WrongAnswerServiceImpl(
            WrongAnswerPageQryExe wrongAnswerPageQryExe,
            WrongAnswerSummaryQryExe wrongAnswerSummaryQryExe,
            WrongAnswerStudentOptionsQryExe wrongAnswerStudentOptionsQryExe) {
        this.wrongAnswerPageQryExe = wrongAnswerPageQryExe;
        this.wrongAnswerSummaryQryExe = wrongAnswerSummaryQryExe;
        this.wrongAnswerStudentOptionsQryExe = wrongAnswerStudentOptionsQryExe;
    }

    @Override
    public WrongAnswerPageVO pageWrongAnswers(
            Long studentId, String topic, String questionType, int page, int size) {
        return wrongAnswerPageQryExe.execute(studentId, topic, questionType, page, size);
    }

    @Override
    public String generateAiSummary(Long studentId, String topic, String questionType) {
        return wrongAnswerSummaryQryExe.execute(studentId, topic, questionType);
    }

    @Override
    public List<StudentOptionVO> listStudentOptions() {
        return wrongAnswerStudentOptionsQryExe.execute();
    }
}
