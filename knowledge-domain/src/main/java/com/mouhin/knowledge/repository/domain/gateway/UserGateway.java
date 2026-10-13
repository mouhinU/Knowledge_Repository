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

    /**
     * 按主键查询用户。
     *
     * @param id 用户主键
     * @return 命中的用户；不存在时返回 {@link Optional#empty()}
     */
    Optional<User> findById(Long id);

    /**
     * 按用户唯一键查询。
     *
     * @param userKey 用户业务键
     * @return 命中的用户；不存在时返回 {@link Optional#empty()}
     */
    Optional<User> findByUserKey(String userKey);

    /**
     * 按用户名精确查询（当前租户作用域）。
     *
     * @param username 用户名
     * @return 命中的用户；不存在时返回 {@link Optional#empty()}
     */
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

    /**
     * 列出全部用户（当前租户作用域）。
     *
     * @return 用户列表；无记录返回空列表
     */
    List<User> listAll();

    /**
     * 查找用户在指定文档上拥有的角色列表。
     *
     * @param userId 用户主键
     * @param documentId 文档主键
     * @return 角色键列表；无角色时返回空列表
     */
    List<String> findRoleKeysByUserIdAndDocumentId(Long userId, Long documentId);

    /**
     * 持久化新用户，回填自增主键到入参实体。
     *
     * @param user 用户实体，{@code id} 应为 {@code null}
     */
    void save(User user);

    /**
     * 以显式租户 id 落库新用户（P3-T3 首位 admin 创建路径）。
     *
     * <p>行为等同 {@link #save(User)}，但强制写入指定 {@code tenantId}，不依赖 {@code TenantContext} / MyBatis-Plus
     * 拦截器；主键回填 {@code user.id}。
     *
     * @param user 用户实体（其内部 tenantKey 字段仅供 domain 层读；写库以本参数 tenantId 为准）
     * @param tenantId 目标租户 id
     */
    void saveWithTenant(User user, long tenantId);

    /**
     * 更新已有用户信息（按主键覆盖非空字段）。
     *
     * @param user 用户实体，{@code id} 必填
     */
    void update(User user);
}
