package com.mouhin.knowledge.repository.application.executor.student;

import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import com.mouhin.knowledge.repository.domain.gateway.StudentSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.StudentSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 考生退出登录命令执行器（app 层用例，事务边界）
 *
 * <p>P0-T5：优先撤销 {@code kb_student_session} 中该 token 对应的会话行；若 token 恰好匹配 {@code
 * kb_student.session_token} 快照（legacy 最后 token），一并按旧逻辑清空 legacy 字段，避免兼容读回退漏判。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
@Slf4j
public class StudentLogoutCmdExe {

    private final StudentGateway studentGateway;
    private final StudentSessionGateway studentSessionGateway;

    public StudentLogoutCmdExe(
            StudentGateway studentGateway, StudentSessionGateway studentSessionGateway) {
        this.studentGateway = studentGateway;
        this.studentSessionGateway = studentSessionGateway;
    }

    @Transactional
    public void execute(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        Optional<StudentSession> sessionOpt = studentSessionGateway.findByToken(token);
        sessionOpt.ifPresent(
                session -> {
                    studentSessionGateway.revoke(token);
                    log.info(
                            "考生退出登录（会话表）: id={}, sessionRowId={}",
                            session.getStudentId(),
                            session.getId());
                });

        // 兼容：若 token 命中 legacy 快照，同步清空 kb_student.session_token / token_expiry
        studentGateway
                .findBySessionToken(token)
                .ifPresent(
                        student -> {
                            studentGateway.clearSessionToken(student.getId());
                            log.debug("考生退出登录（legacy 快照）: id={}", student.getId());
                        });
    }
}
