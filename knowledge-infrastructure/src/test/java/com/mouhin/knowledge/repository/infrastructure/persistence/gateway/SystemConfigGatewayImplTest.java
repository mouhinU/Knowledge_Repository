package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.model.entity.SystemConfig;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.SystemConfigDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.SystemConfigMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link SystemConfigGatewayImpl} 契约单测：锁定 upsert 两分支、空 key 非法与查询转换。
 *
 * <p>Mapper 以 {@code mock(SystemConfigMapper.class)} 手工注入，不启 Spring。
 *
 * @author mouhinU
 * @date 2026-09-24 18:21:00
 */
@DisplayName("系统配置网关契约单测 (SystemConfigGatewayImpl)")
class SystemConfigGatewayImplTest {

    private final SystemConfigMapper systemConfigMapper = mock(SystemConfigMapper.class);
    private final SystemConfigGatewayImpl gateway = new SystemConfigGatewayImpl(systemConfigMapper);

    private SystemConfigDO doOf(Long id, String key, String value) {
        SystemConfigDO doObj = new SystemConfigDO();
        doObj.setId(id);
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
        assertThat(config.getId()).isEqualTo(55L);
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

        verify(systemConfigMapper, times(1)).insert(any(SystemConfigDO.class));
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
}
