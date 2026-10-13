package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 平台端开通租户命令（client 层入参，P3-T1）。
 *
 * <p>由 {@code PlatformTenantController} 从请求体组装，交 app 层 {@code TenantCreateCmdExe} 消费。 {@code
 * tenantKey} 为全局唯一业务键（小写，须通过 {@code SiloCollectionNaming.isValid} 校验，为将来 SILO 集合命名预留合法性）； {@code
 * operatorId}/{@code operatorName} 由平台令牌 claim 注入，用于审计溯源，不由前端请求体提供。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Getter
@Setter
public class TenantCreateCmd {

    /**
     * 租户业务键（全局唯一，小写字母开头，[a-z0-9_]，2–63 字符）。
     */
    private String tenantKey;

    /**
     * 租户展示名。
     */
    private String name;

    /**
     * 套餐标识（如 FREE / STANDARD / ENTERPRISE），可空由 app 层落默认。
     */
    private String plan;

    /**
     * 数据驻留区（如 cn-hangzhou），可空。
     */
    private String dataRegion;

    /**
     * 操作者标识（来自平台令牌 claim，非请求体）。
     */
    private String operatorId;

    /**
     * 操作者展示名（来自平台令牌 claim，非请求体）。
     */
    private String operatorName;
}
