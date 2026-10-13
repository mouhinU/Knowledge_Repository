package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.mouhin.knowledge.repository.domain.context.TenantContext;
import com.mouhin.knowledge.repository.domain.model.entity.SystemConfig;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.SystemConfigDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.SystemConfigMapper;
import com.mouhin.knowledge.repository.infrastructure.tenant.TenantIdResolver;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * {@link SystemConfigGatewayImpl} 契约单测（P3-T3 起 sys_config 已租户化）：
 *
 * <ul>
 *   <li>upsert 两分支：命中已存在走 updateById、未命中走 insert；
 *   <li>空 key 非法；
 *   <li>读写路径均按当前 {@link TenantContext} 追加 {@code WHERE tenant_id=?}（双闸门 saas.enabled=false
 *       下拦截器缺席时仍保证隔离）；
 *   <li>{@code saveOrUpdate} 强制以当前租户覆盖 DO.tenantId，防跨租户写入；
 *   <li>{@code copyAllFromTenantToTenant} 走 JdbcTemplate 原生 SQL，绕过拦截器；source==target 时零操作。
 * </ul>
 *
 * <p>Mapper / TenantIdResolver / JdbcTemplate 均以 mock 手工注入，不启 Spring。
 *
 * @author mouhinU
 * @date 2026-09-24 18:21:00
 */
@DisplayName("系统配置网关契约单测 (SystemConfigGatewayImpl, P3-T3 租户化)")
class SystemConfigGatewayImplTest {

    private static final long DEFAULT_TENANT_ID = 1L;
    private static final long ACME_TENANT_ID = 42L;

