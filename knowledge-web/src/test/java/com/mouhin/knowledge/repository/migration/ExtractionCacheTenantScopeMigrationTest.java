package com.mouhin.knowledge.repository.migration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code kb_extraction_cache} 租户内化验证（P4-T3 验收，V30）。
 *
 * <p>V30 把解析结果缓存的唯一键从「全局唯一」{@code (checksum, strategy, model_name, prompt_hash, render_mode)} 切换为
 * 「租户内唯一」{@code (tenant_id, …)}，并在表上新增 {@code tenant_id} 列。本用例在 H2（MODE=MySQL）回放 V1→V30 全链后，用原生 SQL
 * 断言与 P4-T3 直接相关的两条 schema 不变式：
 *
 * <ol>
 *   <li>缓存表已具备 {@code tenant_id} 列（V30 加列生效）；
 *   <li>同一业务键（文件摘要 + 策略 + 模型 + 提示词摘要 + 渲染模式）在不同租户下可共存（跨租户不再相互覆盖）， 但在同一租户内仍被唯一约束拒绝（隔离粒度未丢失）。
 * </ol>
 *
 * <p>刻意不起 Spring 上下文、不用拦截器：仅验 schema 语义（索引切换是否无损、列是否存在），快速确定。应用层经 {@code
 * TenantLineInnerInterceptor} 的读写自动隔离行为由 {@code ExtractionCacheTenantIsolationIntegrationTest} 覆盖。
 *
 * @author mouhinU
 * @date 2026-10-10
 */
@DisplayName("kb_extraction_cache 租户内化 (P4-T3 / V30)")
class ExtractionCacheTenantScopeMigrationTest {

    private static final String JDBC_URL =
            "jdbc:h2:mem:kr-extr-cache-p4t3;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    /** 业务键：文件摘要 + 策略 + 模型 + 提示词摘要 + 渲染模式，跨租户复用同一组值以验证唯一键租户内化。 */
    private static final String CHECKSUM = "a".repeat(32);

    private static final String STRATEGY = "VISION";
    private static final String MODEL = "gpt-4o";
    private static final String PROMPT_HASH = "p".repeat(32);
    private static final String RENDER_MODE = "raster";

    private static long defaultTenantId;
    private static long acmeTenantId;

    @BeforeAll
    static void migrateAndSeed() {
        Flyway.configure()
                .dataSource(JDBC_URL, USER, PASSWORD)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
                Statement st = conn.createStatement()) {
            // 默认租户由 V23 种子提供
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'default'")) {
                assertTrue(rs.next(), "默认租户种子缺失");
                defaultTenantId = rs.getLong(1);
            }
            // 追加第二个租户，用于跨租户同键共存验证
            st.executeUpdate(
                    "INSERT INTO sys_tenant (tenant_key, name, plan, status, data_region) "
                            + "VALUES ('acme', 'Acme', 'standard', 'ACTIVE', 'cn')");
            try (ResultSet rs =
                    st.executeQuery("SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'acme'")) {
                assertTrue(rs.next(), "acme 租户插入失败");
                acmeTenantId = rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("V30 已为 kb_extraction_cache 增加 tenant_id 列")
    void tenantIdColumnExists() throws SQLException {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
                Statement st = conn.createStatement()) {
            st.execute("SELECT tenant_id FROM kb_extraction_cache WHERE 1 = 0");
        }
    }

    @Test
    @DisplayName("同一业务键跨租户可共存（旧全局唯一 → 租户内唯一的关键放开）")
    void sameBusinessKeyDifferentTenantsCoexist() throws SQLException {
        assertDoesNotThrow(() -> insertCache(defaultTenantId, "default 结果"), "default 租户写入业务键应成功");
        assertDoesNotThrow(
                () -> insertCache(acmeTenantId, "acme 结果"), "acme 租户应以相同业务键共存（不再撞旧全局唯一键，修复跨租户覆盖）");
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
                PreparedStatement ps =
                        conn.prepareStatement(
                                "SELECT COUNT(*) FROM kb_extraction_cache "
                                        + "WHERE checksum = ? AND strategy = ?")) {
            ps.setString(1, CHECKSUM);
            ps.setString(2, STRATEGY);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1), "同一业务键应同时存在于 default 与 acme 两个租户");
            }
        }
    }

    @Test
    @DisplayName("同租户内重复业务键仍被唯一约束拒绝（隔离粒度未丢失）")
    void sameBusinessKeySameTenantRejected() throws SQLException {
        long probeTenant = 999_999L;
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASSWORD)) {
            insertCacheForTenant(conn, probeTenant, "首次");
            assertThrows(
                    SQLException.class,
                    () -> insertCacheForTenant(conn, probeTenant, "重复"),
                    "同租户重复业务键应违反 UNIQUE(tenant_id, checksum, strategy, model_name, prompt_hash, render_mode)");
        } finally {
            try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
                    Statement st = conn.createStatement()) {
                st.executeUpdate(
                        "DELETE FROM kb_extraction_cache WHERE tenant_id = " + probeTenant);
            }
        }
    }

    private void insertCache(long tenantId, String payload) throws SQLException {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASSWORD)) {
            insertCacheForTenant(conn, tenantId, payload);
        }
    }

    private void insertCacheForTenant(Connection conn, long tenantId, String payload)
            throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement(
                        "INSERT INTO kb_extraction_cache "
                                + "(checksum, strategy, model_name, prompt_hash, render_mode, result_json, tenant_id) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, CHECKSUM);
            ps.setString(2, STRATEGY);
            ps.setString(3, MODEL);
            ps.setString(4, PROMPT_HASH);
            ps.setString(5, RENDER_MODE);
            ps.setString(6, "{\"payload\":\"" + payload + "\"}");
            ps.setLong(7, tenantId);
            ps.executeUpdate();
        }
    }
}
