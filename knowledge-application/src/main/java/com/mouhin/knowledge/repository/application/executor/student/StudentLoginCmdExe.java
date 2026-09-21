package com.mouhin.knowledge.repository.application.executor.student;

import com.mouhin.knowledge.repository.client.dto.StudentLoginCmd;
import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 考生登录命令执行器（app 层用例，事务边界）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
@Slf4j
public class StudentLoginCmdExe {

    /** 令牌有效期：24 小时 */
    private static final int TOKEN_VALIDITY_HOURS = 24;

    /** 有效账号状态 */
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final StudentGateway studentGateway;
    private final BCryptPasswordEncoder passwordEncoder;

    public StudentLoginCmdExe(
            StudentGateway studentGateway, BCryptPasswordEncoder passwordEncoder) {
        this.studentGateway = studentGateway;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public String execute(StudentLoginCmd cmd) {
        Student student =
                studentGateway
                        .findByUsername(cmd.getUsername())
                        .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));

        if (!STATUS_ACTIVE.equals(student.getStatus())) {
            throw new IllegalArgumentException("账号已被禁用");
        }

        if (!passwordEncoder.matches(cmd.getPassword(), student.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        student.setSessionToken(token);
        student.setTokenExpiry(LocalDateTime.now().plusHours(TOKEN_VALIDITY_HOURS));
        student.setUpdateTime(LocalDateTime.now());
        studentGateway.update(student);

        log.info("考生登录成功: username='{}', id={}", cmd.getUsername(), student.getId());
        return token;
    }
}
