package com.mouhin.knowledge.repository.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.UserInviteDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户邀请 Mapper（P3-T3）。
 *
 * <p>全部 CRUD 继承自 {@link BaseMapper}；查询走 {@code LambdaQueryWrapper}，无 XML SQL。 {@code
 * sys_user_invite} 已进 {@code KnowledgeTenantLineHandler.IGNORE_TABLES} 白名单， 拦截器不会自动追加 {@code WHERE
 * tenant_id=?}，租户过滤由 {@code UserInviteGatewayImpl} 显式完成。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Mapper
public interface UserInviteMapper extends BaseMapper<UserInviteDO> {
}
