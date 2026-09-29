package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.knowledge.repository.domain.context.TenantContext;
import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.UserDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.UserMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.UserRoleMapper;
import com.mouhin.knowledge.repository.infrastructure.tenant.TenantIdResolver;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 用户仓储实现
 *
 * <p>P1-T8 起在 {@code DO→Entity} 转换处借 {@link TenantIdResolver} 反查 {@code UserDO.tenant_id (BIGINT)}
 * 对应的 {@code tenant_key (String)}，把语义化 key 带回 domain {@link User}。历史行 {@code tenant_id} 缺失或漂移时以
 * {@link TenantContext#DEFAULT_TENANT_KEY} 兜底，绝不阻断用户加载。反向写入（{@code Entity→DO}）当前不映射
 * tenant_id：新用户注册路径尚未按租户隔离（Phase 3 起才补），依赖 V26 的 {@code DEFAULT 1} 兜底落 default 租户。
 *
 * @author mouhinU
 * @date 2026-09-02
 */
@Repository
public class UserGatewayImpl implements UserGateway {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final TenantIdResolver tenantIdResolver;

    public UserGatewayImpl(
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            TenantIdResolver tenantIdResolver) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.tenantIdResolver = tenantIdResolver;
    }

    @Override
    public Optional<User> findById(Long id) {
        UserDO doObj = userMapper.selectById(id);
        return Optional.ofNullable(toDomain(doObj));
    }

    @Override
    public Optional<User> findByUserKey(String userKey) {
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDO::getUserKey, userKey);
        UserDO doObj = userMapper.selectOne(wrapper);
        return Optional.ofNullable(toDomain(doObj));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDO::getUsername, username);
        UserDO doObj = userMapper.selectOne(wrapper);
        return Optional.ofNullable(toDomain(doObj));
    }

    @Override
    public List<User> listAll() {
        return userMapper.selectList(null).stream().map(this::toDomain).toList();
    }

    @Override
    public List<String> findRoleKeysByUserIdAndDocumentId(Long userId, Long documentId) {
        return userRoleMapper.selectRoleKeysByUserId(userId);
    }

    @Override
    public void save(User user) {
        java.util.Objects.requireNonNull(user, "User must not be null on save");
        UserDO doObj = toDO(user);
        doObj.setCreateTime(LocalDateTime.now());
        doObj.setUpdateTime(LocalDateTime.now());
        userMapper.insert(doObj);
        user.setId(doObj.getId());
    }

    @Override
    public void update(User user) {
        java.util.Objects.requireNonNull(user, "User must not be null on update");
        UserDO doObj = toDO(user);
        doObj.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(doObj);
    }

    private User toDomain(UserDO doObj) {
        if (doObj == null) {
            return null;
        }
        User user = new User();
        user.setId(doObj.getId());
        user.setUserKey(doObj.getUserKey());
        user.setUsername(doObj.getUsername());
        user.setDepartmentId(doObj.getDepartmentId());
        user.setTenantKey(resolveTenantKeyForEntity(doObj.getTenantId()));
        user.setAdmin(doObj.getAdmin());
        user.setPasswordHash(doObj.getPasswordHash());
        user.setStatus(doObj.getStatus());
        user.setCreatedTime(doObj.getCreateTime());
        user.setUpdatedTime(doObj.getUpdateTime());
        return user;
    }

    private String resolveTenantKeyForEntity(Long tenantId) {
        if (tenantId == null) {
            return TenantContext.DEFAULT_TENANT_KEY;
        }
        return tenantIdResolver.lookupKeyOrDefault(tenantId, TenantContext.DEFAULT_TENANT_KEY);
    }

    private UserDO toDO(User user) {
        if (user == null) {
            return null;
        }
        UserDO doObj = new UserDO();
        doObj.setId(user.getId());
        doObj.setUserKey(user.getUserKey());
        doObj.setUsername(user.getUsername());
        doObj.setDepartmentId(user.getDepartmentId());
        doObj.setAdmin(user.getAdmin());
        doObj.setPasswordHash(user.getPasswordHash());
        doObj.setStatus(user.getStatus());
        return doObj;
    }
}
