package com.mouhin.knowledge.repository.migration;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 唯一键租户内化验证（P1-T5 验收）。
 *
 * <p>V25 把 {@code kb_category.name} 等唯一键从"全局唯一"改为 {@code UNIQUE(tenant_id, key)}。本用例在 H2（MySQL 模式）
 * 回放全链后，验证核心不变式：<b>同一业务键在不同租户可共存，但在同一租户内仍唯一</b>——这正是单租户全局唯一所不允许、而多租户必须放开的语义。
 *
 * <p>刻意不起 Spring 上下文：仅需 schema 语义，直连内存库回放迁移 + 原生 SQL 断言，快速确定。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@DisplayName("唯一键租户内化 (P1-T5)")
class TenantUniqueKeyDuplicationTest {

    private static final String JDBC_URL =
            "jdbc:h2:mem:kr-ukdup;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

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

    private void insertCategory(String name, long tenantId) throws SQLException {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
                PreparedStatement ps =
                        conn.prepareStatement(
                                "INSERT INTO kb_category (name, sort_order, tenant_id) VALUES (?, 0, ?)")) {
            ps.setString(1, name);
            ps.setLong(2, tenantId);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("同名分类跨租户可共存（全局唯一→租户内唯一的关键放开）")
    void sameKeyDifferentTenantsCoexist() throws SQLException {
        // V4 种子已在 default 租户下建了 '工作'（V24 回填），acme 再建同名应成功
        assertDoesNotThrow(
                () -> insertCategory("工作", acmeTenantId), "acme 租户应与 default 租户共用分类名 '工作'");
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
                PreparedStatement ps =
                        conn.prepareStatement("SELECT COUNT(*) FROM kb_category WHERE name = ?")) {
            ps.setString(1, "工作");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1), "'工作' 应同时存在于 default 与 acme 两个租户（跨租户同名不再被拒）");
            }
        }
    }

    @Test
    @DisplayName("同租户内重复键仍被唯一约束拒绝（隔离粒度未丢失）")
    void sameKeySameTenantRejected() {
        // '学习' 已在 default 租户存在（V4 种子 + V24 回填）
        assertThrows(
                SQLException.class,
                () -> insertCategory("学习", defaultTenantId),
                "default 租户内重复分类名应违反 UNIQUE(tenant_id, name)");
    }
}
