package com.mouhin.knowledge.repository.application.executor.student;

import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 考生退出登录命令执行器（app 层用例，事务边界）
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
@Slf4j
public class StudentLogoutCmdExe {

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
                            log.info("考生退出登录: id={}", student.getId());
                        });
    }
}
