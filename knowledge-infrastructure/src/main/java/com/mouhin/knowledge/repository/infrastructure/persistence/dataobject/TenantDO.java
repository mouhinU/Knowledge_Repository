package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 租户主表数据对象（P1-T1，SaaS 平台元数据）。
 *
 * <p>平台级表，本身不带 {@code tenant_id} 列——须登记到 {@code TenantLineInnerInterceptor} 的 ignore 名单（P1-T7），
 * 否则会被自动注入租户过滤而形成悖论。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@Getter
@Setter
@TableName("sys_tenant")
public class TenantDO {

    /** 状态：已创建但尚未开通（P3-T2 起点）。 */
    public static final String STATUS_PENDING = "PENDING";

    /** 状态：开通中（步骤逐条落 {@code sys_tenant_provision_step}）。 */
    public static final String STATUS_PROVISIONING = "PROVISIONING";

    /** 状态：正常。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 状态：已停用（软拦，请求返 403 TENANT_SUSPENDED）。 */
    public static final String STATUS_SUSPENDED = "SUSPENDED";

    /** 状态：已归档（保留期内可恢复）。 */
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 状态：删除中（ARCHIVED 过保留期 + 双人复核后由 reconciler 认领硬删的中间态）。 */
    public static final String STATUS_DELETING = "DELETING";

    /** 状态：已删除（终态）。 */
    public static final String STATUS_DELETED = "DELETED";

    /** 状态：开通失败（可通过 {@code provisionRequestId} + attempt_no+1 重放）。 */
    public static final String STATUS_FAILED = "FAILED";

    @TableId(value = "tenant_id", type = IdType.AUTO)
    private Long tenantId;

    @TableField("tenant_key")
    private String tenantKey;

    @TableField("name")
    private String name;

    @TableField("plan")
    private String plan;

    @TableField("status")
    private String status;

    @TableField("data_region")
    private String dataRegion;

    @TableField("provision_request_id")
    private String provisionRequestId;

    @TableField("deletion_request_id")
    private String deletionRequestId;

    @TableField("archived_at")
    private LocalDateTime archivedAt;

    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
