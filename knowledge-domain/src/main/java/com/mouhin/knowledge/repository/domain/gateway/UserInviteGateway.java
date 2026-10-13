package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.UserInvite;

import java.util.List;
import java.util.Optional;

/**
 * 用户邀请仓储接口（P3-T3）。
 *
 * <p>Gateway 位于 domain 层，实现落 infrastructure（COLA 红线 #7）。所有方法均以 invite_code_hash / tenant_id+user_id
 * 为主键路径查询；<b>不</b>提供按 raw code 查询的入口——raw code 从不落库， 消费方须先自行 SHA-256 后再调 {@link #findByCodeHash}。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
public interface UserInviteGateway {

    /**
     * 按 code hash 精确查询（{@code UNIQUE(invite_code_hash)} 支撑 O(1)）。
     *
     * @param inviteCodeHash SHA-256 hex 字符串（64 字符）
     * @return 邀请实体；不存在时 {@link Optional#empty()}
     */
    Optional<UserInvite> findByCodeHash(String inviteCodeHash);

    /**
     * 列出某租户下某用户的全部 PENDING 邀请（未过期未消费未吊销）。用于"重复签发前吊销旧邀请"场景。
     *
     * @param tenantId 租户 id
     * @param userId   用户 id
     * @return 邀请列表，可能为空
     */
    List<UserInvite> findPendingByUser(Long tenantId, Long userId);

    /**
     * 落库新邀请；主键回填 {@code invite.inviteId}。
     */
    void insert(UserInvite invite);

    /**
     * 更新既有邀请（消费 / 吊销时回填 status/usedAt/revokedAt/updateTime）。
     */
    void update(UserInvite invite);
}
