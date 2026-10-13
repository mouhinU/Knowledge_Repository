package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mouhin.knowledge.repository.domain.gateway.StudentSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.StudentSession;
import com.mouhin.knowledge.repository.infrastructure.persistence.converter.StudentSessionConverter;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.StudentSessionDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.StudentSessionMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * 考生会话仓储实现（P0-T5，多会话支持）
 *
 * <p>令牌唯一索引 {@code uk_student_session_token} 保证同一 token 至多落一行；{@code revoke} 用 {@code
 * LambdaUpdateWrapper.set(...)} 显式写入 {@code revoked_at}，绕过 MyBatis-Plus NOT_NULL 更新策略。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@Repository
public class StudentSessionGatewayImpl implements StudentSessionGateway {

    private final StudentSessionMapper studentSessionMapper;

    public StudentSessionGatewayImpl(StudentSessionMapper studentSessionMapper) {
        this.studentSessionMapper = studentSessionMapper;
    }

    @Override
    public void save(StudentSession session) {
        Objects.requireNonNull(session, "StudentSession must not be null on save");
        StudentSessionDO doObj = StudentSessionConverter.toDO(session);
        studentSessionMapper.insert(doObj);
        session.setId(doObj.getId());
    }

    @Override
    public Optional<StudentSession> findByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<StudentSessionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudentSessionDO::getSessionToken, token);
        StudentSessionDO doObj = studentSessionMapper.selectOne(wrapper);
        return Optional.ofNullable(StudentSessionConverter.toDomain(doObj));
    }

    @Override
    public Optional<StudentSession> findActiveByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<StudentSessionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudentSessionDO::getSessionToken, token)
                .isNull(StudentSessionDO::getRevokedAt)
                .gt(StudentSessionDO::getExpiresAt, LocalDateTime.now());
        StudentSessionDO doObj = studentSessionMapper.selectOne(wrapper);
        return Optional.ofNullable(StudentSessionConverter.toDomain(doObj));
    }

    @Override
    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<StudentSessionDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(StudentSessionDO::getSessionToken, token)
                .isNull(StudentSessionDO::getRevokedAt)
                .set(StudentSessionDO::getRevokedAt, now)
                .set(StudentSessionDO::getUpdateTime, now);
        studentSessionMapper.update(null, wrapper);
    }

    @Override
    public int revokeAllByStudentId(Long studentId) {
        if (studentId == null) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<StudentSessionDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(StudentSessionDO::getStudentId, studentId)
                .isNull(StudentSessionDO::getRevokedAt)
                .set(StudentSessionDO::getRevokedAt, now)
                .set(StudentSessionDO::getUpdateTime, now);
        return studentSessionMapper.update(null, wrapper);
    }
}
