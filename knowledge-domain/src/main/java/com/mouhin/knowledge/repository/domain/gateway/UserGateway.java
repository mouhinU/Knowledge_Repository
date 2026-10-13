package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.User;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓储接口。
 *
 * <p><b>P3-T3 扩展</b>：新增 {@link #saveWithTenant(User, long)} 与 {@link
 * #findByUsernameAndTenant(String, long)}，服务于"平台端开通新租户时创建首位管理员"场景——此时 {@code TenantContext}
 * 仍是平台调用者身份， 无法通过标准 {@link #save(User)} 让拦截器自动填入正确的 tenant_id；须显式传入目标租户。
 *
 * @author mouhinU
 * @date 2026-09-02
 */
public interface UserGateway {

    Optional<User> findById(Long id);

    Optional<User> findByUserKey(String userKey);

    Optional<User> findByUsername(String username);

    /**
     * 按 (username, tenantId) 精确查找（P3-T3 开通前置查重）。
     *
     * <p>不同于 {@link #findByUsername(String)}：后者依赖当前 {@code TenantContext} 或 saas.enabled=true
     * 时的拦截器；本方法直接以显式 tenantId 查询，绕过任何上下文，供平台端 provisioning 步骤使用。
     *
     * @param username 用户名
     * @param tenantId 目标租户 id
     * @return 命中的用户；不存在时 {@link Optional#empty()}
     */
    Optional<User> findByUsernameAndTenant(String username, long tenantId);

    List<User> listAll();

    /**
     * 查找用户在指定文档上拥有的角色列表
     */
    List<String> findRoleKeysByUserIdAndDocumentId(Long userId, Long documentId);

    void save(User user);

    /**
     * 以显式租户 id 落库新用户（P3-T3 首位 admin 创建路径）。
     *
     * <p>行为等同 {@link #save(User)}，但强制写入指定 {@code tenantId}，不依赖 {@code TenantContext} / MyBatis-Plus
     * 拦截器；主键回填 {@code user.id}。
     *
     * @param user     用户实体（其内部 tenantKey 字段仅供 domain 层读；写库以本参数 tenantId 为准）
     * @param tenantId 目标租户 id
     */
    void saveWithTenant(User user, long tenantId);

    void update(User user);
}
