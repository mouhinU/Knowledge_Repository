package com.mouhin.knowledge.repository.application.executor.examtaking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mouhin.knowledge.repository.domain.gateway.ExamAnswerGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 保存 / 更新答题命令执行器（app 层用例，事务边界，支持断点续答）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class ExamSaveAnswersCmdExe {

    private static final Logger logger = LoggerFactory.getLogger(ExamSaveAnswersCmdExe.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    private final ExamAnswerGateway examAnswerGateway;
    private final ExamSessionGateway examSessionGateway;
    private final ExamTakingSupport support;

    public ExamSaveAnswersCmdExe(ExamAnswerGateway examAnswerGateway,
                                 ExamSessionGateway examSessionGateway,
                                 ExamTakingSupport support) {
        this.examAnswerGateway = examAnswerGateway;
        this.examSessionGateway = examSessionGateway;
        this.support = support;
    }

    @Transactional
    public void execute(String sessionKey, String studentToken, List<Map<String, String>> answers) {
        ExamSession session = support.resolveSession(sessionKey, studentToken);

        if (!STATUS_IN_PROGRESS.equals(session.getStatus())) {
            throw new IllegalStateException("考试已结束，无法修改答案");
        }

        // 清除旧答案，重新保存
        examAnswerGateway.deleteBySessionId(session.getId());

        // 解析题目 JSON 获取题目信息
        List<Map<String, Object>> questions = support.parseQuestions(session.getQuestionsJson());

        List<ExamAnswer> answerEntities = new ArrayList<>();
        for (Map<String, String> ans : answers) {
            ExamAnswer answer = new ExamAnswer();
            answer.setSessionId(session.getId());

            int qIndex = Integer.parseInt(ans.get("questionIndex"));
            answer.setQuestionIndex(qIndex);

            // 从题目列表中匹配题目信息
            Map<String, Object> questionMeta = support.findQuestion(questions, qIndex);
            if (questionMeta != null) {
                answer.setQuestionType((String) questionMeta.get("type"));
                answer.setQuestionContent((String) questionMeta.get("content"));
                answer.setMaxScore((Integer) questionMeta.get("maxScore"));
                if (questionMeta.get("options") != null) {
                    try {
                        answer.setOptionsJson(OBJECT_MAPPER.writeValueAsString(questionMeta.get("options")));
                    } catch (Exception ignored) {
                        // 序列化失败忽略
                    }
                }
            } else {
                answer.setQuestionType(ans.getOrDefault("questionType", "SHORT_ANSWER"));
                answer.setQuestionContent(ans.getOrDefault("content", ""));
                answer.setMaxScore(0);
            }

            answer.setStudentAnswer(ans.get("answer"));
            answer.setCreateTime(LocalDateTime.now());
            answer.setUpdateTime(LocalDateTime.now());
            answerEntities.add(answer);
        }

        examAnswerGateway.saveAll(answerEntities);
        session.setUpdateTime(LocalDateTime.now());
        examSessionGateway.update(session);

        logger.debug("保存答题 [session={}, count={}]", sessionKey, answers.size());
    }
}
