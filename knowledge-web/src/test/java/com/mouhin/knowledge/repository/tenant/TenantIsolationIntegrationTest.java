package com.mouhin.knowledge.repository.tenant;

import static org.junit.jupiter.api.Assertions.*;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mouhin.knowledge.repository.domain.context.TenantContext;
import com.mouhin.knowledge.repository.infrastructure.config.MyBatisPlusConfig;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.DepartmentDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.DepartmentMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.TenantMapper;
import com.mouhin.knowledge.repository.infrastructure.tenant.TenantIdResolver;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * P1-T10 · 跨租户负向端到端（Testcontainers MySQL）。
 *
 * <p>目标：在真实 MySQL 8（临时容器）上跑完整 Flyway V1→V26 + Spring 上下文 + MyBatis-Plus {@code
 * TenantLineInnerInterceptor}，验证 SaaS 多租户核心不变式——同一 SQL 会话，绑定不同 {@link TenantContext}
 * 的调用者看到的行集互不重叠、写操作不能触达别的租户。这些不变式是"数据泄漏红线"（提案 §9）的技术兜底。
 *
 * <p>覆盖：
 *
 * <ol>
 *   <li>SELECT：绑 acme → {@code selectList} 仅返回 acme 行；绑 default → 仅返回 default 行。
 *   <li>SELECT by id：绑错租户 → {@code selectById} 返回 null（谓词 {@code WHERE id=? AND tenant_id=?} 无匹配）。
 *   <li>UPDATE：绑 acme → {@code updateById(default 行)} 影响 0 行。
 *   <li>DELETE：绑 acme → {@code deleteById(default 行)} 影响 0 行；对 acme 自己的行 → 影响 1 行。
 *   <li>INSERT：绑 acme → 提交 {@code tenantId=null} 的 DO，DB 行 {@code tenant_id} 由拦截器自动填为 acme id。
 *   <li>ignoreTable：{@code sys_tenant}（平台元数据）在拦截器白名单里，绑任一租户 {@code selectCount} 都能数到所有 ACTIVE 租户，
 *       否则 {@link TenantIdResolver} 本身就无法启动。
 * </ol>
 *
 * <p><b>不覆盖</b>（留给 Phase 2 及以后）：Milvus partition-key 向量召回隔离（P2-T1+），跨进程异步任务的租户快照传播（已由 {@code
 * TenantContextPropagatingExecutorTest} 单元测试覆盖），配额 / 限流（Phase 6）。
 *
 * <p><b>零污染</b>：容器与生产 MySQL（{@code localhost:3307}）完全隔离，随机端口 + 内存数据卷；Flyway 从 V1 全量迁移，无历史 schema
 * 泄漏。 {@code @Testcontainers(disabledWithoutDocker = true)} 保证无 Docker 环境（本机 Windows CI Runner 之类）
 * 自动 skip，不阻塞纯单元测试 CI 通道。
 *
 * @author mouhinU
 * @date 2026-09-30
 */
@DisplayName("P1-T10 跨租户负向 · Testcontainers MySQL")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = TenantIsolationIntegrationTest.TestConfig.class)
class TenantIsolationIntegrationTest {

    /** 与生产对齐：MySQL 8 镜像；utf8mb4 通过容器启动参数确保中文 name 列不截断。 */
    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("knowledge_repository")
                    .withUsername("test")
                    .withPassword("test")
                    .withCommand(
                            "--character-set-server=utf8mb4",
                            "--collation-server=utf8mb4_unicode_ci");

