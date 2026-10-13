package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.valueobject.SiloCollectionSchema;
import java.util.List;

/**
 * SILO 独立集合管理端口（domain 定义，infrastructure 以 Milvus SDK 实现）。
 *
 * <p>Phase 3 T-3=方案 A：SILO 只建独立 collection（{@code kc_{tenantKey}}），不建独立 database，全部共享 {@code
 * default} database。本端口提供集合级读操作——列举 SILO 集合、查询集合 schema 描述——供 {@code TenantSchemaRunner}（P3-T6）做
 * schema 对齐巡检与健康体检。
 *
 * <p><b>范围边界</b>：本端口只暴露<b>读方法</b>（list / describe）。写方法（{@code provision} / {@code drop}）属 P3-T5
 * Archive &amp; Delete 编排的续交范围，待首批 SILO 租户上线前在同一接口追加。
 *
 * @author QoderWork
 * @date 2026-10-12
 */
public interface SiloCollectionAdmin {

    /**
     * 列举当前 database 下所有 SILO 形态集合（以 {@code kc_} 前缀开头且剩余部分符合命名规范的集合）。
     *
     * <p>返回值为已过滤的 SILO 集合 schema 摘要列表；POOL 主集合（{@code knowledge_chunks}）与其他非 SILO 集合不包含在结果中。 无 SILO
     * 集合时返回空列表（不返回 {@code null}）。
     *
     * @return SILO 集合 schema 摘要列表，永不为 {@code null}
     */
    List<SiloCollectionSchema> listSiloCollections();

    /**
     * 查询指定 SILO 集合的 schema 描述。
     *
     * @param tenantKey 租户业务键（不含前缀）
     * @return 集合 schema 摘要
     * @throws IllegalArgumentException 集合不存在或命名不合法
     */
    SiloCollectionSchema describeSiloCollection(String tenantKey);

    /**
     * 检查指定 SILO 集合是否存在。
     *
     * @param tenantKey 租户业务键（不含前缀）
     * @return 存在返回 {@code true}
     */
    boolean hasSiloCollection(String tenantKey);
}
