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
     *
     * @param session 会话实体，{@code id} 应为 {@code null}
     */
    void save(StudentSession session);

    /**
     * 按令牌查询会话（不校验有效性）。
     *
     * @param token 会话令牌字符串
     * @return 命中的会话；不存在时返回 {@link Optional#empty()}
     */
    Optional<StudentSession> findByToken(String token);

    /**
     * 按令牌查询「当前有效」会话：{@code revoked_at IS NULL AND expires_at > now()}。
     *
     * @param token 会话令牌字符串
     * @return 有效会话；已撤销 / 已过期 / 不存在时返回 {@link Optional#empty()}
     */
    Optional<StudentSession> findActiveByToken(String token);

    /**
     * 撤销指定令牌：置 {@code revoked_at = NOW()}；令牌不存在时静默。
     *
     * @param token 待撤销的会话令牌
     */
    void revoke(String token);

    /**
     * 撤销某考生的所有活跃会话（管理员禁用账号或改密场景使用）。
     *
     * @param studentId 考生主键
     * @return 实际撤销的会话数
     */
    int revokeAllByStudentId(Long studentId);
}
