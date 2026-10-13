package com.mouhin.knowledge.repository.tenant;

import com.mouhin.knowledge.repository.domain.context.TenantContext;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionCacheKey;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.infrastructure.config.MyBatisPlusConfig;
import com.mouhin.knowledge.repository.infrastructure.persistence.gateway.ExtractionCacheRepositoryImpl;
import com.mouhin.knowledge.repository.infrastructure.tenant.TenantIdResolver;
import org.junit.jupiter.api.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P4-T3 · 解析缓存跨租户隔离端到端（H2 MODE=MySQL，挂载 {@code TenantLineInnerInterceptor}，无需 Docker）。
 *
 * <p>修复目标 = 提案 §11 G7：{@code kb_extraction_cache} 曾在全局唯一键下、且被列在租户拦截器 ignore 白名单，导致 A
 * 租户已解析过的同一文件（内容摘要 + 策略 + 模型 + 提示词摘要 + 渲染模式一致），B 租户 {@code find} 直接命中并读到 A 的 {@code
 * result_json}。V30 加 {@code tenant_id} 列并把唯一键改为租户内、{@code KnowledgeTenantLineHandler} 已将本表移出 ignore
 * 名单，读写由拦截器自动追加 {@code tenant_id} 谓词与列值。
 *
 * <p>本用例在真实 H2（Flyway V1→V30）+ Spring 上下文 + 真实 {@link ExtractionCacheRepositoryImpl} / {@code
 * ExtractionCacheMapper} / {@link MyBatisPlusConfig} 拦截器上验证：
 *
 * <ol>
 *   <li>default 租户写入业务键 K；acme 租户 {@code find(K)} 返空（跨租户不再命中，泄漏已闭合）；
 *   <li>acme 租户另以同一 K 写入自身结果，两行以 {@code tenant_id} 区分共存（租户内唯一键生效）；
 *   <li>default 租户 {@code find(K)} 仍得自身结果，未被 acme 覆盖（互不污染）；
 *   <li>插入行的 {@code tenant_id} 由拦截器自动补为绑定租户（非 DB DEFAULT 1、非应用层显式赋值）。
 * </ol>
 *
 * <p>与生产门控一致：{@code knowledge.saas.enabled=true}（挂拦截器）；未起 web/security 栈，仅数据访问层。
 *
 * @author mouhinU
 * @date 2026-10-10
 */
@Tag("config-isolation")
@DisplayName("P4-T3 解析缓存跨租户隔离 全链路集成 (H2)")
@SpringBootTest(classes = ExtractionCacheTenantIsolationIntegrationTest.TestConfig.class)
class ExtractionCacheTenantIsolationIntegrationTest {

    private static final ExtractionCacheKey KEY =
            new ExtractionCacheKey("b".repeat(32), "VISION", "gpt-4o", "q".repeat(32), "raster");
    @Autowired
    private ExtractionCacheRepositoryImpl extractionCacheRepository;
    @Autowired
    private TenantIdResolver tenantIdResolver;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () ->
                        "jdbc:h2:mem:kr-extr-cache-iso;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("knowledge.saas.enabled", () -> "true");
    }

    @BeforeEach
    void seedSecondTenant() {
        Integer exists =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM sys_tenant WHERE tenant_key = 'acme'", Integer.class);
        if (exists == null || exists == 0) {
            jdbcTemplate.update(
                    "INSERT INTO sys_tenant (tenant_key, name, plan, status, data_region) "
                            + "VALUES ('acme', 'Acme 制造', 'standard', 'ACTIVE', 'cn')");
        }
        tenantIdResolver.invalidateByKey("acme");
        tenantIdResolver.resolve("acme");
        // 保证业务键 K 在两个租户下都是干净的起点（本用例每次自带独立内存库，此处仅防同名残留）。
        jdbcTemplate.update("DELETE FROM kb_extraction_cache WHERE checksum = ?", KEY.checksum());
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM kb_extraction_cache WHERE checksum = ?", KEY.checksum());
        TenantContext.clear();
    }

    @Test
    @DisplayName("A 租户写入的解析缓存，B 租户以相同业务键查询为空（G7 泄漏闭合）")
    void cacheBuiltByOneTenantInvisibleToAnother() {
        ExtractionResult defaultResult =
                new ExtractionResult(List.of("default 机密解析文本"), 1, false, KEY.checksum(), "pdf");
        ExtractionResult acmeResult =
                new ExtractionResult(List.of("acme 自有解析文本"), 1, false, KEY.checksum(), "pdf");

        // 1) default 建缓存
        try {
            TenantContext.set("default");
            extractionCacheRepository.save(KEY, defaultResult);
        } finally {
            TenantContext.clear();
        }

        // 2) acme 相同业务键应查不到（修复前会命中 default 的 result_json）
        Optional<ExtractionResult> crossTenantHit;
        try {
            TenantContext.set("acme");
            crossTenantHit = extractionCacheRepository.find(KEY);
            // 3) acme 以同一业务键写入自身结果，应与 default 行按 tenant_id 共存
            extractionCacheRepository.save(KEY, acmeResult);
        } finally {
            TenantContext.clear();
        }
        assertThat(crossTenantHit).as("acme 不应命中 default 租户写入的缓存（G7 跨租户泄漏已修复）").isEmpty();

        // 4) default 再查仍得自身结果，未被 acme 覆盖
        Optional<ExtractionResult> defaultReadBack;
        try {
            TenantContext.set("default");
            defaultReadBack = extractionCacheRepository.find(KEY);
        } finally {
            TenantContext.clear();
        }
        assertThat(defaultReadBack)
                .as("default 应仍读到自身缓存")
                .isPresent()
                .hasValueSatisfying(
                        r -> assertThat(r.pageTexts()).containsExactly("default 机密解析文本"));

        // 5) acme 读回自身结果
        Optional<ExtractionResult> acmeReadBack;
        try {
            TenantContext.set("acme");
            acmeReadBack = extractionCacheRepository.find(KEY);
        } finally {
            TenantContext.clear();
        }
        assertThat(acmeReadBack)
                .as("acme 应读到自身缓存")
                .isPresent()
                .hasValueSatisfying(r -> assertThat(r.pageTexts()).containsExactly("acme 自有解析文本"));

        // 6) 拦截器自动写入 tenant_id：两行以不同 tenant_id 共存
        long defaultId = tenantIdResolver.resolve("default");
        long acmeId = tenantIdResolver.resolve("acme");
        assertThat(acmeId).isNotEqualTo(defaultId);
        List<Long> tenantIds =
                jdbcTemplate.query(
                        "SELECT tenant_id FROM kb_extraction_cache WHERE checksum = ? ORDER BY tenant_id",
                        (rs, i) -> rs.getLong(1),
                        KEY.checksum());
        assertThat(tenantIds)
                .as("同一业务键应在两个租户下各存一行，tenant_id 由拦截器自动补齐")
                .containsExactlyInAnyOrder(defaultId, acmeId);
    }

    /**
     * 最小上下文：DataSource / Flyway / MyBatis-Plus + 租户拦截器 + 租户解析器 + 真实缓存仓储。
     */
    @Configuration
    @EnableAutoConfiguration
    @MapperScan("com.mouhin.knowledge.repository.infrastructure.persistence.mapper")
    @Import({MyBatisPlusConfig.class, TenantIdResolver.class, ExtractionCacheRepositoryImpl.class})
    static class TestConfig {
    }
}
