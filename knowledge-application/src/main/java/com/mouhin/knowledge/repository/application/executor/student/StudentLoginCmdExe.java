package com.mouhin.knowledge.repository.application.executor.student;

import com.mouhin.knowledge.repository.client.dto.StudentLoginCmd;
import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import com.mouhin.knowledge.repository.domain.gateway.StudentSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import com.mouhin.knowledge.repository.domain.model.entity.StudentSession;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 考生登录命令执行器（app 层用例，事务边界）
 *
 * <p>P0-T5：每次登录向 {@code kb_student_session} 落一行独立会话，允许多端并发在线而不互踢； 同步保留 {@code
 * kb_student.session_token} 的"最后 token"写入，作为历史消费方的兼容回退（1 个版本后下线）。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
@Slf4j
public class StudentLoginCmdExe {

    /** 令牌有效期：24 小时 */
    private static final int TOKEN_VALIDITY_HOURS = 24;

    private final StudentGateway studentGateway;
    private final StudentSessionGateway studentSessionGateway;
    private final BCryptPasswordEncoder passwordEncoder;

    public StudentLoginCmdExe(
            StudentGateway studentGateway,
            StudentSessionGateway studentSessionGateway,
            BCryptPasswordEncoder passwordEncoder) {
        this.studentGateway = studentGateway;
        this.studentSessionGateway = studentSessionGateway;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public String execute(StudentLoginCmd cmd) {
        Student student =
                studentGateway
                        .findByUsername(cmd.getUsername())
                        .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));

        if (!User.STATUS_ACTIVE.equals(student.getStatus())) {
            throw new IllegalArgumentException("账号已被禁用");
        }

        if (!passwordEncoder.matches(cmd.getPassword(), student.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = now.plusHours(TOKEN_VALIDITY_HOURS);

        // P0-T5：新增会话表落一行——多端并发登录互不影响
        StudentSession session = new StudentSession();
        session.setSessionToken(token);
        session.setStudentId(student.getId());
        session.setExpiresAt(expiry);
        session.setCreateTime(now);
        session.setUpdateTime(now);
        studentSessionGateway.save(session);

        // 兼容回退：legacy 字段仍写入"最后 token"，Phase 1 后续版本随消费方全部切换后下线
        student.setSessionToken(token);
        student.setTokenExpiry(expiry);
        student.setUpdateTime(now);
        studentGateway.update(student);

        log.info(
                "考生登录成功: username='{}', id={}, sessionRowId={}",
                cmd.getUsername(),
                student.getId(),
                session.getId());
        return token;
    }
}
