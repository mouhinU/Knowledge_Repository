package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.knowledge.repository.domain.context.TenantContext;
import com.mouhin.knowledge.repository.domain.gateway.SystemConfigGateway;
import com.mouhin.knowledge.repository.domain.model.entity.SystemConfig;
import com.mouhin.knowledge.repository.infrastructure.persistence.converter.SystemConfigConverter;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.SystemConfigDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.SystemConfigMapper;
import com.mouhin.knowledge.repository.infrastructure.tenant.TenantIdResolver;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 系统配置网关实现（基础设施层持久化）。
 *
 * <p><b>P3-T3 / C-4=方案 A 起</b>：{@code sys_config} 已加 {@code tenant_id NOT NULL DEFAULT 1} 与 {@code
 * UNIQUE(tenant_id, config_key)}（V28 迁移），并从 {@code KnowledgeTenantLineHandler.IGNORE_TABLES}
 * 白名单移除。本实现的读写路径均<b>显式</b>按当前 {@code TenantContext} 追加 {@code WHERE tenant_id=?}—— 双闸门 {@code
 * saas.enabled=false} 时拦截器缺席，显式路径确保租户隔离；将来 saas.enabled=true 打开时， 拦截器会再追加一次相同谓词，行为等价、无副作用。
 *
 * <p>{@link #copyAllFromTenantToTenant(long, long)} 是本类唯一绕过 {@code TenantContext} 的方法， 供 P3-T3
 * {@code CopyDefaultSystemConfigStep} 使用。为避免拦截器强制统一 tenant_id 破坏"从源租户读、 往目标租户写"的语义，走 {@link
 * JdbcTemplate} 原生 SQL；H2 MODE=MySQL 与 MySQL 8 双方言兼容。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Repository
public class SystemConfigGatewayImpl implements SystemConfigGateway {

    private static final Logger log = LoggerFactory.getLogger(SystemConfigGatewayImpl.class);

    /** 拷贝 SQL：SELECT 源租户全部行的业务列，供 {@link #copyAllFromTenantToTenant} 逐行 INSERT 到目标租户。 */
    private static final String SQL_SELECT_BY_TENANT =
            "SELECT config_key, config_value, value_type, description, category, editable "
                    + "FROM sys_config WHERE tenant_id = ?";

    /** 目标租户是否已有同 config_key 行的存在性检查 SQL。 */
    private static final String SQL_EXISTS_IN_TENANT =
            "SELECT COUNT(1) FROM sys_config WHERE tenant_id = ? AND config_key = ?";

    /** 向目标租户 INSERT 一行配置的 SQL（create_time / update_time 走 DB DEFAULT）。 */
    private static final String SQL_INSERT_INTO_TENANT =
            "INSERT INTO sys_config (tenant_id, config_key, config_value, value_type, description, category, editable) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private final SystemConfigMapper systemConfigMapper;
    private final TenantIdResolver tenantIdResolver;
    private final JdbcTemplate jdbcTemplate;

    public SystemConfigGatewayImpl(
            SystemConfigMapper systemConfigMapper,
            TenantIdResolver tenantIdResolver,
            JdbcTemplate jdbcTemplate) {
        this.systemConfigMapper = systemConfigMapper;
        this.tenantIdResolver = tenantIdResolver;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<SystemConfig> findByKey(String configKey) {
        long tenantId = currentTenantId();
        LambdaQueryWrapper<SystemConfigDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfigDO::getTenantId, tenantId)
                .eq(SystemConfigDO::getConfigKey, configKey);
        SystemConfigDO doObj = systemConfigMapper.selectOne(wrapper);
        return Optional.ofNullable(SystemConfigConverter.toDomain(doObj));
    }

    @Override
    public List<SystemConfig> findAll() {
        long tenantId = currentTenantId();
        LambdaQueryWrapper<SystemConfigDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfigDO::getTenantId, tenantId)
                .orderByAsc(SystemConfigDO::getCategory)
                .orderByAsc(SystemConfigDO::getConfigKey);
        return systemConfigMapper.selectList(wrapper).stream()
                .map(SystemConfigConverter::toDomain)
                .toList();
    }

    @Override
    public List<SystemConfig> findByCategory(String category) {
        long tenantId = currentTenantId();
        LambdaQueryWrapper<SystemConfigDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfigDO::getTenantId, tenantId)
                .eq(SystemConfigDO::getCategory, category)
                .orderByAsc(SystemConfigDO::getConfigKey);
        return systemConfigMapper.selectList(wrapper).stream()
                .map(SystemConfigConverter::toDomain)
                .toList();
    }

    @Override
    public void saveOrUpdate(SystemConfig config) {
        if (config == null || config.getConfigKey() == null || config.getConfigKey().isBlank()) {
            throw new IllegalArgumentException(
                    "SystemConfig with a non-blank configKey is required");
        }
        long tenantId = currentTenantId();
        SystemConfigDO doObj = SystemConfigConverter.toDO(config);
        if (doObj == null) {
            throw new IllegalStateException(
                    "SystemConfigConverter returned null for " + config.getConfigKey());
        }
        // 强制以当前租户覆盖 DO.tenantId，避免调用方误传导致跨租户写入
        doObj.setTenantId(tenantId);

        LambdaQueryWrapper<SystemConfigDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfigDO::getTenantId, tenantId)
                .eq(SystemConfigDO::getConfigKey, config.getConfigKey());
        SystemConfigDO existing = systemConfigMapper.selectOne(wrapper);
        if (existing != null) {
            doObj.setId(existing.getId());
            systemConfigMapper.updateById(doObj);
            config.setId(existing.getId());
        } else {
            systemConfigMapper.insert(doObj);
            config.setId(doObj.getId());
        }
        config.setTenantId(tenantId);
    }

    @Override
    public void batchSaveOrUpdate(List<SystemConfig> configs) {
        if (configs == null) {
            return;
        }
        for (SystemConfig config : configs) {
            saveOrUpdate(config);
        }
    }

    @Override
    public int copyAllFromTenantToTenant(long sourceTenantId, long targetTenantId) {
        if (sourceTenantId == targetTenantId) {
            log.debug(
                    "[SystemConfig] copy skipped: source and target tenant are the same ({})",
                    sourceTenantId);
            return 0;
        }
        List<ConfigRow> sources =
                jdbcTemplate.query(
                        SQL_SELECT_BY_TENANT,
                        (rs, rowNum) ->
                                new ConfigRow(
                                        rs.getString("config_key"),
                                        rs.getString("config_value"),
                                        rs.getString("value_type"),
                                        rs.getString("description"),
                                        rs.getString("category"),
                                        rs.getInt("editable") != 0),
                        sourceTenantId);
        int copied = 0;
        for (ConfigRow row : sources) {
            Integer exists =
                    jdbcTemplate.queryForObject(
                            SQL_EXISTS_IN_TENANT, Integer.class, targetTenantId, row.configKey());
            if (exists != null && exists > 0) {
                continue;
            }
            jdbcTemplate.update(
                    SQL_INSERT_INTO_TENANT,
                    targetTenantId,
                    row.configKey(),
                    row.configValue(),
                    row.valueType(),
                    row.description(),
                    row.category(),
                    row.editable());
            copied++;
        }
        log.info(
                "[SystemConfig] copied {} rows from tenant {} to tenant {} (skipped {} pre-existing)",
                copied,
                sourceTenantId,
                targetTenantId,
                sources.size() - copied);
        return copied;
    }

    /**
     * 解析当前线程绑定的租户 id（未绑定时回落 default → 1）。
     *
     * <p>与 {@code KnowledgeTenantLineHandler.getTenantId()} 语义一致；两处均走 {@link
     * TenantIdResolver#resolve(String)}，缓存策略共享。
     */
    private long currentTenantId() {
        return tenantIdResolver.resolve(TenantContext.getOrDefault());
    }

    /**
     * 拷贝操作的中间行结构；只承载 SELECT 出的业务列，不带 id / tenant_id / 时间戳（由 DB DEFAULT / INSERT 语句填）。
     *
     * @param configKey 配置键
     * @param configValue 配置值
     * @param valueType 值类型
     * @param description 说明
     * @param category 分组
     * @param editable 是否可编辑
     */
    private record ConfigRow(
            String configKey,
            String configValue,
            String valueType,
            String description,
            String category,
            boolean editable) {}
}
