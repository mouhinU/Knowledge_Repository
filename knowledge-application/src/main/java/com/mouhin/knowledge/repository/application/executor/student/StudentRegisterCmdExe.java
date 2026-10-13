package com.mouhin.knowledge.repository.application.executor.student;

import com.mouhin.knowledge.repository.application.converter.StudentConverter;
import com.mouhin.knowledge.repository.client.dto.StudentRegisterCmd;
import com.mouhin.knowledge.repository.client.dto.StudentVO;
import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 考生注册命令执行器（app 层用例，事务边界）
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
@Slf4j
public class StudentRegisterCmdExe {

    /** 密码最小长度 */
    private static final int MIN_PASSWORD_LENGTH = 6;

    /** 初始账号状态 */
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final StudentGateway studentGateway;
    private final BCryptPasswordEncoder passwordEncoder;

    public StudentRegisterCmdExe(
            StudentGateway studentGateway, BCryptPasswordEncoder passwordEncoder) {
        this.studentGateway = studentGateway;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public StudentVO execute(StudentRegisterCmd cmd) {
        String username = cmd.getUsername();
        String password = cmd.getPassword();
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("密码长度至少 6 位");
        }

        Optional<Student> existing = studentGateway.findByUsername(username);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }

        Student student = new Student();
        student.setUsername(username);
        student.setPasswordHash(passwordEncoder.encode(password));
        student.setDisplayName(cmd.getDisplayName() != null ? cmd.getDisplayName() : username);
        student.setStudentNo(cmd.getStudentNo());
        student.setStatus(STATUS_ACTIVE);
        student.setCreateTime(LocalDateTime.now());
        student.setUpdateTime(LocalDateTime.now());

        studentGateway.save(student);
        log.info("考生注册成功: username='{}', id={}", username, student.getId());
        return StudentConverter.toVO(student);
    }
}
