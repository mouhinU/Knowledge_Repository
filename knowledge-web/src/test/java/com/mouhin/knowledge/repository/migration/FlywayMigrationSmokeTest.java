package com.mouhin.knowledge.repository.migration;

import static org.junit.jupiter.api.Assertions.*;

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
 * Flyway V1-V26 迁移冒烟测试（体检 HIGH H4）。
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
@DisplayName("Flyway V1-V35 迁移冒烟（H2/MySQL 模式）")
class FlywayMigrationSmokeTest {

    /** 迁移脚本总数（V1..V35）。新增迁移时需同步此常量。 */
    private static final int EXPECTED_MIGRATION_COUNT = 35;

    private static final String JDBC_URL =
            "jdbc:h2:mem:kr-smoke;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    @Test
    @DisplayName("全部迁移脚本在 H2 上成功应用，最终版本为 35")
    void allMigrationsApplyOnH2() throws Exception {
        Flyway flyway =
                Flyway.configure()
                        .dataSource(JDBC_URL, USER, PASSWORD)
                        .locations("classpath:db/migration")
                        .load();

        MigrateResult result = flyway.migrate();

        // 干净库上执行的迁移数 = 版本脚本数；失败标记应为 false / 0
        assertEquals(EXPECTED_MIGRATION_COUNT, result.migrationsExecuted, "应用的迁移数量应与版本脚本数一致");

        // 二次调用应无待处理迁移（幂等），并停留在 V30
        MigrationInfo current = flyway.info().current();
        assertNotNull(current, "迁移后应存在当前版本");
        assertEquals("35", current.getVersion().getVersion(), "最终版本应为 V35（种子运行时配置键）");

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

            // 抽查 V22（P0-T5 学生多会话）新建表 kb_student_session 及关键列存在
            boolean hasStudentSession = false;
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT COUNT(*) FROM information_schema.tables "
                                    + "WHERE LOWER(table_name) = 'kb_student_session'")) {
                assertTrue(rs.next());
                hasStudentSession = rs.getInt(1) > 0;
            }
            assertTrue(hasStudentSession, "V22 表缺失 (kb_student_session)");

            List<String> sessionCols = new ArrayList<>();
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT LOWER(column_name) FROM information_schema.columns "
                                    + "WHERE LOWER(table_name) = 'kb_student_session' "
                                    + "AND LOWER(column_name) IN "
                                    + "('session_token','student_id','expires_at','revoked_at')")) {
                while (rs.next()) {
                    sessionCols.add(rs.getString(1));
                }
            }
            assertTrue(
                    sessionCols.contains("session_token"),
                    "V22 列缺失 (kb_student_session.session_token)");
            assertTrue(
                    sessionCols.contains("student_id"), "V22 列缺失 (kb_student_session.student_id)");
            assertTrue(
                    sessionCols.contains("expires_at"), "V22 列缺失 (kb_student_session.expires_at)");
            assertTrue(
                    sessionCols.contains("revoked_at"), "V22 列缺失 (kb_student_session.revoked_at)");

            // 抽查 V23（P1-T1 SaaS 租户元数据）两张平台表存在
            List<String> tenantTables = new ArrayList<>();
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT LOWER(table_name) FROM information_schema.tables "
                                    + "WHERE LOWER(table_name) IN ('sys_tenant','sys_tenant_placement')")) {
                while (rs.next()) {
                    tenantTables.add(rs.getString(1));
                }
            }
            assertTrue(tenantTables.contains("sys_tenant"), "V23 表缺失 (sys_tenant)");
            assertTrue(
                    tenantTables.contains("sys_tenant_placement"),
                    "V23 表缺失 (sys_tenant_placement)");

            // 抽查 V23 关键列存在
            List<String> tenantCols = new ArrayList<>();
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT LOWER(column_name) FROM information_schema.columns "
                                    + "WHERE LOWER(table_name) = 'sys_tenant' "
                                    + "AND LOWER(column_name) IN ('tenant_key','plan','status','data_region')")) {
                while (rs.next()) {
                    tenantCols.add(rs.getString(1));
                }
            }
            assertTrue(tenantCols.contains("tenant_key"), "V23 列缺失 (sys_tenant.tenant_key)");
            assertTrue(tenantCols.contains("plan"), "V23 列缺失 (sys_tenant.plan)");
            assertTrue(tenantCols.contains("status"), "V23 列缺失 (sys_tenant.status)");
            assertTrue(tenantCols.contains("data_region"), "V23 列缺失 (sys_tenant.data_region)");

            // 抽查 V23 种子：默认租户已就绪且状态 ACTIVE
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT tenant_id, status FROM sys_tenant WHERE tenant_key = 'default'")) {
                assertTrue(rs.next(), "V23 种子默认租户行缺失 (tenant_key='default')");
                long defaultTenantId = rs.getLong("tenant_id");
                assertEquals("ACTIVE", rs.getString("status"), "默认租户 status 应为 ACTIVE");

                // 默认租户须有对应 POOL 放置
                try (ResultSet prs =
                        st.executeQuery(
                                "SELECT placement_type FROM sys_tenant_placement WHERE tenant_id = "
                                        + defaultTenantId)) {
                    assertTrue(prs.next(), "V23 默认租户放置记录缺失");
                    assertEquals("POOL", prs.getString("placement_type"), "默认租户应为 POOL 放置");
                }
            }

            // 抽查 V24（P1-T4）：15 张业务表都已加 tenant_id 列
            List<String> tenantIdTables = new ArrayList<>();
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT LOWER(table_name) FROM information_schema.columns "
                                    + "WHERE LOWER(column_name) = 'tenant_id' "
                                    + "AND LOWER(table_name) IN ("
                                    + "'kb_document','kb_document_chunk','kb_document_image','kb_category',"
                                    + "'kb_exam_history','kb_exam_session','kb_exam_question','kb_exam_answer',"
                                    + "'kb_student','kb_student_session','kb_writing_history',"
                                    + "'sys_user','sys_role','sys_user_role','sys_department')")) {
                while (rs.next()) {
                    tenantIdTables.add(rs.getString(1));
                }
            }
            assertEquals(
                    15, tenantIdTables.size(), "V24 应为 15 张业务表添加 tenant_id，实际=" + tenantIdTables);

            // 抽查 V24 回填：种子管理员应已归属默认租户（sys_user.user_key='user-admin' 从 V15 起就存在）
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT u.tenant_id AS assigned, d.tenant_id AS dflt "
                                    + "FROM sys_user u, sys_tenant d "
                                    + "WHERE u.user_key = 'user-admin' AND d.tenant_key = 'default'")) {
                assertTrue(rs.next(), "种子管理员行缺失");
                long assigned = rs.getLong("assigned");
                long dflt = rs.getLong("dflt");
                assertEquals(dflt, assigned, "V24 回填：存量 sys_user 应归属默认租户 id");
            }

            // 抽查 V26（P1-T6）：默认租户 id 必须为 1（V26 硬编码 DEFAULT 1 的前提，避免漂移）
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'default'")) {
                assertTrue(rs.next(), "V23 种子默认租户缺失");
                assertEquals(1L, rs.getLong(1), "V26 依赖默认租户 id=1（DEFAULT 1 硬编码前提）");
            }

            // 抽查 V26（P1-T6）：15 张业务表的 tenant_id 均已收紧为 NOT NULL
            int notNullCount;
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT COUNT(*) FROM information_schema.columns "
                                    + "WHERE LOWER(column_name) = 'tenant_id' "
                                    + "AND UPPER(is_nullable) = 'NO' "
                                    + "AND LOWER(table_name) IN ("
                                    + "'kb_document','kb_document_chunk','kb_document_image','kb_category',"
                                    + "'kb_exam_history','kb_exam_session','kb_exam_question','kb_exam_answer',"
                                    + "'kb_student','kb_student_session','kb_writing_history',"
                                    + "'sys_user','sys_role','sys_user_role','sys_department')")) {
                assertTrue(rs.next());
                notNullCount = rs.getInt(1);
            }
            assertEquals(
                    15, notNullCount, "V26 应将 15 张业务表 tenant_id 全部收紧为 NOT NULL，实际=" + notNullCount);

            // 抽查 V27（P3-T2 开通状态机）：新表 sys_tenant_provision_step 存在
            boolean hasProvisionStep = false;
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT COUNT(*) FROM information_schema.tables "
                                    + "WHERE LOWER(table_name) = 'sys_tenant_provision_step'")) {
                assertTrue(rs.next());
                hasProvisionStep = rs.getInt(1) > 0;
            }
            assertTrue(hasProvisionStep, "V27 表缺失 (sys_tenant_provision_step)");

            // 抽查 V27：sys_tenant 增加 provision_request_id 列
            boolean hasTenantRequestId = false;
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT COUNT(*) FROM information_schema.columns "
                                    + "WHERE LOWER(table_name) = 'sys_tenant' "
                                    + "AND LOWER(column_name) = 'provision_request_id'")) {
                assertTrue(rs.next());
                hasTenantRequestId = rs.getInt(1) > 0;
            }
            assertTrue(hasTenantRequestId, "V27 列缺失 (sys_tenant.provision_request_id)");

            // 抽查 V27：sys_tenant_provision_step 关键列齐备
            List<String> stepCols = new ArrayList<>();
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT LOWER(column_name) FROM information_schema.columns "
                                    + "WHERE LOWER(table_name) = 'sys_tenant_provision_step' "
                                    + "AND LOWER(column_name) IN "
                                    + "('provision_request_id','step_name','attempt_no','status','error_detail')")) {
                while (rs.next()) {
                    stepCols.add(rs.getString(1));
                }
            }
            assertTrue(
                    stepCols.contains("provision_request_id"),
                    "V27 列缺失 (sys_tenant_provision_step.provision_request_id)");
            assertTrue(
                    stepCols.contains("step_name"),
                    "V27 列缺失 (sys_tenant_provision_step.step_name)");
            assertTrue(
                    stepCols.contains("attempt_no"),
                    "V27 列缺失 (sys_tenant_provision_step.attempt_no)");
            assertTrue(stepCols.contains("status"), "V27 列缺失 (sys_tenant_provision_step.status)");
            assertTrue(
                    stepCols.contains("error_detail"),
                    "V27 列缺失 (sys_tenant_provision_step.error_detail)");

            // 抽查 V28（P3-T3 sys_config 租户化 + 邀请式引导）：sys_config 增加 tenant_id 列
            boolean hasConfigTenantId = false;
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT COUNT(*) FROM information_schema.columns "
                                    + "WHERE LOWER(table_name) = 'sys_config' "
                                    + "AND LOWER(column_name) = 'tenant_id'")) {
                assertTrue(rs.next());
                hasConfigTenantId = rs.getInt(1) > 0;
            }
            assertTrue(hasConfigTenantId, "V28 列缺失 (sys_config.tenant_id)");

            // 抽查 V28：复合唯一索引 uk_config_tenant_key 应覆盖 (tenant_id, config_key)
            List<String> ukCols = new ArrayList<>();
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT LOWER(column_name) FROM information_schema.index_columns "
                                    + "WHERE LOWER(table_name) = 'sys_config' "
                                    + "AND LOWER(index_name) = 'uk_config_tenant_key'")) {
                while (rs.next()) {
                    ukCols.add(rs.getString(1));
                }
            }
            assertTrue(
                    ukCols.contains("tenant_id") && ukCols.contains("config_key"),
                    "V28 复合唯一索引 uk_config_tenant_key 应覆盖 (tenant_id, config_key)，实际=" + ukCols);

            // 抽查 V28：新表 sys_user_invite 存在
            boolean hasUserInvite = false;
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT COUNT(*) FROM information_schema.tables "
                                    + "WHERE LOWER(table_name) = 'sys_user_invite'")) {
                assertTrue(rs.next());
                hasUserInvite = rs.getInt(1) > 0;
            }
            assertTrue(hasUserInvite, "V28 表缺失 (sys_user_invite)");

            // 抽查 V28：sys_user_invite 关键列齐备
            List<String> inviteCols = new ArrayList<>();
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT LOWER(column_name) FROM information_schema.columns "
                                    + "WHERE LOWER(table_name) = 'sys_user_invite' "
                                    + "AND LOWER(column_name) IN "
                                    + "('invite_id','tenant_id','user_id','invite_code_hash','status','expires_at')")) {
                while (rs.next()) {
                    inviteCols.add(rs.getString(1));
                }
            }
            assertTrue(inviteCols.contains("invite_id"), "V28 列缺失 (sys_user_invite.invite_id)");
            assertTrue(inviteCols.contains("tenant_id"), "V28 列缺失 (sys_user_invite.tenant_id)");
            assertTrue(inviteCols.contains("user_id"), "V28 列缺失 (sys_user_invite.user_id)");
            assertTrue(
                    inviteCols.contains("invite_code_hash"),
                    "V28 列缺失 (sys_user_invite.invite_code_hash)");
            assertTrue(inviteCols.contains("status"), "V28 列缺失 (sys_user_invite.status)");
            assertTrue(inviteCols.contains("expires_at"), "V28 列缺失 (sys_user_invite.expires_at)");

            // 抽查 V28：sys_user 增加 must_change_password 列
            boolean hasMustChangePwd = false;
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT COUNT(*) FROM information_schema.columns "
                                    + "WHERE LOWER(table_name) = 'sys_user' "
                                    + "AND LOWER(column_name) = 'must_change_password'")) {
                assertTrue(rs.next());
                hasMustChangePwd = rs.getInt(1) > 0;
            }
            assertTrue(hasMustChangePwd, "V28 列缺失 (sys_user.must_change_password)");

            // 抽查 V29（P3-T5 租户归档 + 删除生命周期）：sys_tenant 增加 archived_at / deleted_at /
            // deletion_request_id
            List<String> tenantArchiveCols = new ArrayList<>();
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT LOWER(column_name) FROM information_schema.columns "
                                    + "WHERE LOWER(table_name) = 'sys_tenant' "
                                    + "AND LOWER(column_name) IN "
                                    + "('archived_at','deleted_at','deletion_request_id')")) {
                while (rs.next()) {
                    tenantArchiveCols.add(rs.getString(1));
                }
            }
            assertTrue(
                    tenantArchiveCols.contains("archived_at"), "V29 列缺失 (sys_tenant.archived_at)");
            assertTrue(tenantArchiveCols.contains("deleted_at"), "V29 列缺失 (sys_tenant.deleted_at)");
            assertTrue(
                    tenantArchiveCols.contains("deletion_request_id"),
                    "V29 列缺失 (sys_tenant.deletion_request_id)");

            // 抽查 V29：三张新表存在
            List<String> newTables = new ArrayList<>();
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT LOWER(table_name) FROM information_schema.tables "
                                    + "WHERE LOWER(table_name) IN "
                                    + "('sys_tenant_delete_approval','sys_tenant_deletion_step','sys_audit_log')")) {
                while (rs.next()) {
                    newTables.add(rs.getString(1));
                }
            }
            assertTrue(
                    newTables.contains("sys_tenant_delete_approval"),
                    "V29 表缺失 (sys_tenant_delete_approval)");
            assertTrue(
                    newTables.contains("sys_tenant_deletion_step"),
                    "V29 表缺失 (sys_tenant_deletion_step)");
            assertTrue(newTables.contains("sys_audit_log"), "V29 表缺失 (sys_audit_log)");

            // 抽查 V29：sys_tenant_deletion_step 关键列齐备
            List<String> delStepCols = new ArrayList<>();
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT LOWER(column_name) FROM information_schema.columns "
                                    + "WHERE LOWER(table_name) = 'sys_tenant_deletion_step' "
                                    + "AND LOWER(column_name) IN "
                                    + "('tenant_id','deletion_request_id','step_name','attempt_no','status','rows_affected')")) {
                while (rs.next()) {
                    delStepCols.add(rs.getString(1));
                }
            }
            assertTrue(
                    delStepCols.contains("tenant_id"),
                    "V29 列缺失 (sys_tenant_deletion_step.tenant_id)");
            assertTrue(
                    delStepCols.contains("deletion_request_id"),
                    "V29 列缺失 (sys_tenant_deletion_step.deletion_request_id)");
            assertTrue(
                    delStepCols.contains("step_name"),
                    "V29 列缺失 (sys_tenant_deletion_step.step_name)");
            assertTrue(
                    delStepCols.contains("attempt_no"),
                    "V29 列缺失 (sys_tenant_deletion_step.attempt_no)");
            assertTrue(delStepCols.contains("status"), "V29 列缺失 (sys_tenant_deletion_step.status)");
            assertTrue(
                    delStepCols.contains("rows_affected"),
                    "V29 列缺失 (sys_tenant_deletion_step.rows_affected)");

            // 抽查 V29：复合唯一索引 uk_deletion_step 应覆盖 (tenant_id, deletion_request_id, step_name,
            // attempt_no)
            List<String> delStepUkCols = new ArrayList<>();
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT LOWER(column_name) FROM information_schema.index_columns "
                                    + "WHERE LOWER(table_name) = 'sys_tenant_deletion_step' "
                                    + "AND LOWER(index_name) = 'uk_deletion_step'")) {
                while (rs.next()) {
                    delStepUkCols.add(rs.getString(1));
                }
            }
            assertTrue(
                    delStepUkCols.contains("tenant_id")
                            && delStepUkCols.contains("deletion_request_id")
                            && delStepUkCols.contains("step_name")
                            && delStepUkCols.contains("attempt_no"),
                    "V29 复合唯一索引 uk_deletion_step 应覆盖 (tenant_id, deletion_request_id, step_name, attempt_no)，实际="
                            + delStepUkCols);

            // 抽查 V30（P4-T3 kb_extraction_cache 租户内化）：新增 tenant_id 列
            boolean hasCacheTenantCol = false;
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT COUNT(*) FROM information_schema.columns "
                                    + "WHERE LOWER(table_name) = 'kb_extraction_cache' "
                                    + "AND LOWER(column_name) = 'tenant_id'")) {
                assertTrue(rs.next());
                hasCacheTenantCol = rs.getInt(1) > 0;
            }
            assertTrue(hasCacheTenantCol, "V30 列缺失 (kb_extraction_cache.tenant_id)");

            // 抽查 V30：新复合唯一索引 uk_extraction_cache_tenant_key 应覆盖
            // (tenant_id, checksum, strategy, model_name, prompt_hash, render_mode)
            List<String> cacheUkCols = new ArrayList<>();
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT LOWER(column_name) FROM information_schema.index_columns "
                                    + "WHERE LOWER(table_name) = 'kb_extraction_cache' "
                                    + "AND LOWER(index_name) = 'uk_extraction_cache_tenant_key'")) {
                while (rs.next()) {
                    cacheUkCols.add(rs.getString(1));
                }
            }
            assertTrue(
                    cacheUkCols.contains("tenant_id")
                            && cacheUkCols.contains("checksum")
                            && cacheUkCols.contains("strategy")
                            && cacheUkCols.contains("model_name")
                            && cacheUkCols.contains("prompt_hash")
                            && cacheUkCols.contains("render_mode"),
                    "V30 复合唯一索引 uk_extraction_cache_tenant_key 应覆盖 (tenant_id, checksum, strategy, model_name, prompt_hash, render_mode)，实际="
                            + cacheUkCols);

            // 抽查 V30：旧全局唯一索引 uk_extraction_cache_key 应已被删除（无损切换到租户内唯一）
            int legacyCacheUkColCount = 0;
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT COUNT(*) FROM information_schema.index_columns "
                                    + "WHERE LOWER(table_name) = 'kb_extraction_cache' "
                                    + "AND LOWER(index_name) = 'uk_extraction_cache_key'")) {
                assertTrue(rs.next());
                legacyCacheUkColCount = rs.getInt(1);
            }
            assertEquals(
                    0,
                    legacyCacheUkColCount,
                    "V30 切换后旧全局唯一索引 uk_extraction_cache_key 应已不存在（残留列="
                            + legacyCacheUkColCount
                            + "）");

            // 抽查 V31（P4-T4 per-tenant LLM 配置）：新表 sys_tenant_llm_profile 存在
            boolean hasLlmProfile = false;
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT COUNT(*) FROM information_schema.tables "
                                    + "WHERE LOWER(table_name) = 'sys_tenant_llm_profile'")) {
                assertTrue(rs.next());
                hasLlmProfile = rs.getInt(1) > 0;
            }
            assertTrue(hasLlmProfile, "V31 表缺失 (sys_tenant_llm_profile)");

            // 抽查 V31 关键列
            List<String> llmProfileCols = new ArrayList<>();
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT LOWER(column_name) FROM information_schema.columns "
                                    + "WHERE LOWER(table_name) = 'sys_tenant_llm_profile' "
                                    + "AND LOWER(column_name) IN "
                                    + "('tenant_id','api_key','base_url','model','embedding_model','dimension')")) {
                while (rs.next()) {
                    llmProfileCols.add(rs.getString(1));
                }
            }
            assertTrue(
                    llmProfileCols.contains("tenant_id"),
                    "V31 列缺失 (sys_tenant_llm_profile.tenant_id)");
            assertTrue(
                    llmProfileCols.contains("api_key"), "V31 列缺失 (sys_tenant_llm_profile.api_key)");
            assertTrue(
                    llmProfileCols.contains("base_url"),
                    "V31 列缺失 (sys_tenant_llm_profile.base_url)");
            assertTrue(llmProfileCols.contains("model"), "V31 列缺失 (sys_tenant_llm_profile.model)");
            assertTrue(
                    llmProfileCols.contains("embedding_model"),
                    "V31 列缺失 (sys_tenant_llm_profile.embedding_model)");
            assertTrue(
                    llmProfileCols.contains("dimension"),
                    "V31 列缺失 (sys_tenant_llm_profile.dimension)");

            // 抽查 V32（P6-T1 租户用量计量）：新表 tenant_usage 存在
            boolean hasTenantUsage = false;
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT COUNT(*) FROM information_schema.tables "
                                    + "WHERE LOWER(table_name) = 'tenant_usage'")) {
                assertTrue(rs.next());
                hasTenantUsage = rs.getInt(1) > 0;
            }
            assertTrue(hasTenantUsage, "V32 表缺失 (tenant_usage)");

            // 抽查 V32 关键列
            List<String> tenantUsageCols = new ArrayList<>();
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT LOWER(column_name) FROM information_schema.columns "
                                    + "WHERE LOWER(table_name) = 'tenant_usage' "
                                    + "AND LOWER(column_name) IN "
                                    + "('tenant_id','metric','period','usage_value')")) {
                while (rs.next()) {
                    tenantUsageCols.add(rs.getString(1));
                }
            }
            assertTrue(tenantUsageCols.contains("tenant_id"), "V32 列缺失 (tenant_usage.tenant_id)");
            assertTrue(tenantUsageCols.contains("metric"), "V32 列缺失 (tenant_usage.metric)");
            assertTrue(tenantUsageCols.contains("period"), "V32 列缺失 (tenant_usage.period)");
            assertTrue(
                    tenantUsageCols.contains("usage_value"), "V32 列缺失 (tenant_usage.usage_value)");

            // 抽查 V33（P6-T2 套餐限额）：新表 tenant_plan_limit 存在
            boolean hasTenantPlanLimit = false;
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT COUNT(*) FROM information_schema.tables "
                                    + "WHERE LOWER(table_name) = 'tenant_plan_limit'")) {
                assertTrue(rs.next());
                hasTenantPlanLimit = rs.getInt(1) > 0;
            }
            assertTrue(hasTenantPlanLimit, "V33 表缺失 (tenant_plan_limit)");

            // 抽查 V33 关键列
            List<String> tenantPlanLimitCols = new ArrayList<>();
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT LOWER(column_name) FROM information_schema.columns "
                                    + "WHERE LOWER(table_name) = 'tenant_plan_limit' "
                                    + "AND LOWER(column_name) IN "
                                    + "('tenant_id','metric','quota','policy')")) {
                while (rs.next()) {
                    tenantPlanLimitCols.add(rs.getString(1));
                }
            }
            assertTrue(
                    tenantPlanLimitCols.contains("tenant_id"),
                    "V33 列缺失 (tenant_plan_limit.tenant_id)");
            assertTrue(
                    tenantPlanLimitCols.contains("metric"), "V33 列缺失 (tenant_plan_limit.metric)");
            assertTrue(tenantPlanLimitCols.contains("quota"), "V33 列缺失 (tenant_plan_limit.quota)");
            assertTrue(
                    tenantPlanLimitCols.contains("policy"), "V33 列缺失 (tenant_plan_limit.policy)");

            // 抽查 V34（R3 核心表外键约束）：kb_document_chunk 应存在外键 fk_chunk_document
            boolean hasFkChunkDoc = false;
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT COUNT(*) FROM information_schema.table_constraints "
                                    + "WHERE LOWER(table_name) = 'kb_document_chunk' "
                                    + "AND LOWER(constraint_name) = 'fk_chunk_document' "
                                    + "AND UPPER(constraint_type) = 'FOREIGN KEY'")) {
                assertTrue(rs.next());
                hasFkChunkDoc = rs.getInt(1) > 0;
            }
            assertTrue(hasFkChunkDoc, "V34 外键缺失 (kb_document_chunk.fk_chunk_document)");

            // 抽查 V34：sys_department 应存在自引用外键 fk_department_parent（RESTRICT）
            boolean hasFkDeptParent = false;
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT COUNT(*) FROM information_schema.table_constraints "
                                    + "WHERE LOWER(table_name) = 'sys_department' "
                                    + "AND LOWER(constraint_name) = 'fk_department_parent' "
                                    + "AND UPPER(constraint_type) = 'FOREIGN KEY'")) {
                assertTrue(rs.next());
                hasFkDeptParent = rs.getInt(1) > 0;
            }
            assertTrue(hasFkDeptParent, "V34 外键缺失 (sys_department.fk_department_parent)");
        }
    }
}
