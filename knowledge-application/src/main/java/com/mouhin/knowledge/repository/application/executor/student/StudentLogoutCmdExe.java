package com.mouhin.knowledge.repository.application.executor.student;

import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 考生退出登录命令执行器（app 层用例，事务边界）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class StudentLogoutCmdExe {

    private static final Logger logger = LoggerFactory.getLogger(StudentLogoutCmdExe.class);

    private final StudentGateway studentGateway;

    public StudentLogoutCmdExe(StudentGateway studentGateway) {
        this.studentGateway = studentGateway;
    }

    @Transactional
    public void execute(String token) {
        studentGateway
                .findBySessionToken(token)
                .ifPresent(
                        student -> {
                            // 令牌置空须走专用方法：通用 update 在 NOT_NULL 策略下不会把 session_token 写回 null
                            studentGateway.clearSessionToken(student.getId());
                            logger.info("考生退出登录: id={}", student.getId());
                        });
    }
}
