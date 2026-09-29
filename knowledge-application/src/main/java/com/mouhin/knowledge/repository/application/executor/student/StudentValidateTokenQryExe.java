package com.mouhin.knowledge.repository.application.executor.student;

import com.mouhin.knowledge.repository.application.converter.StudentConverter;
import com.mouhin.knowledge.repository.client.dto.StudentVO;
import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import com.mouhin.knowledge.repository.domain.gateway.StudentSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import com.mouhin.knowledge.repository.domain.model.entity.StudentSession;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 令牌校验查询执行器（app 层用例）
 *
 * <p>P0-T5：优先走 {@code kb_student_session} 会话表——任意活跃 token 都能解析到考生；未命中则回退到 legacy {@code
 * kb_student.session_token} 快照（仅覆盖"最后 token"），保证升级窗口内旧登录端不失效。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
public class StudentValidateTokenQryExe {

    /** 有效账号状态 */
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final StudentGateway studentGateway;
    private final StudentSessionGateway studentSessionGateway;

    public StudentValidateTokenQryExe(
            StudentGateway studentGateway, StudentSessionGateway studentSessionGateway) {
        this.studentGateway = studentGateway;
        this.studentSessionGateway = studentSessionGateway;
    }

    public Optional<StudentVO> execute(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Optional<Student> studentOpt = resolveStudent(token);
        if (studentOpt.isEmpty()) {
            return Optional.empty();
        }
        Student student = studentOpt.get();
        if (!isCredentialFresh(token, student)) {
            return Optional.empty();
        }
        if (!STATUS_ACTIVE.equals(student.getStatus())) {
            return Optional.empty();
        }
        return Optional.of(StudentConverter.toVO(student));
    }

    /** 会话表命中 → 直接按 studentId 反查；否则回退到 legacy findBySessionToken。 */
    private Optional<Student> resolveStudent(String token) {
        Optional<StudentSession> sessionOpt = studentSessionGateway.findActiveByToken(token);
        if (sessionOpt.isPresent()) {
            return studentGateway.findById(sessionOpt.get().getStudentId());
        }
        return studentGateway.findBySessionToken(token);
    }

    /**
     * 会话表命中即视为有效（{@code findActiveByToken} 已过滤 expires_at 与 revoked_at）；legacy 路径沿用 {@code
     * isTokenValid()}。
     */
    private boolean isCredentialFresh(String token, Student student) {
        if (studentSessionGateway.findActiveByToken(token).isPresent()) {
            return true;
        }
        return student.isTokenValid();
    }
}
