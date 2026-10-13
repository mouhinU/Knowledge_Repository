package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 租户放置策略数据对象（P1-T1，SaaS 平台元数据）。
 *
 * <p>记录每个租户走 {@code POOL}（共享库 + {@code tenant_id} 列过滤）还是 {@code SILO}（独立库 / 独立 collection）。 与
 * {@link TenantDO} 同为平台级表，须登记到 {@code TenantLineInnerInterceptor} 的 ignore 名单（P1-T7）。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@Getter
@Setter
@TableName("sys_tenant_placement")
public class TenantPlacementDO {

    /**
     * 放置类型：共享库（列级隔离）。
     */
    public static final String PLACEMENT_POOL = "POOL";

    /**
     * 放置类型：独立库（物理隔离）。
     */
    public static final String PLACEMENT_SILO = "SILO";

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("placement_type")
    private String placementType;

    @TableField("silo_ref")
    private String siloRef;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