    @Autowired private DepartmentMapper departmentMapper;
    @Autowired private TenantMapper tenantMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantIdResolver tenantIdResolver;
    private long defaultId;
    private long acmeId;

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.url", MYSQL::getJdbcUrl);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("knowledge.saas.enabled", () -> "true");
    }

    private static void assertAllBelongTo(List<DepartmentDO> rows, long expectedTenant) {
        assertTrue(!rows.isEmpty(), "结果集不应为空（应至少包含 seed 的本租户行）");
        for (DepartmentDO row : rows) {
            assertEquals(
                    expectedTenant,
                    row.getTenantId(),
                    "行 id=" + row.getId() + " 的 tenant_id=" + row.getTenantId() + " 与绑定租户不符");
        }
    }

    @BeforeEach
    void seedSecondTenant() {
        defaultId = tenantIdResolver.resolve("default");
        Long existing =
                jdbcTemplate
                        .query(
                                "SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'acme'",
                                (rs, i) -> rs.getLong(1))
                        .stream()
                        .findFirst()
                        .orElse(null);
        if (existing == null) {
            jdbcTemplate.update(
                    "INSERT INTO sys_tenant (tenant_key, name, plan, status, data_region) "
                            + "VALUES ('acme', 'Acme 制造', 'standard', 'ACTIVE', 'cn')");
            existing =
                    jdbcTemplate.queryForObject(
                            "SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'acme'",
                            Long.class);
        }
        acmeId = existing;
        tenantIdResolver.invalidateByKey("acme"); // 强制走 DB 回填双向缓存
        tenantIdResolver.resolve("acme");
    }

    @AfterEach
    void cleanUp() {
        // 测试期间通过 raw JDBC 塞进 sys_department 的样本行；不走 MP 拦截器以避免影响下一次测试的可见性。
        jdbcTemplate.update("DELETE FROM sys_department WHERE department_key LIKE 'p1t10-%'");
        TenantContext.clear();
    }

    // ---------------------------------------------------------------------
    // 1. SELECT 过滤：绑定 acme → 只看 acme 行；绑定 default → 只看 default 行
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("selectList 按绑定租户过滤，跨租户行不可见")
    void selectListIsScopedToBoundTenant() {
        long deptAcme = seedDepartmentRaw("p1t10-acme", "Acme 分部", acmeId);
        long deptDefault = seedDepartmentRaw("p1t10-def", "默认分部", defaultId);
        try {
            TenantContext.set("acme");
            List<DepartmentDO> acmeRows = departmentMapper.selectList(null);
            assertAllBelongTo(acmeRows, acmeId);
            assertTrue(
                    acmeRows.stream().anyMatch(d -> d.getId().equals(deptAcme)),
                    "acme 自己的行必须在结果集内");
            assertFalse(
                    acmeRows.stream().anyMatch(d -> d.getId().equals(deptDefault)),
                    "default 的行绝不能出现在 acme 结果集内");

            TenantContext.set("default");
            List<DepartmentDO> defaultRows = departmentMapper.selectList(null);
            assertAllBelongTo(defaultRows, defaultId);
            assertTrue(defaultRows.stream().anyMatch(d -> d.getId().equals(deptDefault)));
            assertFalse(defaultRows.stream().anyMatch(d -> d.getId().equals(deptAcme)));
        } finally {
            TenantContext.clear();
        }
    }

    // ---------------------------------------------------------------------
    // 2. SELECT by id：绑错租户 → null（谓词追加后无匹配）
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("selectById 绑错租户返回 null")
    void selectByIdIsScoped() {
        long deptDefault = seedDepartmentRaw("p1t10-def-byid", "默认分部", defaultId);
        try {
            TenantContext.set("acme");
            DepartmentDO invisible = departmentMapper.selectById(deptDefault);
            assertNull(invisible, "acme 上下文读 default 行必须为 null（红线：跨租户不可见）");

            TenantContext.set("default");
            DepartmentDO visible = departmentMapper.selectById(deptDefault);
            assertNotNull(visible, "default 上下文读自己的行必须命中");
        } finally {
            TenantContext.clear();
        }
    }

    // ---------------------------------------------------------------------
    // 3. UPDATE：绑 acme 改 default 行 → 影响 0 行；改 acme 行 → 影响 1 行
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("updateById 不能触达他租户行")
    void updateCannotTouchOtherTenant() {
        long deptDefault = seedDepartmentRaw("p1t10-def-upd", "默认分部", defaultId);
        long deptAcme = seedDepartmentRaw("p1t10-acme-upd", "Acme 分部", acmeId);
        try {
            TenantContext.set("acme");

            DepartmentDO tamperDefault = new DepartmentDO();
            tamperDefault.setId(deptDefault);
            tamperDefault.setDepartmentName("篡改");
            int affectedOnForeign = departmentMapper.updateById(tamperDefault);
            assertEquals(0, affectedOnForeign, "acme 上下文 updateById 他租户行必须 0 行");

            DepartmentDO touchOwn = new DepartmentDO();
            touchOwn.setId(deptAcme);
            touchOwn.setDepartmentName("Acme 更名");
            int affectedOnOwn = departmentMapper.updateById(touchOwn);
            assertEquals(1, affectedOnOwn, "acme 上下文 updateById 本租户行应 1 行");

            String defaultName =
                    jdbcTemplate.queryForObject(
                            "SELECT department_name FROM sys_department WHERE id = ?",
                            String.class,
                            deptDefault);
            assertEquals("默认分部", defaultName, "default 行的名字不应被 acme 上下文的 updateById 影响");
        } finally {
            TenantContext.clear();
        }
    }

    // ---------------------------------------------------------------------
    // 4. DELETE：绑 acme 删 default 行 → 0 行；删 acme 自己行 → 1 行
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("deleteById 不能触达他租户行")
    void deleteCannotTouchOtherTenant() {
        long deptDefault = seedDepartmentRaw("p1t10-def-del", "默认分部", defaultId);
        long deptAcme = seedDepartmentRaw("p1t10-acme-del", "Acme 分部", acmeId);
        try {
            TenantContext.set("acme");
            int deleteForeign = departmentMapper.deleteById(deptDefault);
            assertEquals(0, deleteForeign, "acme 上下文 deleteById 他租户行必须 0 行");
            int deleteOwn = departmentMapper.deleteById(deptAcme);
            assertEquals(1, deleteOwn, "acme 上下文 deleteById 本租户行应 1 行");

            Integer foreignAlive =
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM sys_department WHERE id = ?",
                            Integer.class,
                            deptDefault);
            assertEquals(1, foreignAlive, "default 行不应被 acme 上下文删除");
        } finally {
            TenantContext.clear();
        }
    }

    // ---------------------------------------------------------------------
    // 5. INSERT：绑 acme，DO.tenantId=null → 拦截器自动填 acme；raw JDBC 复核
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("insert 自动注入 tenant_id（无需应用层显式赋值）")
    void insertAutoPopulatesTenantId() {
        try {
            TenantContext.set("acme");
            DepartmentDO fresh = new DepartmentDO();
            fresh.setDepartmentKey("p1t10-insert");
            fresh.setDepartmentName("新建分部");
            fresh.setParentId(null);
            int rows = departmentMapper.insert(fresh);
            assertEquals(1, rows, "insert 应影响 1 行");
            assertNotNull(fresh.getId(), "insert 后主键回填");

            Long actualTenant =
                    jdbcTemplate.queryForObject(
                            "SELECT tenant_id FROM sys_department WHERE id = ?",
                            Long.class,
                            fresh.getId());
            assertEquals(acmeId, actualTenant, "拦截器应把 tenant_id 自动补为 acme 而不是走 DB DEFAULT 1");
        } finally {
            TenantContext.clear();
        }
    }

    // ---------------------------------------------------------------------
    // 6. ignoreTable：sys_tenant 平台元数据不受过滤（否则 TenantIdResolver 无法启动）
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("sys_tenant 在 ignore 白名单，selectCount 不被过滤")
    void ignoreTableSysTenantNotScoped() {
        try {
            TenantContext.set("acme");
            Long total = tenantMapper.selectCount(Wrappers.query());
            assertTrue(
                    total >= 2L, "绑定 acme 也应看到所有租户（default + acme），实际 = " + total + " —— 平台表被误过滤了");
        } finally {
            TenantContext.clear();
        }
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    /** 用 raw JDBC 落一行 sys_department，绕开 MP 拦截器，可控指定 tenant_id。 */
    private long seedDepartmentRaw(String deptKey, String name, long tenantId) {
        jdbcTemplate.update(
                "INSERT INTO sys_department (department_key, department_name, parent_id, tenant_id) "
                        + "VALUES (?, ?, NULL, ?)",
                deptKey,
                name,
                tenantId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_department WHERE department_key = ?", Long.class, deptKey);
    }

    /**
     * 最小可用 Spring 上下文：仅 DataSource / Flyway / MyBatis-Plus / 拦截器 / 租户解析器 / 两个测试用 Mapper。
     *
     * <p>{@code webEnvironment=NONE} 默认（未显式指定即为 MOCKING，无 embedded server）——本类通过 {@link
     * SpringBootTest} 无参重载启动，不加载任何 web/security 栈；{@link EnableAutoConfiguration} 只让 DataSource
     * 相关三件套 + MyBatis-Plus 自动装配生效。
     */
    @Configuration
    @EnableAutoConfiguration
    @MapperScan("com.mouhin.knowledge.repository.infrastructure.persistence.mapper")
    @Import({MyBatisPlusConfig.class, TenantIdResolver.class})
    static class TestConfig {}
}
