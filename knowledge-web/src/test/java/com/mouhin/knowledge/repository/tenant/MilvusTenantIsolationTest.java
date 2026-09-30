package com.mouhin.knowledge.repository.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.mouhin.knowledge.repository.domain.context.TenantContext;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.domain.model.valueobject.SearchResult;
import com.mouhin.knowledge.repository.infrastructure.config.MilvusConfig;
import com.mouhin.knowledge.repository.infrastructure.config.MyBatisPlusConfig;
import com.mouhin.knowledge.repository.infrastructure.milvus.EmbeddingStoreProvider;
import com.mouhin.knowledge.repository.infrastructure.milvus.EmbeddingStoreRouter;
import com.mouhin.knowledge.repository.infrastructure.milvus.MilvusVectorStoreService;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.TenantMapper;
import com.mouhin.knowledge.repository.infrastructure.tenant.TenantIdResolver;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

/**
 * P2-T5 · 向量跨租户负向端到端（Testcontainers Milvus + Testcontainers MySQL）。
 *
 * <p>目标：把 S-2 方案 b（LangChain4j 单 store + {@code metadata.tenant_id} JSON 谓词路径）的隔离正确性 从 <b>单元 mock
 * 层</b>（P2-T2/P2-T3 已覆盖）与 <b>spike REST 直连层</b>（{@code spikes/milvus-partition-key/ RESULTS.md} §2
 * 已证 0 泄漏）拉到 <b>真实 Milvus 引擎 + Spring 上下文 + MyBatis-Plus 拦截器 + LangChain4j MilvusEmbeddingStore</b>
 * 的完整栈上再验一次，作为 Phase 2 收尾的门禁负向用例。
 *
 * <p>三条核心不变式（清单 §7 步骤 6）：
 *
 * <ol>
 *   <li>单租户召回非空：A（acme）建 100 向量后，A 上下文中 {@code MilvusVectorStoreService.search} 命中 = 100；
 *   <li>跨租户负向：A、B 各建 100 向量后，B 上下文中检索 {@code maxResults=200}，返回结果集 document_key 前缀 <b>全为 {@code
 *       p2t5-b-}</b>，不含任何 {@code p2t5-a-}；反向亦然；
 *   <li>关闭态等价：未 ThreadLocal 绑定时（{@code saas.enabled=false} 语义）走 {@code default} 租户谓词，不抛、 不 500。
 * </ol>
 *
 * <p><b>不覆盖</b>（Phase 3 会审）：partition-key 万级时延阈值、SILO 独立集合生命周期、{@code io.milvus} SDK 直连建
 * partition-key 集合。S-3 门禁降级已把阈值项从 Phase 2 剥离（清单 §4 S-3）。
 *
 * <p><b>本地执行状态</b>：本机若无 Docker Desktop（当前会话即如此），{@link Testcontainers #disabledWithoutDocker()} 会整类
 * skip；首次真实执行由 C-1 独立 CI job（{@code .github/workflows/ci.yml · tenant-isolation}）负责，命令 {@code mvn
 * -B test -Dgroups=tenant -DargLine="-Dapi.version=1.44"}，规避 Docker Engine 29.8.1 与 Testcontainers
 * 1.20.6 的 API 协商坑（{@code MEMORY} P1-T10 条目）。
 *
 * <p><b>fake EmbeddingModel</b>：本类 {@link TestConfig#deterministicEmbeddingModel()} 提供 dim=4 常量向量
 * {@code [1,0,0,0]} 的模型，覆盖 {@link dev.langchain4j.model.embedding.EmbeddingModel} 唯一抽象 {@code
 * embedAll}； {@code embed(String)} 通过 default 委派 {@code embedAll}。常量向量使每次 {@code search} 相似度恒为 1.0，
 * 断言可以只看 <b>条数</b> 与 <b>document_key 前缀</b>，不涉及排序稳定性。
 *
 * @author mouhinU
 * @date 2026-09-30
 */
@DisplayName("P2-T5 向量跨租户负向 · Testcontainers Milvus")
@Tag("tenant")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = MilvusTenantIsolationTest.TestConfig.class)
class MilvusTenantIsolationTest {

