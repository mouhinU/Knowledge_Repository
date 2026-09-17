package com.mouhin.knowledge.repository.application.executor.student;

import com.mouhin.knowledge.repository.application.converter.StudentConverter;
import com.mouhin.knowledge.repository.client.dto.StudentVO;
import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 令牌校验查询执行器（app 层用例）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class StudentValidateTokenQryExe {

    /** 有效账号状态 */
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final StudentGateway studentGateway;

    public StudentValidateTokenQryExe(StudentGateway studentGateway) {
        this.studentGateway = studentGateway;
    }

    public Optional<StudentVO> execute(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Optional<Student> opt = studentGateway.findBySessionToken(token);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        Student student = opt.get();
        if (!student.isTokenValid()) {
            return Optional.empty();
        }
        if (!STATUS_ACTIVE.equals(student.getStatus())) {
            return Optional.empty();
        }
        return Optional.of(StudentConverter.toVO(student));
    }
}
