package com.mouhin.knowledge.repository.application.service;

import com.alibaba.cola.dto.MultiResponse;
import com.alibaba.cola.dto.Response;
import com.alibaba.cola.dto.SingleResponse;
import com.mouhin.knowledge.repository.application.executor.examtaking.ExamGetAnswersQryExe;
import com.mouhin.knowledge.repository.application.executor.examtaking.ExamGetSessionQryExe;
import com.mouhin.knowledge.repository.application.executor.examtaking.ExamMySessionsQryExe;
import com.mouhin.knowledge.repository.application.executor.examtaking.ExamSaveAnswersCmdExe;
import com.mouhin.knowledge.repository.application.executor.examtaking.ExamStartFromHistoryCmdExe;
import com.mouhin.knowledge.repository.application.executor.examtaking.ExamStartWithPaperCmdExe;
import com.mouhin.knowledge.repository.application.executor.examtaking.ExamSubmitCmdExe;
import com.mouhin.knowledge.repository.application.executor.examtaking.ExamUpdateDurationCmdExe;
import com.mouhin.knowledge.repository.application.executor.examtaking.ExamUpdateQuestionsJsonCmdExe;
import com.mouhin.knowledge.repository.application.executor.examtaking.ExamValidateReportQryExe;
import com.mouhin.knowledge.repository.client.api.ExamTakingServiceI;
import com.mouhin.knowledge.repository.client.dto.ExamAnswerDTO;
import com.mouhin.knowledge.repository.client.dto.ExamSessionDTO;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 在线做题应用服务实现（app 层，仅分发到执行器）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Service
public class ExamTakingServiceImpl implements ExamTakingServiceI {

    private final ExamStartFromHistoryCmdExe startFromHistoryCmdExe;
    private final ExamStartWithPaperCmdExe startWithPaperCmdExe;
    private final ExamValidateReportQryExe validateReportQryExe;
    private final ExamSaveAnswersCmdExe saveAnswersCmdExe;
    private final ExamSubmitCmdExe submitCmdExe;
    private final ExamGetSessionQryExe getSessionQryExe;
    private final ExamGetAnswersQryExe getAnswersQryExe;
    private final ExamMySessionsQryExe mySessionsQryExe;
    private final ExamUpdateQuestionsJsonCmdExe updateQuestionsJsonCmdExe;
    private final ExamUpdateDurationCmdExe updateDurationCmdExe;

    public ExamTakingServiceImpl(
            ExamStartFromHistoryCmdExe startFromHistoryCmdExe,
            ExamStartWithPaperCmdExe startWithPaperCmdExe,
            ExamValidateReportQryExe validateReportQryExe,
            ExamSaveAnswersCmdExe saveAnswersCmdExe,
            ExamSubmitCmdExe submitCmdExe,
            ExamGetSessionQryExe getSessionQryExe,
            ExamGetAnswersQryExe getAnswersQryExe,
            ExamMySessionsQryExe mySessionsQryExe,
            ExamUpdateQuestionsJsonCmdExe updateQuestionsJsonCmdExe,
            ExamUpdateDurationCmdExe updateDurationCmdExe) {
        this.startFromHistoryCmdExe = startFromHistoryCmdExe;
        this.startWithPaperCmdExe = startWithPaperCmdExe;
        this.validateReportQryExe = validateReportQryExe;
        this.saveAnswersCmdExe = saveAnswersCmdExe;
        this.submitCmdExe = submitCmdExe;
        this.getSessionQryExe = getSessionQryExe;
        this.getAnswersQryExe = getAnswersQryExe;
        this.mySessionsQryExe = mySessionsQryExe;
        this.updateQuestionsJsonCmdExe = updateQuestionsJsonCmdExe;
        this.updateDurationCmdExe = updateDurationCmdExe;
    }

    @Override
    public SingleResponse<ExamSessionDTO> startFromHistory(
            String studentToken, String historySessionId) {
        return SingleResponse.of(startFromHistoryCmdExe.execute(studentToken, historySessionId));
    }

    @Override
    public SingleResponse<ExamSessionDTO> startWithPaper(
            String studentToken,
            String examPaper,
            String answerKey,
            String topic,
            String difficulty) {
        return SingleResponse.of(
                startWithPaperCmdExe.execute(
                        studentToken, examPaper, answerKey, topic, difficulty));
    }

    @Override
    public SingleResponse<Map<String, Object>> validateReport(String questionsJson) {
        return SingleResponse.of(validateReportQryExe.execute(questionsJson));
    }

    @Override
    public Response saveAnswers(
            String sessionKey, String studentToken, List<Map<String, String>> answers) {
        saveAnswersCmdExe.execute(sessionKey, studentToken, answers);
        return Response.buildSuccess();
    }

    @Override
    public Response submitExam(String sessionKey, String studentToken) {
        submitCmdExe.execute(sessionKey, studentToken);
        return Response.buildSuccess();
    }

    @Override
    public SingleResponse<ExamSessionDTO> getSession(String sessionKey, String studentToken) {
        return SingleResponse.of(getSessionQryExe.execute(sessionKey, studentToken));
    }

    @Override
    public MultiResponse<ExamAnswerDTO> getAnswers(String sessionKey, String studentToken) {
        return MultiResponse.of(getAnswersQryExe.execute(sessionKey, studentToken));
    }

    @Override
    public MultiResponse<ExamSessionDTO> listMySessions(String studentToken) {
        return MultiResponse.of(mySessionsQryExe.execute(studentToken));
    }

    @Override
    public Response updateQuestionsJson(
            String sessionKey, String studentToken, String questionsJson) {
        updateQuestionsJsonCmdExe.execute(sessionKey, studentToken, questionsJson);
        return Response.buildSuccess();
    }

    @Override
    public Response updateDuration(String sessionKey, Integer durationMinutes) {
        updateDurationCmdExe.execute(sessionKey, durationMinutes);
        return Response.buildSuccess();
    }
}
