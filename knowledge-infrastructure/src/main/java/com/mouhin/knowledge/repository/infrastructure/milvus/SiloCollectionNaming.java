package com.mouhin.knowledge.repository.infrastructure.milvus;

import com.mouhin.knowledge.repository.domain.model.valueobject.TenantKey;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * SILO 独立集合命名工具与静态校验（P2-T4，Phase 2 交付仅命名工具与入库校验）。
 *
 * <p>命名规范（清单 §4 T-3，Phase 2 定案）：SILO 集合名 = {@value #COLLECTION_PREFIX} + 租户 {@code
 * tenantKey}，例：{@code default → kc_default}、{@code acme → kc_acme}。前缀 {@code kc_} （knowledge-chunk
 * 首字母）与 LangChain4j 默认集合 {@code knowledge_chunks} 一眼可辨，同时避免与 Phase 2 现网 POOL 集合 {@code
 * knowledge_chunks} 冲突。
 *
 * <p>字符集约束（{@value #TENANT_KEY_PATTERN}）：小写字母开头，后接小写字母 / 数字 / 下划线，总长 2–63 字符（{@code {1,62}}
 * 表示首字符后至少再 1 个）。规则对齐 Milvus collection name 约束 （字母 / 下划线开头 + 字母 / 数字 / 下划线，长度 1–255），并保留 192
 * 字符余量给前缀 / 未来扩展。 现网种子 {@code default} 与常见示例 {@code acme} 均通过；{@code TenantResolverFilter}（P1-T3）
 * 已在 HTTP 入口把 {@code tenant_key} 归一为小写。
 *
 * <p><b>Phase 2 范围</b>：本类只提供<b>纯静态工具</b>——命名转换 + 校验，不建集合、不接 {@code MilvusClient} 生命周期；S-1 方案 B 授权
 * SILO 生命周期实现延后至 Phase 3 P3-T1（平台端 {@code TenantLifecycleService.create} 预校验调用点见 {@link
 * #assertValidForProvisioning(String)} 使用示例，Phase 3 起在 {@code SiloCollectionAdmin} 建集合前调用）。
 *
 * <p><b>回退策略</b>：若 Phase 3 决定采用其他命名前缀（如 {@code knowledge_} 保留域），只需替换本类 常量 {@link
 * #COLLECTION_PREFIX}；因 Phase 2 不建任何 SILO 集合，替换零成本。
 *
 * @author mouhinU
 * @date 2026-09-30
 */
public final class SiloCollectionNaming {

    /**
     * SILO 集合名前缀，与 POOL 主集合 {@code knowledge_chunks} 显式区分。
     */
    public static final String COLLECTION_PREFIX = "kc_";

    /**
     * 租户键合法正则：小写字母开头 + {@code [a-z0-9_]}，总长 2–63。
     *
     * <p>{@code ^[a-z][a-z0-9_]{1,62}$}：首字符必须是字母（避免 Milvus "下划线开头"在某些 SDK 版本被识别为保留字），后续 1–62
     * 个字符允许字母 / 数字 / 下划线。上限 63 给前缀 {@code kc_}（3 字符）留出 192 字符的 Milvus 硬限余量。
     */
    public static final String TENANT_KEY_PATTERN = TenantKey.PATTERN;
    /**
     * Milvus collection name 长度硬上限（含前缀），超出时 Phase 3 建集合直接失败。
     */
    static final int MAX_MILVUS_COLLECTION_NAME_LENGTH = 255;
    /**
     * {@link #TENANT_KEY_PATTERN} 的预编译形态，避免热路径重复构造。
     */
    private static final Pattern TENANT_KEY_REGEX = Pattern.compile(TENANT_KEY_PATTERN);

    private SiloCollectionNaming() {
    }

    /**
     * 把 {@code tenantKey} 转成 SILO 集合名。校验通过则返回 {@code kc_{tenantKey}}； 未通过抛 {@link
     * IllegalArgumentException}。
     *
     * <p>行为契约：
     *
     * <ul>
     *   <li>{@code null} 或空白 → 抛 {@code IllegalArgumentException}；
     *   <li>包含首尾空白 → 先 {@code trim}（宽松读），再校验；
     *   <li>包含大写字母 → 抛（不自动 lowerCase，避免 {@code acme} vs {@code ACME} 静默映射到 同一集合，与 {@link
     *       com.mouhin.knowledge.repository.domain.context.TenantContext} 的"绑定即原样"语义保持显式）；
     *   <li>已带前缀 {@code kc_} 的字符串 → 抛（防止误用 {@code toCollectionName("kc_acme")} 得到 {@code
     *       kc_kc_acme}）。
     * </ul>
     *
     * @param tenantKey 租户业务键，例 {@code default} / {@code acme} / {@code edu_bj_01}
     * @return SILO 集合名，例 {@code kc_default} / {@code kc_acme}
     * @throws IllegalArgumentException 校验失败
     */
    public static String toCollectionName(String tenantKey) {
        if (!isValid(tenantKey)) {
            throw new IllegalArgumentException(
                    "Invalid tenantKey for SILO collection naming: '"
                            + tenantKey
                            + "'; must match "
                            + TENANT_KEY_PATTERN);
        }
        String normalised = tenantKey.trim();
        String collectionName = COLLECTION_PREFIX + normalised;
        if (collectionName.length() > MAX_MILVUS_COLLECTION_NAME_LENGTH) {
            // 理论上不可达：正则已把 tenantKey 限到 63 字符，前缀 3 → 66 远小于 255。
            // 保留断言便于将来若放宽正则时不遗漏边界。
            throw new IllegalArgumentException(
                    "Collection name exceeds Milvus 255-char limit: " + collectionName);
        }
        return collectionName;
    }

    /**
     * 纯校验：{@code tenantKey} 是否符合 SILO 命名规范。返回 boolean，不抛。
     *
     * <p>用于 {@code sys_tenant} 表入库前的预校验（Phase 3 P3-T1 平台端 create 接口）与 {@code
     * TenantResolverFilter}（P1-T3）在解析 header / JWT claim 时的可选 strict 模式。
     *
     * @param tenantKey 待校验字符串，{@code null} / 空白 / 大小写不符 / 首字符非字母均返回 {@code false}
     * @return 合法返回 {@code true}
     */
    public static boolean isValid(String tenantKey) {
        if (tenantKey == null) {
            return false;
        }
        String trimmed = tenantKey.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        // 拒绝已经带前缀的输入，防止 toCollectionName 二次拼接
        if (trimmed.startsWith(COLLECTION_PREFIX)) {
            return false;
        }
        return TENANT_KEY_REGEX.matcher(trimmed).matches();
    }

    /**
     * Phase 3 P3-T1 建集合前的预校验：不仅 {@code tenantKey} 要合法，还要拒绝 POOL 主集合 名冲突（{@code knowledge_chunks}）与
     * {@code kc_} 前缀重复使用。
     *
     * <p>Phase 2 仅定义 API 与自测，不调用；Phase 3 起由 {@code SiloCollectionAdmin.createIfAbsent} 在建集合前调用一次。
     *
     * @param tenantKey 目标租户
     * @throws IllegalArgumentException 命名不合法或与保留字冲突
     */
    public static void assertValidForProvisioning(String tenantKey) {
        if (!isValid(tenantKey)) {
            throw new IllegalArgumentException(
                    "Cannot provision SILO collection for tenantKey='" + tenantKey + "'");
        }
        String candidate = COLLECTION_PREFIX + tenantKey.trim();
        // 与 POOL 主集合名冲突（虽然当前前缀不同不会真撞，此断言为将来放宽前缀时的护栏）
        if ("knowledge_chunks".equalsIgnoreCase(candidate)) {
            throw new IllegalArgumentException(
                    "SILO collection name collides with POOL 'knowledge_chunks': " + candidate);
        }
    }

    /**
     * 判断 {@code collectionName} 是否为一个 SILO 形态集合名（以 {@value #COLLECTION_PREFIX} 开头 且剩余部分符合 {@link
     * #TENANT_KEY_PATTERN}）。Phase 3 集合枚举 / 巡检工具会用到。
     *
     * @param collectionName Milvus 侧回读的集合名
     * @return 是 SILO 集合返回 {@code true}；{@code null} / POOL 主集合 / 其他一律 {@code false}
     */
    public static boolean isSiloCollectionName(String collectionName) {
        if (collectionName == null || !collectionName.startsWith(COLLECTION_PREFIX)) {
            return false;
        }
        String tail = collectionName.substring(COLLECTION_PREFIX.length());
        // tail 直接匹配正则即可（不再拒绝 kc_ 前缀，因为我们已经剥离了一次）
        return TENANT_KEY_REGEX.matcher(tail).matches();
    }

    /**
     * 反向解析：从 SILO 集合名还原 {@code tenantKey}（小写原样，不做归一化）。
     *
     * @param collectionName 必须是 {@link #isSiloCollectionName(String)} 为 true 的形态
     * @return 集合名去掉前缀后的租户键
     * @throws IllegalArgumentException 非 SILO 集合名
     */
    public static String fromCollectionName(String collectionName) {
        if (!isSiloCollectionName(collectionName)) {
            throw new IllegalArgumentException("Not a SILO collection name: " + collectionName);
        }
        return collectionName.substring(COLLECTION_PREFIX.length()).toLowerCase(Locale.ROOT);
    }
}
