package com.mouhin.knowledge.repository.application.executor.examtaking;

import com.mouhin.knowledge.repository.application.converter.ExamTakingConverter;
import com.mouhin.knowledge.repository.application.util.ExamPaperParser;
import com.mouhin.knowledge.repository.client.dto.ExamSessionDTO;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * 考试场次详情查询执行器（app 层用例）
 *
 * <p>保留原有「读时重解析」副作用：当选择题选项解析异常，或存在考试方案但旧 questionsJson 缺少 sectionLabel
 * 需要按方案补齐时，重新解析并回写持久层，再返回最新的场次视图。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
public class ExamGetSessionQryExe {

    private final ExamTakingSupport support;

    public ExamGetSessionQryExe(ExamTakingSupport support) {
        this.support = support;
    }

    public ExamSessionDTO execute(String sessionKey, String studentToken) {
        ExamSession session = support.resolveSession(sessionKey, studentToken);

        String questionsJson = session.getQuestionsJson();
        String planJson = session.getExamPlan();
        boolean planUpgradeNeeded =
                planJson != null
                        && !planJson.isBlank()
                        && (questionsJson == null || !questionsJson.contains("sectionLabel"));
        if (session.getExamPaper() != null
                && ((questionsJson != null && support.needsReparse(questionsJson))
                        || planUpgradeNeeded)) {
            questionsJson = ExamPaperParser.parseToJson(session.getExamPaper(), planJson);
            session.setQuestionsJson(questionsJson);
            session.setUpdateTime(LocalDateTime.now());
            support.updateSession(session);
        }

        return ExamTakingConverter.toSessionDTO(session);
    }
}
