package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.Student;

import java.util.List;
import java.util.Optional;

/**
 * 考生仓储接口
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
public interface StudentGateway {

    void save(Student student);

    void update(Student student);

    Optional<Student> findById(Long id);

    Optional<Student> findByUsername(String username);

    Optional<Student> findBySessionToken(String token);

    /**
     * 清空指定考生的会话令牌与令牌过期时间（退出登录使用）。
     *
     * <p>与通用 {@link #update(Student)} 分离：MyBatis-Plus 默认 NOT_NULL 更新策略会跳过 null 字段，
     * 通用 update 无法把 {@code session_token} / {@code token_expiry} 真正置空。本实现通过
     * {@code LambdaUpdateWrapper.set(..., null)} 显式写入 NULL，保证令牌即刻失效。</p>
     *
     * @param id 考生主键
     */
    void clearSessionToken(Long id);

    List<Student> listAll();

    List<Student> listByDepartment(String departmentId);

    void deleteById(Long id);
}
