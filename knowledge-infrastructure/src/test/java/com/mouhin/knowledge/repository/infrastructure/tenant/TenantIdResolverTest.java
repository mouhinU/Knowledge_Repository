package com.mouhin.knowledge.repository.infrastructure.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.TenantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link TenantIdResolver} 单元测试（P1-T7）。
 *
 * <p>覆盖：cache miss → DB 回源 → 二次读命中；大小写归一化；blank/未知/非 ACTIVE 三种异常路径； invalidate/clear 语义。使用 {@link
 * MockitoExtension} 隔离 {@link TenantMapper}。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenantIdResolver 缓存/异常语义")
class TenantIdResolverTest {

    @Mock private TenantMapper tenantMapper;

    private TenantIdResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TenantIdResolver(tenantMapper);
    }

    private static TenantDO activeRow(long id, String key) {
        TenantDO row = new TenantDO();
        row.setTenantId(id);
        row.setTenantKey(key);
        row.setStatus(TenantDO.STATUS_ACTIVE);
        return row;
    }

    @Nested
    @DisplayName("正常解析路径")
    class HappyPath {

        @Test
        @DisplayName("首次解析回源 DB，二次读缓存")
        void cachesAfterFirstHit() {
            when(tenantMapper.selectOne(any(Wrapper.class))).thenReturn(activeRow(1L, "default"));

            long first = resolver.resolve("default");
            long second = resolver.resolve("default");

            assertThat(first).isEqualTo(1L);
            assertThat(second).isEqualTo(1L);
            verify(tenantMapper, times(1)).selectOne(any(Wrapper.class));
        }

        @Test
        @DisplayName("tenant_key 大小写/前后空格归一化后共享缓存")
        void normalizesKey() {
            when(tenantMapper.selectOne(any(Wrapper.class))).thenReturn(activeRow(42L, "acme"));

            long a = resolver.resolve("ACME");
            long b = resolver.resolve("  acme  ");

            assertThat(a).isEqualTo(42L);
            assertThat(b).isEqualTo(42L);
            verify(tenantMapper, times(1)).selectOne(any(Wrapper.class));
        }

        @Test
        @DisplayName("不同 key 各自回源")
        void distinctKeysCachedSeparately() {
            when(tenantMapper.selectOne(any(Wrapper.class)))
                    .thenReturn(activeRow(1L, "default"))
                    .thenReturn(activeRow(2L, "acme"));

            assertThat(resolver.resolve("default")).isEqualTo(1L);
            assertThat(resolver.resolve("acme")).isEqualTo(2L);
            verify(tenantMapper, times(2)).selectOne(any(Wrapper.class));
        }
    }

    @Nested
    @DisplayName("异常路径")
    class Failures {

        @Test
        @DisplayName("null/blank 键抛 IllegalStateException 且不触库")
        void blankKeyRejected() {
            assertThatThrownBy(() -> resolver.resolve(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("blank");
            assertThatThrownBy(() -> resolver.resolve("   "))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("blank");
            verifyNoInteractions(tenantMapper);
        }

        @Test
        @DisplayName("未知 key 抛 IllegalStateException (Unknown tenant_key)")
        void unknownKeyRejected() {
            when(tenantMapper.selectOne(any(Wrapper.class))).thenReturn(null);

            assertThatThrownBy(() -> resolver.resolve("ghost"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Unknown tenant_key");
        }

        @Test
        @DisplayName("非 ACTIVE 状态租户抛 IllegalStateException (status 提示)")
        void nonActiveTenantRejected() {
            TenantDO suspended = new TenantDO();
            suspended.setTenantId(9L);
            suspended.setTenantKey("suspendedcorp");
            suspended.setStatus(TenantDO.STATUS_SUSPENDED);
            when(tenantMapper.selectOne(any(Wrapper.class))).thenReturn(suspended);

            assertThatThrownBy(() -> resolver.resolve("suspendedcorp"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not ACTIVE")
                    .hasMessageContaining("SUSPENDED");
        }
    }

    @Nested
    @DisplayName("缓存失效钩子")
    class Invalidation {

        @Test
        @DisplayName("invalidateByKey 后下次 resolve 会回源 DB")
        void invalidateEvictsSingleKey() {
            when(tenantMapper.selectOne(any(Wrapper.class)))
                    .thenReturn(activeRow(1L, "default"))
                    .thenReturn(activeRow(101L, "default"));

            assertThat(resolver.resolve("default")).isEqualTo(1L);
            resolver.invalidateByKey("DEFAULT");
            assertThat(resolver.resolve("default")).isEqualTo(101L);
            verify(tenantMapper, times(2)).selectOne(any(Wrapper.class));
        }

        @Test
        @DisplayName("invalidateAll 全量清空缓存")
        void clearEmptiesCache() {
            when(tenantMapper.selectOne(any(Wrapper.class)))
                    .thenReturn(activeRow(1L, "default"))
                    .thenReturn(activeRow(2L, "acme"));

            assertThat(resolver.resolve("default")).isEqualTo(1L);
            assertThat(resolver.resolve("acme")).isEqualTo(2L);
            resolver.invalidateAll();
            assertThat(resolver.resolve("default")).isIn(1L, 2L);
            verify(tenantMapper, times(3)).selectOne(any(Wrapper.class));
        }
    }
}
