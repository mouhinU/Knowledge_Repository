package com.mouhin.knowledge.repository.application.executor.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.application.support.SystemConfigService;
import com.mouhin.knowledge.repository.domain.gateway.SystemConfigGateway;
import com.mouhin.knowledge.repository.domain.model.entity.SystemConfig;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 系统配置更新执行器单测：以真实 {@link SystemConfigService}（仅 mock 其 Gateway）锁定执行器透传的完整写链路—— （1）DB 已有可编辑记录 → 改值后走
 * {@code gateway.saveOrUpdate} 并即时刷新缓存； （2）未注册 key → 白名单拒绝（Unknown key），不落库； （3）默认值已注册但 DB 缺行 →
 * 按注册元数据创建可编辑记录； （4）editable=false → 只读拒绝，不落库。
 *
 * @author mouhinU
 * @date 2026-09-24 18:15:00
 */
@DisplayName("系统配置更新执行器 (UpdateSystemConfigCmdExe)")
class UpdateSystemConfigCmdExeTest {

    private static final String KNOWN_KEY = "exam.quality-score-threshold";

    private final SystemConfigGateway systemConfigGateway = mock(SystemConfigGateway.class);
    private final UpdateSystemConfigCmdExe exe =
            new UpdateSystemConfigCmdExe(new SystemConfigService(systemConfigGateway));

    private SystemConfig existing(String key, String value, Boolean editable) {
        SystemConfig config = new SystemConfig();
        config.setId(1L);
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setValueType("INTEGER");
        config.setEditable(editable);
        return config;
    }

    @Test
    @DisplayName("DB 已有可编辑记录 → 改值后 saveOrUpdate 落库，缓存即时生效")
    void updatesExistingEditableConfigAndRefreshesCache() {
        when(systemConfigGateway.findByKey(KNOWN_KEY))
                .thenReturn(Optional.of(existing(KNOWN_KEY, "80", true)));

        exe.execute(KNOWN_KEY, "90");

        ArgumentCaptor<SystemConfig> cap = ArgumentCaptor.forClass(SystemConfig.class);
        verify(systemConfigGateway, times(1)).saveOrUpdate(cap.capture());
        assertThat(cap.getValue().getConfigValue()).isEqualTo("90");
        assertThat(cap.getValue().getConfigKey()).isEqualTo(KNOWN_KEY);
    }

    @Test
    @DisplayName("未注册 key → 白名单校验拒绝 'Unknown system config key'，不落库")
    void unknownKeyRejectedByWhitelist() {
        assertThatThrownBy(() -> exe.execute("not.a.registered.key", "1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown system config key");
        verify(systemConfigGateway, never()).saveOrUpdate(any());
        verify(systemConfigGateway, never()).findByKey(anyString());
    }

    @Test
    @DisplayName("默认值已注册但 DB 缺行 → 按注册元数据创建可编辑新记录并落库")
    void createsRecordFromDefaultsWhenDbMissing() {
        when(systemConfigGateway.findByKey(KNOWN_KEY)).thenReturn(Optional.empty());

        exe.execute(KNOWN_KEY, "75");

        ArgumentCaptor<SystemConfig> cap = ArgumentCaptor.forClass(SystemConfig.class);
        verify(systemConfigGateway).saveOrUpdate(cap.capture());
        SystemConfig created = cap.getValue();
        assertThat(created.getConfigValue()).isEqualTo("75");
        assertThat(created.getValueType()).isEqualTo("INTEGER");
        assertThat(created.getCategory()).isEqualTo("feature-toggle");
        assertThat(created.getEditable()).isTrue();
    }

    @Test
    @DisplayName("记录标记 editable=false → 抛只读异常，不落库")
    void readOnlyConfigRejected() {
        when(systemConfigGateway.findByKey(KNOWN_KEY))
                .thenReturn(Optional.of(existing(KNOWN_KEY, "80", false)));

        assertThatThrownBy(() -> exe.execute(KNOWN_KEY, "90"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("read-only");
        verify(systemConfigGateway, never()).saveOrUpdate(any());
    }
}
