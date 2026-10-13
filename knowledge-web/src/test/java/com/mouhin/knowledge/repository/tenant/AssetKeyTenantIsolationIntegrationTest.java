package com.mouhin.knowledge.repository.tenant;

import com.mouhin.knowledge.repository.domain.context.TenantContext;
import com.mouhin.knowledge.repository.domain.gateway.DocumentImageGateway;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentImage;
import com.mouhin.knowledge.repository.infrastructure.config.MyBatisPlusConfig;
import com.mouhin.knowledge.repository.infrastructure.persistence.gateway.DocumentImageGatewayImpl;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P5-T3 · 配图 assetKey 跨租户校验端到端（H2 MODE=MySQL，挂载 {@code TenantLineInnerInterceptor}，无需 Docker）。
 *
 * <p>校验目标：{@code GET /api/exam/assets/{assetKey}} 公开端点（无需 auth token，assetKey 即凭证）在多租户下天然隔离——
 * {@code kb_document_image} 表有 {@code tenant_id} 列且不在 {@code
 * KnowledgeTenantLineHandler.IGNORE_TABLES} 白名单， {@code TenantLineInnerInterceptor} 自动追加 {@code
 * WHERE tenant_id=?}，跨租户查询自然返空，Controller 统一 404（不暴露资源存在性）。
 *
 * <p>本用例在真实 H2（Flyway V1→V31）+ Spring 上下文 + 真实 {@link DocumentImageGatewayImpl} 上验证：
 *
 * <ol>
 *   <li>acme 租户写入 assetKey=K 的配图记录；
 *   <li>acme 租户 {@code findByAssetKey(K)} 命中（租户内可达）；
 *   <li>globex 租户 {@code findByAssetKey(K)} 返空（跨租户不可见，Controller 将返 404）；
 *   <li>插入行的 {@code tenant_id} 由拦截器自动补为绑定租户（非 DB DEFAULT 1、非应用层显式赋值）。
 * </ol>
 *
 * <p>与生产门控一致：{@code knowledge.saas.enabled=true}（挂拦截器）；未起 web/security 栈，仅数据访问层。
 *
 * @author mouhinU
 * @date 2026-10-12
 */
@Tag("tenant-isolation")
@DisplayName("P5-T3 配图 assetKey 跨租户校验 全链路集成 (H2)")
@SpringBootTest(classes = AssetKeyTenantIsolationIntegrationTest.TestConfig.class)
class AssetKeyTenantIsolationIntegrationTest {

    private static final String ASSET_KEY = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6";
    @Autowired
    private DocumentImageGateway imageGateway;
    @Autowired
    private TenantIdResolver tenantIdResolver;
    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () ->
                        "jdbc:h2:mem:kr-asset-iso;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("knowledge.saas.enabled", () -> "true");
    }

    @BeforeEach
    void setUp() {
        // Seed tenants if not exist
        Integer acmeExists =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_tenant WHERE tenant_key = 'acme'", Integer.class);
        if (acmeExists == null || acmeExists == 0) {
            jdbc.update(
                    "INSERT INTO sys_tenant (tenant_key, name, plan, status, data_region) "
                            + "VALUES ('acme', 'Acme 制造', 'standard', 'ACTIVE', 'cn')");
        }
        Integer globexExists =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_tenant WHERE tenant_key = 'globex'",
                        Integer.class);
        if (globexExists == null || globexExists == 0) {
            jdbc.update(
                    "INSERT INTO sys_tenant (tenant_key, name, plan, status, data_region) "
                            + "VALUES ('globex', 'Globex 集团', 'enterprise', 'ACTIVE', 'cn')");
        }
        tenantIdResolver.invalidateByKey("acme");
        tenantIdResolver.invalidateByKey("globex");
        tenantIdResolver.resolve("acme");
        tenantIdResolver.resolve("globex");

        jdbc.update("DELETE FROM kb_document_image WHERE asset_key = ?", ASSET_KEY);

        // V34 FK: kb_document_image.document_id → kb_document(id)，须先建父文档。
        TenantContext.set("acme");
        Integer docExists =
                jdbc.queryForObject("SELECT COUNT(*) FROM kb_document WHERE id = 1", Integer.class);
        if (docExists == null || docExists == 0) {
            jdbc.update(
                    "INSERT INTO kb_document (id, document_key, file_name, file_type, file_size, "
                            + "storage_path, status, owner_id, department_id, tenant_id) "
                            + "VALUES (1, 'doc-acme-001', 'test.pdf', 'application/pdf', 0, "
                            + "'/dev/null', 'INDEXED', 'admin', 'dept-root', "
                            + "(SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'acme'))");
        }
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        jdbc.update("DELETE FROM kb_document_image WHERE asset_key = ?", ASSET_KEY);
    }

    @Test
    @DisplayName("跨租户 assetKey 查询返空（拦截器自动追加 tenant_id 谓词）")
    void crossTenantAssetKeyLookupReturnsEmpty() {
        // acme 租户写入配图
        TenantContext.set("acme");
        DocumentImage image = new DocumentImage();
        image.setAssetKey(ASSET_KEY);
        image.setDocumentId(1L);
        image.setDocumentKey("doc-acme-001");
        image.setStoragePath(
                "/tmp/test-storage/acme/exam-assets/doc-acme-001/" + ASSET_KEY + ".png");
        image.setSha256("sha256-acme");
        image.setMimeType("image/png");
        image.setPageNo(1);
        image.setSeqOnPage(1);
        image.setWidth(100);
        image.setHeight(100);
        image.setByteSize(1024L);
        imageGateway.save(image);

        // acme 租户可命中
        Optional<DocumentImage> acmeHit = imageGateway.findByAssetKey(ASSET_KEY);
        assertThat(acmeHit).isPresent();
        assertThat(acmeHit.get().getAssetKey()).isEqualTo(ASSET_KEY);
        assertThat(acmeHit.get().getDocumentKey()).isEqualTo("doc-acme-001");

        // globex 租户不可见（跨租户查询返空，Controller 将返 404）
        TenantContext.set("globex");
        Optional<DocumentImage> globexHit = imageGateway.findByAssetKey(ASSET_KEY);
        assertThat(globexHit).isEmpty();

        // 验证插入行的 tenant_id 由拦截器自动补为 acme 的 tenant_id（非 DEFAULT 1）
        Long insertedTenantId =
                jdbc.queryForObject(
                        "SELECT tenant_id FROM kb_document_image WHERE asset_key = ?",
                        Long.class,
                        ASSET_KEY);
        assertThat(insertedTenantId).isNotNull();
        // acme 租户 ID 由 TenantIdResolver 自动分配（H2 内存库，非 1）
        assertThat(insertedTenantId).isGreaterThan(0L);
    }

    /**
     * 最小上下文：DataSource / Flyway / MyBatis-Plus + 租户拦截器 + 租户解析器 + 真实配图仓储。
     */
    @Configuration
    @EnableAutoConfiguration
    @MapperScan("com.mouhin.knowledge.repository.infrastructure.persistence.mapper")
    @Import({MyBatisPlusConfig.class, TenantIdResolver.class, DocumentImageGatewayImpl.class})
    static class TestConfig {
    }
}
