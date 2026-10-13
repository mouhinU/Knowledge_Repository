package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.SystemConfig;

import java.util.List;
import java.util.Optional;

/**
 * 系统配置网关（领域层端口）。
 *
 * <p><b>P3-T3 / C-4=方案 A 起</b>：{@code sys_config} 已加 {@code tenant_id} 列并改 {@code UNIQUE(tenant_id,
 * config_key)}。所有查询 / 写入方法均以 {@code TenantContext.getOrDefault()} 解析出的当前租户为作用域（infrastructure 层
 * {@code SystemConfigGatewayImpl} 显式追加 {@code WHERE tenant_id=?}，不依赖 MyBatis-Plus 拦截器，保证双闸门
 * saas.enabled=false 阶段行为一致）。
 *
 * <p>唯一例外：{@link #copyAllFromTenantToTenant(long, long)} 用于 P3-T3 开通步骤把平台默认租户的配置 拷贝到新租户，须绕过 {@code
 * TenantContext}（后者是平台端调用者身份，非目标租户）。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
public interface SystemConfigGateway {

    /**
     * 按配置键查询（当前租户作用域）。
     */
    Optional<SystemConfig> findByKey(String configKey);

    /**
     * 查询当前租户全部配置（按 category + config_key 排序）。
     */
    List<SystemConfig> findAll();

    /**
     * 按分组查询（当前租户作用域）。
     */
    List<SystemConfig> findByCategory(String category);

    /**
     * 保存或更新配置（按 (tenantId, configKey) 幂等；tenantId 取自当前 {@code TenantContext}）。
     */
    void saveOrUpdate(SystemConfig config);

    /**
     * 批量保存或更新。
     */
    void batchSaveOrUpdate(List<SystemConfig> configs);

    /**
     * 把源租户的全部配置行拷贝到目标租户（P3-T3 开通步骤 {@code CopyDefaultSystemConfigStep} 专用）。
     *
     * <p><b>幂等</b>：目标租户已存在的 {@code config_key} 会被跳过，不覆盖既有值；重放安全。
     *
     * <p><b>绕过 TenantContext</b>：本方法直接以显式 tenantId 读写，不受当前线程租户绑定影响； infrastructure 实现须使用 {@code
     * JdbcTemplate} 或 {@code @InterceptorIgnore} 等手段跳过 MyBatis-Plus
     * 租户拦截器，避免"从源租户读、往目标租户写"被拦截器强制统一到同一 tenantId。
     *
     * @param sourceTenantId 源租户 id（通常为 default 租户 1）
     * @param targetTenantId 目标租户 id（新开通租户）
     * @return 本次实际新增的行数（跳过的已存在行不计）
     */
    int copyAllFromTenantToTenant(long sourceTenantId, long targetTenantId);
}
