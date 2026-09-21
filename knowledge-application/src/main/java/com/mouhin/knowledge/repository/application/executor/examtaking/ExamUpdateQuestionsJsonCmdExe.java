package com.mouhin.knowledge.repository.application.executor.examtaking;

import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 更新场次题目 JSON 命令执行器（app 层用例，事务边界，用于修复旧数据选项解析）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class ExamUpdateQuestionsJsonCmdExe {

    private static final Logger logger =
            LoggerFactory.getLogger(ExamUpdateQuestionsJsonCmdExe.class);

    private final ExamSessionGateway examSessionGateway;
    private final ExamTakingSupport support;

    public ExamUpdateQuestionsJsonCmdExe(
            ExamSessionGateway examSessionGateway, ExamTakingSupport support) {
        this.examSessionGateway = examSessionGateway;
        this.support = support;
    }

    @Transactional
    public void execute(String sessionKey, String studentToken, String questionsJson) {
        ExamSession session = support.resolveSession(sessionKey, studentToken);
        session.setQuestionsJson(questionsJson);
        session.setUpdateTime(LocalDateTime.now());
        examSessionGateway.update(session);
        logger.info("已重新解析 questionsJson [session={}]", sessionKey);
    }
}
