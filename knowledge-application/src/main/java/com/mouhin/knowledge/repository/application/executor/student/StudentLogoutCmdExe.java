package com.mouhin.knowledge.repository.application.executor.student;

import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
        studentGateway.findBySessionToken(token).ifPresent(student -> {
            student.setSessionToken(null);
            student.setTokenExpiry(null);
            student.setUpdateTime(LocalDateTime.now());
            studentGateway.update(student);
            logger.info("考生退出登录: id={}", student.getId());
        });
    }
}