    /** Milvus standalone 内部 gRPC 端口（容器视角固定），{@code MILVUS.getMappedPort()} 映射到宿主随机端口。 */
    private static final int MILVUS_INTERNAL_PORT = 19530;

    /** 与生产对齐：Milvus 2.5.4 standalone（内置 etcd + 本地文件存储，无外部依赖）。 */
    @Container
    static final MilvusContainer MILVUS =
            new MilvusContainer("milvusdb/milvus:v2.5.4").withReuse(false);

    /** 与 P1-T10 同基线：MySQL 8 + utf8mb4，承载 {@code sys_tenant} 元数据。 */
    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("knowledge_repository")
                    .withUsername("test")
                    .withPassword("test")
                    .withCommand(
                            "--character-set-server=utf8mb4",
                            "--collation-server=utf8mb4_unicode_ci");

    @DynamicPropertySource
    static void bindDynamicProperties(DynamicPropertyRegistry registry) {
        // MySQL / Flyway
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.url", MYSQL::getJdbcUrl);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        // SaaS 开启态：走 ThreadLocal + TenantIdResolver 完整链路
        registry.add("knowledge.saas.enabled", () -> "true");
        // Milvus：{@link MilvusConfig} 读取 host / port / collection / dimension / database
        registry.add("knowledge.milvus.host", MILVUS::getHost);
        registry.add("knowledge.milvus.port", () -> MILVUS.getMappedPort(MILVUS_INTERNAL_PORT));
        registry.add("knowledge.milvus.collection-name", () -> "p2t5_knowledge_chunks");
        registry.add("knowledge.milvus.dimension", () -> "4");
        registry.add("knowledge.milvus.database-name", () -> "default");
    }

    /**
     * 最小可用上下文：DataSource + Flyway + MyBatis-Plus 拦截器 + TenantIdResolver + Milvus 三件套（Config /
     * Provider / Router / Service）。EmbeddingModel 由本类 {@link
     * TestConfig#deterministicEmbeddingModel()} 覆盖真实 {@code EmbeddingModelConfig} 的 OpenAI 版（后者需要外部
     * HTTP，测试不可用）。
     */
    @Configuration
    @EnableAutoConfiguration
    @MapperScan("com.mouhin.knowledge.repository.infrastructure.persistence.mapper")
    @Import({
        MyBatisPlusConfig.class,
        TenantIdResolver.class,
        MilvusConfig.class,
        EmbeddingStoreRouter.class,
        EmbeddingStoreProvider.class,
        MilvusVectorStoreService.class,
    })
    static class TestConfig {

