package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 租户删除审批令牌数据对象（P3-T5，映射 {@code sys_tenant_delete_approval}）。
 *
 * <p>平台级表，{@code tenant_id} 是"被删除的租户"业务列而非拦截器过滤维度；须登记到 {@code TenantLineInnerInterceptor} 的 ignore
 * 名单（同 {@code sys_user_invite}）。{@code tokenHash} 存 SHA-256(raw token)，raw token 不入库。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Getter
@Setter
@TableName("sys_tenant_delete_approval")
public class TenantDeleteApprovalDO {

    @TableId(value = "approval_id", type = IdType.AUTO)
    private Long approvalId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("approver_operator_id")
    private String approverOperatorId;

    @TableField("token_hash")
    private String tokenHash;

    @TableField("status")
    private String status;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("used_at")
    private LocalDateTime usedAt;

    @TableField("used_by_operator_id")
    private String usedByOperatorId;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
