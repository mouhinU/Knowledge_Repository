package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.Student;
import java.util.List;
import java.util.Optional;

/**
 * 考生仓储接口
 *
 * @author mouhinU
 * @date 2026-09-15
 */
public interface StudentGateway {

    /**
     * 持久化新考生，回填自增主键到入参实体。
     *
     * @param student 考生实体，{@code id} 应为 {@code null}
     */
    void save(Student student);

    /**
     * 更新已有考生信息（按主键全量覆盖非空字段）。
     *
     * @param student 考生实体，{@code id} 必填
     */
    void update(Student student);

    /**
     * 按主键查询考生。
     *
     * @param id 考生主键
     * @return 命中的考生；不存在时返回 {@link Optional#empty()}
     */
    Optional<Student> findById(Long id);

    /**
     * 按用户名精确查询考生（当前租户作用域）。
     *
     * @param username 用户名
     * @return 命中的考生；不存在时返回 {@link Optional#empty()}
     */
    Optional<Student> findByUsername(String username);

    /**
     * 按会话令牌查询考生（令牌有效性由调用方判定）。
     *
     * @param token 会话令牌字符串
     * @return 命中的考生；不存在时返回 {@link Optional#empty()}
     */
    Optional<Student> findBySessionToken(String token);

    /**
     * 清空指定考生的会话令牌与令牌过期时间（退出登录使用）。
     *
     * <p>与通用 {@link #update(Student)} 分离：MyBatis-Plus 默认 NOT_NULL 更新策略会跳过 null 字段， 通用 update 无法把
     * {@code session_token} / {@code token_expiry} 真正置空。本实现通过 {@code LambdaUpdateWrapper.set(...,
     * null)} 显式写入 NULL，保证令牌即刻失效。
     *
     * @param id 考生主键
     */
    void clearSessionToken(Long id);

    /**
     * 列出全部考生（当前租户作用域）。
     *
     * @return 考生列表；无记录返回空列表
     */
    List<Student> listAll();

    /**
     * 按部门列出考生。
     *
     * @param departmentId 部门标识
     * @return 该部门下的考生列表；无记录返回空列表
     */
    List<Student> listByDepartment(String departmentId);

    /**
     * 按主键删除考生。
     *
     * @param id 考生主键
     */
    void deleteById(Long id);
}
