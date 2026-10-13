package com.mouhin.knowledge.repository.domain.model.valueobject;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 租户业务键规范（domain 值对象，P3-T1 单一事实源）。
 *
 * <p>{@code tenantKey} 是租户的全局唯一业务标识，同时用于：子域名反解（{@code {tenantKey}.apex}）、SILO 集合命名 （{@code
 * kc_{tenantKey}}）、JWT claim、{@code sys_tenant.tenant_key} 列。为使「HTTP 入口校验」「平台端开通校验」 「SILO
 * 集合命名校验」三处口径<b>永不漂移</b>，字符集规则收敛到本 domain 常量，infrastructure 的 {@code SiloCollectionNaming} 与 app
 * 层开通 Executor 均引用之，不再各自持有字面量副本。
 *
 * <p><b>严格规则</b>（{@value #PATTERN}）：小写字母开头，后接小写字母 / 数字 / 下划线，总长 2–63 字符。 对齐 Milvus collection name
 * 约束（字母 / 下划线开头 + 字母 / 数字 / 下划线，长度 1–255），并给 SILO 前缀 {@code kc_} 留足余量。
 *
 * <p><b>与 HTTP 入口宽松规则的区别</b>：{@code TenantResolverFilter}（web 层）用的是更宽松的 {@code
 * ^[A-Za-z0-9_-]{1,64}$}，那是<b>请求头注入防护</b>的边界校验（拒绝 {@code ../}、空格等），不是开通合法性校验； 二者语义不同，刻意分离，不在本类合并。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
public final class TenantKey {

    /** 租户键严格正则：小写字母开头 + {@code [a-z0-9_]}，总长 2–63。 */
    public static final String PATTERN = "^[a-z][a-z0-9_]{1,62}$";

    /** {@link #PATTERN} 的预编译形态，避免热路径重复构造。 */
    private static final Pattern REGEX = Pattern.compile(PATTERN);

    private TenantKey() {}

    /**
     * 归一化：去除首尾空白并转小写（与库 collation utf8mb4_general_ci 的忽略大小写语义对齐， 且保证 {@code acme} 与 {@code ACME}
     * 不会静默映射到不同 SILO 集合）。
     *
     * @param raw 原始输入，可为 {@code null}
     * @return 归一化后的键；{@code null} 输入返回 {@code null}
     */
    public static String normalize(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 校验键是否合法（严格规则）：仅去除首尾空白后匹配，<b>不</b>自动转小写——含大写字母（如 {@code Acme}）直接拒绝， 与 {@code
     * SiloCollectionNaming.isValid} 口径一致，避免 {@code acme} 与 {@code ACME} 静默映射到同一 SILO 集合。 需要「宽松读 +
     * 归一」的调用方须显式先 {@link #normalize(String)} 再校验。
     *
     * @param raw 待校验字符串
     * @return 合法返回 {@code true}；{@code null} / 空白 / 含大写 / 不符规则返回 {@code false}
     */
    public static boolean isValid(String raw) {
        if (raw == null) {
            return false;
        }
        String trimmed = raw.trim();
        return !trimmed.isEmpty() && REGEX.matcher(trimmed).matches();
    }
}
