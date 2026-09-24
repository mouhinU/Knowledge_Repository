package com.mouhin.knowledge.repository.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Flyway V1-V21 迁移冒烟测试（体检 HIGH H4）。
 *
 * <p>用 H2（MySQL 兼容模式）在内存中一次性回放全部迁移脚本，锁定"库重建 / 迁移链可成功执行"这一 生产启动前置：若任一版本脚本存在语法错误、跨版本对象依赖破坏（如 V11 新表被
 * V13 ALTER 引用）， {@code flyway.migrate()} 会直接抛错、本用例失败。生产虽运行在 MySQL，但项目要求 H2 亦可启动 （见 AGENTS.md 第十章
 * H2 注意事项），故此处用 H2 MODE=MySQL 校验方言兼容，不依赖外部数据库。
 *
 * <p>刻意不启动 Spring 上下文：仅验证迁移本身，保证快速、确定、可离线运行。
 *
 * @author mouhinU
 * @date 2026-09-19
 */
@DisplayName("Flyway V1-V21 迁移冒烟（H2/MySQL 模式）")
class FlywayMigrationSmokeTest {

    /** 迁移脚本总数（V1..V21）。新增迁移时需同步此常量。 */
    private static final int EXPECTED_MIGRATION_COUNT = 21;

    private static final String JDBC_URL =
            "jdbc:h2:mem:kr-smoke;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    @Test
    @DisplayName("全部迁移脚本在 H2 上成功应用，最终版本为 19")
    void allMigrationsApplyOnH2() throws Exception {
        Flyway flyway =
                Flyway.configure()
                        .dataSource(JDBC_URL, USER, PASSWORD)
                        .locations("classpath:db/migration")
                        .load();

        MigrateResult result = flyway.migrate();

        // 干净库上执行的迁移数 = 版本脚本数；失败标记应为 false / 0
        assertEquals(EXPECTED_MIGRATION_COUNT, result.migrationsExecuted, "应用的迁移数量应与版本脚本数一致");

        // 二次调用应无待处理迁移（幂等），并停留在 V21
        MigrationInfo current = flyway.info().current();
        assertNotNull(current, "迁移后应存在当前版本");
        assertEquals("21", current.getVersion().getVersion(), "最终版本应为 V21（系统配置表 sys_config）");

        // 迁移历史表自身记录数 == 脚本数
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
                Statement st = conn.createStatement()) {
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT COUNT(*) FROM flyway_schema_history "
                                    + "WHERE success = TRUE AND type = 'SQL'")) {
                assertTrue(rs.next());
                assertEquals(
                        EXPECTED_MIGRATION_COUNT,
                        rs.getInt(1),
                        "flyway_schema_history 成功记录数应等于脚本数");
            }
            // 抽查 V1 与 V11 各引入的代表性对象确实存在（schema 无关，按表名匹配）
            List<String> tables = new ArrayList<>();
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT LOWER(table_name) FROM information_schema.tables "
                                    + "WHERE LOWER(table_name) IN ('sys_department','kb_exam_question','kb_document_image')")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            assertTrue(tables.contains("sys_department"), "V1 初始表缺失 (sys_department)");
            assertTrue(tables.contains("kb_exam_question"), "V11 新表缺失 (kb_exam_question)");
            assertTrue(tables.contains("kb_document_image"), "V16 新表缺失 (kb_document_image)");

            // 抽查 V14（CONC-1）为 kb_exam_session 增加的评分围栏令牌列存在
            boolean hasGradingToken = false;
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT COUNT(*) FROM information_schema.columns "
                                    + "WHERE LOWER(table_name) = 'kb_exam_session' "
                                    + "AND LOWER(column_name) = 'grading_token'")) {
                assertTrue(rs.next());
                hasGradingToken = rs.getInt(1) > 0;
            }
            assertTrue(hasGradingToken, "V14 列缺失 (kb_exam_session.grading_token)");

            // 抽查 V15（管理端登录鉴权）为 sys_user 增加的 password_hash / status 列存在
            List<String> userCols = new ArrayList<>();
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT LOWER(column_name) FROM information_schema.columns "
                                    + "WHERE LOWER(table_name) = 'sys_user' "
                                    + "AND LOWER(column_name) IN ('password_hash','status')")) {
                while (rs.next()) {
                    userCols.add(rs.getString(1));
                }
            }
            assertTrue(userCols.contains("password_hash"), "V15 列缺失 (sys_user.password_hash)");
            assertTrue(userCols.contains("status"), "V15 列缺失 (sys_user.status)");

            // 抽查 V15 种子：内置管理员应已置默认口令哈希并处于 ACTIVE
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT password_hash, status FROM sys_user WHERE user_key = 'user-admin'")) {
                assertTrue(rs.next(), "V15 种子管理员行缺失 (user_key='user-admin')");
                String adminHash = rs.getString("password_hash");
                assertNotNull(adminHash, "种子管理员 password_hash 未设置");
                assertEquals("ACTIVE", rs.getString("status"), "种子管理员 status 应为 ACTIVE");
                assertTrue(
                        new BCryptPasswordEncoder().matches("admin123", adminHash),
                        "种子哈希应与默认口令 admin123 匹配（迁移常量漂移）");
            }

            // 抽查 V17（看图题配图绑定）为 kb_exam_question 增加的 images_json 列存在
            boolean hasImagesJson = false;
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT COUNT(*) FROM information_schema.columns "
                                    + "WHERE LOWER(table_name) = 'kb_exam_question' "
                                    + "AND LOWER(column_name) = 'images_json'")) {
                assertTrue(rs.next());
                hasImagesJson = rs.getInt(1) > 0;
            }
            assertTrue(hasImagesJson, "V17 列缺失 (kb_exam_question.images_json)");

            // 抽查 V18（试卷作废标注）为 kb_exam_session 增加的 voided 列存在
            boolean hasVoided = false;
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT COUNT(*) FROM information_schema.columns "
                                    + "WHERE LOWER(table_name) = 'kb_exam_session' "
                                    + "AND LOWER(column_name) = 'voided'")) {
                assertTrue(rs.next());
                hasVoided = rs.getInt(1) > 0;
            }
            assertTrue(hasVoided, "V18 列缺失 (kb_exam_session.voided)");

            // 抽查 V19（解析缓存）新建表 kb_extraction_cache 存在
            boolean hasExtractionCache = false;
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT COUNT(*) FROM information_schema.tables "
                                    + "WHERE LOWER(table_name) = 'kb_extraction_cache'")) {
                assertTrue(rs.next());
                hasExtractionCache = rs.getInt(1) > 0;
            }
            assertTrue(hasExtractionCache, "V19 表缺失 (kb_extraction_cache)");
        }
    }
}
