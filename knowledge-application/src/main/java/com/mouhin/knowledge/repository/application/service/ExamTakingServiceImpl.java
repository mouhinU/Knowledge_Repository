package com.mouhin.knowledge.repository.application.service;

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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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

    public ExamTakingServiceImpl(ExamStartFromHistoryCmdExe startFromHistoryCmdExe,
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
    public ExamSessionDTO startFromHistory(String studentToken, String historySessionId) {
        return startFromHistoryCmdExe.execute(studentToken, historySessionId);
    }

    @Override
    public ExamSessionDTO startWithPaper(String studentToken, String examPaper, String answerKey,
                                         String topic, String difficulty) {
        return startWithPaperCmdExe.execute(studentToken, examPaper, answerKey, topic, difficulty);
    }

    @Override
    public Map<String, Object> validateReport(String questionsJson) {
        return validateReportQryExe.execute(questionsJson);
    }

    @Override
    public void saveAnswers(String sessionKey, String studentToken, List<Map<String, String>> answers) {
        saveAnswersCmdExe.execute(sessionKey, studentToken, answers);
    }

    @Override
    public void submitExam(String sessionKey, String studentToken) {
        submitCmdExe.execute(sessionKey, studentToken);
    }

    @Override
    public ExamSessionDTO getSession(String sessionKey, String studentToken) {
        return getSessionQryExe.execute(sessionKey, studentToken);
    }

    @Override
    public List<ExamAnswerDTO> getAnswers(String sessionKey, String studentToken) {
        return getAnswersQryExe.execute(sessionKey, studentToken);
    }

    @Override
    public List<ExamSessionDTO> listMySessions(String studentToken) {
        return mySessionsQryExe.execute(studentToken);
    }

    @Override
    public void updateQuestionsJson(String sessionKey, String studentToken, String questionsJson) {
        updateQuestionsJsonCmdExe.execute(sessionKey, studentToken, questionsJson);
    }

    @Override
    public void updateDuration(String sessionKey, Integer durationMinutes) {
        updateDurationCmdExe.execute(sessionKey, durationMinutes);
    }
}
