package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.mouhin.knowledge.repository.domain.model.entity.UserInvite;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.UserInviteDO;

/**
 * {@link UserInvite} ⇄ {@link UserInviteDO} 转换器（P3-T3）。
 *
 * <p>无副作用纯映射，两侧字段命名一致，null 输入返回 null。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
public final class UserInviteConverter {

    private UserInviteConverter() {
    }

    /**
     * DO → Entity。
     */
    public static UserInvite toDomain(UserInviteDO doObj) {
        if (doObj == null) {
            return null;
        }
        UserInvite entity = new UserInvite();
        entity.setInviteId(doObj.getInviteId());
        entity.setTenantId(doObj.getTenantId());
        entity.setUserId(doObj.getUserId());
        entity.setInviteCodeHash(doObj.getInviteCodeHash());
        entity.setStatus(doObj.getStatus());
        entity.setExpiresAt(doObj.getExpiresAt());
        entity.setUsedAt(doObj.getUsedAt());
        entity.setRevokedAt(doObj.getRevokedAt());
        entity.setCreateTime(doObj.getCreateTime());
        entity.setUpdateTime(doObj.getUpdateTime());
        return entity;
    }

    /**
     * Entity → DO。
     */
    public static UserInviteDO toDO(UserInvite entity) {
        if (entity == null) {
            return null;
        }
        UserInviteDO doObj = new UserInviteDO();
        doObj.setInviteId(entity.getInviteId());
        doObj.setTenantId(entity.getTenantId());
        doObj.setUserId(entity.getUserId());
        doObj.setInviteCodeHash(entity.getInviteCodeHash());
        doObj.setStatus(entity.getStatus());
        doObj.setExpiresAt(entity.getExpiresAt());
        doObj.setUsedAt(entity.getUsedAt());
        doObj.setRevokedAt(entity.getRevokedAt());
        doObj.setCreateTime(entity.getCreateTime());
        doObj.setUpdateTime(entity.getUpdateTime());
        return doObj;
    }
}
