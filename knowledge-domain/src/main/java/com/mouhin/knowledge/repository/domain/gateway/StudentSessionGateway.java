package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.StudentSession;

import java.util.Optional;

/**
 * 考生会话仓储接口（P0-T5，多会话支持）
 *
 * <p>登录时 {@link #save(StudentSession)} 落一行，退出时 {@link #revoke(String)} 置 {@code revoked_at}。 令牌校验走
 * {@link #findActiveByToken(String)}：仅返回未撤销且未过期的会话。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
public interface StudentSessionGateway {

    /**
     * 新增一条会话记录；持久化后回填 {@code id}。
     */
    void save(StudentSession session);

    /**
     * 按令牌查询会话（不校验有效性）。
     */
    Optional<StudentSession> findByToken(String token);

    /**
     * 按令牌查询「当前有效」会话：{@code revoked_at IS NULL AND expires_at > now()}。
     */
    Optional<StudentSession> findActiveByToken(String token);

    /**
     * 撤销指定令牌：置 {@code revoked_at = NOW()}；令牌不存在时静默。
     */
    void revoke(String token);

    /**
     * 撤销某考生的所有活跃会话（管理员禁用账号或改密场景使用）。
     */
    int revokeAllByStudentId(Long studentId);
}
