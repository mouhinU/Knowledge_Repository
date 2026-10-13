package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.knowledge.repository.domain.gateway.UserInviteGateway;
import com.mouhin.knowledge.repository.domain.model.entity.UserInvite;
import com.mouhin.knowledge.repository.infrastructure.persistence.converter.UserInviteConverter;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.UserInviteDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.UserInviteMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link UserInviteGateway} 实现（P3-T3，基础设施层持久化）。
 *
 * <p>{@code sys_user_invite} 在 {@code KnowledgeTenantLineHandler.IGNORE_TABLES} 白名单里， 拦截器不会追加
 * {@code WHERE tenant_id=?}；因此本类所有查询均显式带上租户谓词，避免跨租户串数据。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Repository
public class UserInviteGatewayImpl implements UserInviteGateway {

    private final UserInviteMapper userInviteMapper;

    public UserInviteGatewayImpl(UserInviteMapper userInviteMapper) {
        this.userInviteMapper = userInviteMapper;
    }

    @Override
    public Optional<UserInvite> findByCodeHash(String inviteCodeHash) {
        if (inviteCodeHash == null || inviteCodeHash.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<UserInviteDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInviteDO::getInviteCodeHash, inviteCodeHash);
        return Optional.ofNullable(
                UserInviteConverter.toDomain(userInviteMapper.selectOne(wrapper)));
    }

    @Override
    public List<UserInvite> findPendingByUser(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            return List.of();
        }
        LambdaQueryWrapper<UserInviteDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInviteDO::getTenantId, tenantId)
                .eq(UserInviteDO::getUserId, userId)
                .eq(UserInviteDO::getStatus, UserInvite.STATUS_PENDING)
                .orderByDesc(UserInviteDO::getCreateTime);
        return userInviteMapper.selectList(wrapper).stream()
                .map(UserInviteConverter::toDomain)
                .toList();
    }

    @Override
    public void insert(UserInvite invite) {
        Objects.requireNonNull(invite, "UserInvite must not be null on insert");
        UserInviteDO doObj = UserInviteConverter.toDO(invite);
        LocalDateTime now = LocalDateTime.now();
        doObj.setCreateTime(now);
        doObj.setUpdateTime(now);
        userInviteMapper.insert(doObj);
        invite.setInviteId(doObj.getInviteId());
        invite.setCreateTime(doObj.getCreateTime());
        invite.setUpdateTime(doObj.getUpdateTime());
    }

    @Override
    public void update(UserInvite invite) {
        Objects.requireNonNull(invite, "UserInvite must not be null on update");
        if (invite.getInviteId() == null) {
            throw new IllegalStateException("UserInvite.inviteId required on update");
        }
        UserInviteDO doObj = UserInviteConverter.toDO(invite);
        doObj.setUpdateTime(LocalDateTime.now());
        userInviteMapper.updateById(doObj);
        invite.setUpdateTime(doObj.getUpdateTime());
    }
}