    private final SystemConfigMapper systemConfigMapper = mock(SystemConfigMapper.class);
    private final TenantIdResolver tenantIdResolver = mock(TenantIdResolver.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final SystemConfigGatewayImpl gateway =
            new SystemConfigGatewayImpl(systemConfigMapper, tenantIdResolver, jdbcTemplate);

    @BeforeEach
    void setUp() {
        // 默认绑定 default → 1；单个测试可再覆盖为其他租户
        when(tenantIdResolver.resolve(TenantContext.DEFAULT_TENANT_KEY))
                .thenReturn(DEFAULT_TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private SystemConfigDO doOf(Long id, String key, String value) {
        SystemConfigDO doObj = new SystemConfigDO();
        doObj.setId(id);
        doObj.setTenantId(DEFAULT_TENANT_ID);
        doObj.setConfigKey(key);
        doObj.setConfigValue(value);
        doObj.setValueType("STRING");
        doObj.setCategory("system");
        doObj.setEditable(Boolean.TRUE);
        return doObj;
    }

    private SystemConfig entityOf(String key, String value) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setValueType("STRING");
        config.setCategory("system");
        return config;
    }

    @Test
    @DisplayName("findByKey → 条件查询命中转 Entity；未命中回 empty")
    void findByKeyMapsAndHandlesMissing() {
        when(systemConfigMapper.selectOne(any())).thenReturn(doOf(1L, "feature.x", "on"));
        Optional<SystemConfig> hit = gateway.findByKey("feature.x");
        assertThat(hit).isPresent();
        assertThat(hit.get().getConfigValue()).isEqualTo("on");
        assertThat(hit.get().getEditable()).isTrue();

        when(systemConfigMapper.selectOne(any())).thenReturn(null);
        assertThat(gateway.findByKey("nope")).isEmpty();
    }

    @Test
    @DisplayName("findAll → selectList 结果批量转 Entity")
    void findAllMapsEveryRow() {
        when(systemConfigMapper.selectList(any()))
                .thenReturn(List.of(doOf(1L, "a", "1"), doOf(2L, "b", "2")));

        List<SystemConfig> all = gateway.findAll();

        assertThat(all).hasSize(2);
        assertThat(all).extracting(SystemConfig::getConfigKey).containsExactly("a", "b");
    }

    @Test
    @DisplayName("saveOrUpdate 命中已存在 → 走 updateById 并回填既有主键，不 insert")
    void saveOrUpdateUpdatesWhenExisting() {
        when(systemConfigMapper.selectOne(any())).thenReturn(doOf(55L, "feature.x", "old"));
        SystemConfig config = entityOf("feature.x", "new");

        gateway.saveOrUpdate(config);

        ArgumentCaptor<SystemConfigDO> cap = ArgumentCaptor.forClass(SystemConfigDO.class);
        verify(systemConfigMapper, times(1)).updateById(cap.capture());
        assertThat(cap.getValue().getId()).isEqualTo(55L);
        assertThat(cap.getValue().getConfigValue()).isEqualTo("new");
        assertThat(cap.getValue().getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
        assertThat(config.getId()).isEqualTo(55L);
        assertThat(config.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
        verify(systemConfigMapper, never()).insert(any(SystemConfigDO.class));
    }

    @Test
    @DisplayName("saveOrUpdate 未命中 → 走 insert 并回填生成主键，不 updateById")
    void saveOrUpdateInsertsWhenAbsent() {
        when(systemConfigMapper.selectOne(any())).thenReturn(null);
        when(systemConfigMapper.insert(any(SystemConfigDO.class)))
                .thenAnswer(
                        inv -> {
                            inv.getArgument(0, SystemConfigDO.class).setId(77L);
                            return 1;
                        });
        SystemConfig config = entityOf("brand.new", "v");

        gateway.saveOrUpdate(config);

        ArgumentCaptor<SystemConfigDO> cap = ArgumentCaptor.forClass(SystemConfigDO.class);
        verify(systemConfigMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
        assertThat(config.getId()).isEqualTo(77L);
        verify(systemConfigMapper, never()).updateById(any(SystemConfigDO.class));
    }

    @Test
    @DisplayName("saveOrUpdate 空/空白 configKey → IllegalArgumentException")
    void saveOrUpdateRejectsBlankKey() {
        assertThatThrownBy(() -> gateway.saveOrUpdate(entityOf("  ", "v")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway.saveOrUpdate(null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(systemConfigMapper, never()).insert(any(SystemConfigDO.class));
        verify(systemConfigMapper, never()).updateById(any(SystemConfigDO.class));
    }

    @Test
    @DisplayName("saveOrUpdate 强制以当前租户覆盖 DO.tenantId（调用方误传跨租户 id 不生效）")
    void saveOrUpdateOverridesTenantId() {
        TenantContext.set("acme");
        when(tenantIdResolver.resolve("acme")).thenReturn(ACME_TENANT_ID);
        when(systemConfigMapper.selectOne(any())).thenReturn(null);
        when(systemConfigMapper.insert(any(SystemConfigDO.class)))
                .thenAnswer(
                        inv -> {
                            inv.getArgument(0, SystemConfigDO.class).setId(99L);
                            return 1;
                        });

        SystemConfig config = entityOf("feature.y", "v");
        config.setTenantId(999L); // 调用方误传

        gateway.saveOrUpdate(config);

        ArgumentCaptor<SystemConfigDO> cap = ArgumentCaptor.forClass(SystemConfigDO.class);
        verify(systemConfigMapper).insert(cap.capture());
        assertThat(cap.getValue().getTenantId())
                .as("DO.tenantId 必须被强制覆盖为当前租户 42")
                .isEqualTo(ACME_TENANT_ID);
        assertThat(config.getTenantId()).isEqualTo(ACME_TENANT_ID);
    }

    @Test
    @DisplayName("copyAllFromTenantToTenant source==target → 零操作，不触 JdbcTemplate")
    void copySkipsWhenSameTenant() {
        int copied = gateway.copyAllFromTenantToTenant(7L, 7L);

        assertThat(copied).isZero();
        verify(jdbcTemplate, never())
                .query(any(String.class), any(RowMapper.class), any(Object[].class));
        verify(jdbcTemplate, never()).update(any(String.class), any(Object[].class));
    }

    @Test
    @DisplayName("copyAllFromTenantToTenant 目标已存在同 key → 跳过 INSERT，计数不计入")
    @SuppressWarnings("unchecked")
    void copySkipsPreExistingRows() throws Exception {
        // 源租户 1 有两行：用 thenAnswer 真正调用生产 RowMapper 配合 mock ResultSet，
        // 产出真实的 SystemConfigGatewayImpl.ConfigRow（private record），避免类型强转失败。
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq(1L)))
                .thenAnswer(
                        inv -> {
                            RowMapper<Object> mapper = inv.getArgument(1);
                            return List.of(
                                    mapper.mapRow(resultSetOf("a", "1"), 0),
                                    mapper.mapRow(resultSetOf("b", "2"), 1));
                        });
        // 目标租户 2 已有 a
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), eq(2L), eq("a")))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), eq(2L), eq("b")))
                .thenReturn(0);

        int copied = gateway.copyAllFromTenantToTenant(1L, 2L);

        assertThat(copied).isEqualTo(1);
        // 仅 b 被 INSERT 一次
        verify(jdbcTemplate, times(1)).update(any(String.class), any(Object[].class));
    }

    /** 构造一个仅承载 config_key / config_value 的 mock ResultSet，其余列回默认值，供生产 RowMapper 读取。 */
    private java.sql.ResultSet resultSetOf(String key, String value) throws Exception {
        java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
        when(rs.getString("config_key")).thenReturn(key);
        when(rs.getString("config_value")).thenReturn(value);
        when(rs.getString("value_type")).thenReturn("STRING");
        when(rs.getString("description")).thenReturn("desc-" + key);
        when(rs.getString("category")).thenReturn("system");
        when(rs.getInt("editable")).thenReturn(1);
        return rs;
    }
}
