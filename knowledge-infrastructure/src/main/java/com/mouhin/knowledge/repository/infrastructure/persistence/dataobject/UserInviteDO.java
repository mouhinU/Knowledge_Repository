package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户邀请数据对象（P3-T3，对应 V28 迁移建表 {@code sys_user_invite}）。
 *
 * <p>字段与 {@code com.mouhin.knowledge.repository.domain.model.entity.UserInvite} 一一对应； 转换器 {@code
 * UserInviteConverter} 负责双向映射。<b>不</b>持有 raw code，只持有 SHA-256 hash。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Getter
@Setter
@TableName("sys_user_invite")
public class UserInviteDO {

    @TableId(value = "invite_id", type = IdType.AUTO)
    private Long inviteId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("user_id")
    private Long userId;

    @TableField("invite_code_hash")
    private String inviteCodeHash;

    @TableField("status")
    private String status;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("used_at")
    private LocalDateTime usedAt;

    @TableField("revoked_at")
    private LocalDateTime revokedAt;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