        /**
         * 常量向量 {@code [1,0,0,0]}，dim=4。相同向量 → 相似度恒为 1.0 → 排序稳定，断言只看条数 + 前缀。
         *
         * <p>{@code @Primary} 保证当 classpath 上出现真实 {@code EmbeddingModelConfig}（通过
         * {@code @EnableAutoConfiguration} 拾取）时本 bean 优先。
         */
        @Bean
        @Primary
        EmbeddingModel deterministicEmbeddingModel() {
            return new EmbeddingModel() {
                @Override
                public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
                    List<Embedding> out = new ArrayList<>(segments.size());
                    for (int i = 0; i < segments.size(); i++) {
                        out.add(Embedding.from(new float[] {1f, 0f, 0f, 0f}));
                    }
                    return Response.from(out);
                }
            };
        }
    }

    @Autowired private MilvusVectorStoreService vectorStore;
    @Autowired private TenantMapper tenantMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantIdResolver tenantIdResolver;

    private long defaultTenantId;
    private long acmeTenantId;
    private long betaTenantId;

    @BeforeAll
    static void warnIfDockerMissing() {
        // 由 @Testcontainers(disabledWithoutDocker=true) 已保证有 Docker 才进入；此处仅记录 endpoint
        System.out.println(
                "[P2-T5] Milvus endpoint="
                        + MILVUS.getEndpoint()
                        + ", MySQL jdbc="
                        + MYSQL.getJdbcUrl());
    }

    @BeforeEach
    void seedTenantsAndCleanVectors() {
        defaultTenantId = tenantIdResolver.resolve("default");
        acmeTenantId = ensureTenant("acme", "Acme 制造");
        betaTenantId = ensureTenant("beta", "Beta 教育");
        // 强制 resolver 双向缓存刷新（seed 走的是 raw JDBC 而非 mapper）
        tenantIdResolver.invalidate("acme");
        tenantIdResolver.invalidate("beta");
        tenantIdResolver.resolve("acme");
        tenantIdResolver.resolve("beta");
        // 集合级清场（本 PR 独立集合名 p2t5_knowledge_chunks，与生产 knowledge_chunks 隔离）
        TenantContext.set("acme");
        safeDelete("p2t5-a-");
        TenantContext.set("beta");
        safeDelete("p2t5-b-");
        TenantContext.clear();
    }

    @AfterEach
    void cleanVectorsAndContext() {
        TenantContext.set("acme");
        safeDelete("p2t5-a-");
        TenantContext.set("beta");
        safeDelete("p2t5-b-");
        TenantContext.clear();
    }

    private long ensureTenant(String key, String name) {
        Long existing =
                jdbcTemplate
                        .query(
                                "SELECT tenant_id FROM sys_tenant WHERE tenant_key = ?",
                                (rs, i) -> rs.getLong(1),
                                key)
                        .stream()
                        .findFirst()
                        .orElse(null);
        if (existing != null) {
            return existing;
        }
        jdbcTemplate.update(
                "INSERT INTO sys_tenant (tenant_key, name, plan, status, data_region) "
                        + "VALUES (?, ?, 'standard', 'ACTIVE', 'cn')",
                key,
                name);
        return jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM sys_tenant WHERE tenant_key = ?", Long.class, key);
    }

    private void safeDelete(String documentKeyPrefix) {
        // MilvusVectorStoreService.deleteByDocumentKey 是精确匹配；此处按前缀逐条清（数量有限，可接受）。
        // 集合级 removeAll 在 LangChain4j 1.0.1 API 中要求 Filter；用一个恒真谓词等价"清集合"更简洁，
        // 但会跨租户误删（本 PR 用两个前缀 + 每次 @BeforeEach 精确删），避免用 Filter 谓词。
        try {
            List<String> keys =
                    jdbcTemplate.queryForList(
                            "SELECT DISTINCT document_key FROM kb_document_chunk WHERE document_key LIKE ?",
                            String.class,
                            documentKeyPrefix + "%");
            keys.forEach(vectorStore::deleteByDocumentKey);
        } catch (Exception ignored) {
            // 集合可能尚未创建（首次运行），delete 失败可忽略
        }
    }

    private static DocumentChunk chunkFor(String documentKey, int index, String tenantKey) {
        DocumentChunk c = new DocumentChunk();
        c.setChunkKey(UUID.randomUUID().toString());
        c.setTenantKey(tenantKey);
        c.setDocumentKey(documentKey);
        c.setDocumentName(documentKey + ".pdf");
        c.setDocumentId(1L);
        c.setChunkIndex(index);
        c.setStartPage(1);
        c.setEndPage(1);
        c.setContent("P2-T5 内容片段 #" + index);
        c.setFileType("pdf");
        c.setTags("p2t5");
        c.setCategory("其他");
        c.setDepartmentId("d-p2t5");
        c.setVisibility("INTERNAL");
        c.setAllowedRoles("[]");
        c.setOwnerId("u-p2t5");
        return c;
    }

    private List<DocumentChunk> buildSet(String prefix, int n, String tenantKey) {
        List<DocumentChunk> chunks = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            chunks.add(chunkFor(prefix + i, i, tenantKey));
        }
        return chunks;
    }

    // ---------------------------------------------------------------------
    // 1. 单租户召回非空：A 建 100 → A 检索命中 100
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("单租户召回非空：A 建 100 向量后 A 上下文检索命中 100 条")
    void sameTenantRecallIsNonEmpty() {
        TenantContext.callWithTenant(
                "acme",
                () -> {
                    vectorStore.storeChunks(buildSet("p2t5-a-", 100, "acme"));
                    return null;
                });

        List<SearchResult> hits =
                TenantContext.callWithTenant(
                        "acme", () -> vectorStore.search("anything", 200, 0.0, null, null));

        assertThat(hits)
                .as("A 上下文应召回全部 100 条 A 建向量，spike §2 已证无剪枝损失")
                .hasSize(100)
                .allSatisfy(r -> assertThat(r.getDocumentId()).startsWith("p2t5-a-"));
    }

    // ---------------------------------------------------------------------
    // 2. 跨租户负向：A 建 100 + B 建 100，B 检索 A 内容为空，反之亦然
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("跨租户负向：B 上下文检索不到任何 A 建向量（metadata.tenant_id 谓词生效）")
    void crossTenantQueryDoesNotLeak() {
        TenantContext.callWithTenant(
                "acme",
                () -> {
                    vectorStore.storeChunks(buildSet("p2t5-a-", 100, "acme"));
                    return null;
                });
        TenantContext.callWithTenant(
                "beta",
                () -> {
                    vectorStore.storeChunks(buildSet("p2t5-b-", 100, "beta"));
                    return null;
                });

        // B 上下文查 A 建的 document_key 前缀，返回 0 条
        List<SearchResult> hitsInBeta =
                TenantContext.callWithTenant(
                        "beta", () -> vectorStore.search("anything", 300, 0.0, null, null));

        assertThat(hitsInBeta)
                .as("B 上下文最多只能召回 B 自己建的 100 条，绝不含 p2t5-a-*")
                .hasSize(100)
                .allSatisfy(
                        r ->
                                assertThat(r.getDocumentId())
                                        .startsWith("p2t5-b-")
                                        .doesNotStartWith("p2t5-a-"));

        // 反向亦然：A 上下文看不到 B 建的向量
        List<SearchResult> hitsInAcme =
                TenantContext.callWithTenant(
                        "acme", () -> vectorStore.search("anything", 300, 0.0, null, null));
        assertThat(hitsInAcme).hasSize(100);
        assertThat(hitsInAcme)
                .allSatisfy(
                        r ->
                                assertThat(r.getDocumentId())
                                        .startsWith("p2t5-a-")
                                        .doesNotStartWith("p2t5-b-"));
    }

    // ---------------------------------------------------------------------
    // 3. 关闭态（未 ThreadLocal 绑定）行为等价：走 default，不抛
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("未绑定 ThreadLocal → 走 default 谓词，不抛不 500；A/B 数据都不被 default 上下文召回")
    void unboundContextFallsBackToDefault() {
        TenantContext.callWithTenant(
                "acme",
                () -> {
                    vectorStore.storeChunks(buildSet("p2t5-a-", 5, "acme"));
                    return null;
                });

        TenantContext.clear();
        List<SearchResult> hits = vectorStore.search("anything", 100, 0.0, null, null);

        // default 谓词下 A 建数据不可见（metadata.tenant_id != 1）；此处断言"不抛且返回空"
        assertThat(hits).as("default 上下文不应看到 acme 数据；若命中说明 metadata.tenant_id 谓词漏了").isEmpty();
    }

    // ---------------------------------------------------------------------
    // 4. tenant_id 与 resolver 结果一致（元数据自检）
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("resolver 数值一致性：default=1、acme、beta 三者互不相同（防止 sys_tenant 种子漂移）")
    void tenantIdsDistinct() {
        assertThat(defaultTenantId).isNotEqualTo(acmeTenantId);
        assertThat(defaultTenantId).isNotEqualTo(betaTenantId);
        assertThat(acmeTenantId).isNotEqualTo(betaTenantId);
        // V23 种子 default.tenant_id = 1
        assertThat(defaultTenantId).isEqualTo(1L);
        // sys_tenant 表存在性自检（TenantMapper 走的是 IGNORE_TABLES 白名单，能全表读到）
        assertThat(tenantMapper.selectCount(null)).isGreaterThanOrEqualTo(3L);
    }
}
