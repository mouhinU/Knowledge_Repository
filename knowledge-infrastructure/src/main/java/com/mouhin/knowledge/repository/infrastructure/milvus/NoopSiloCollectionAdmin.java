package com.mouhin.knowledge.repository.infrastructure.milvus;

import com.mouhin.knowledge.repository.domain.gateway.SiloCollectionAdmin;
import com.mouhin.knowledge.repository.domain.model.valueobject.SiloCollectionSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SILO 集合管理空实现（P3-T6 门控兜底）。
 *
 * <p>当 {@code knowledge.platform.enabled=false}（默认）时装配，所有读方法返回空结果，不连接 Milvus。 与 {@code
 * MilvusSiloCollectionAdmin} 构成双驱动，确保 {@code TenantSchemaRunner} 与 {@code
 * PlatformSchemaHealthController} 在关闭态下可正常注入、不触碰任何数据。
 *
 * @author QoderWork
 * @date 2026-10-12
 */
@Component
@Slf4j
@ConditionalOnProperty(
        name = "knowledge.platform.enabled",
        havingValue = "false",
        matchIfMissing = true)
public class NoopSiloCollectionAdmin implements SiloCollectionAdmin {

    @Override
    public List<SiloCollectionSchema> listSiloCollections() {
        log.debug("[schema-runner] platform disabled, returning empty SILO list");
        return List.of();
    }

    @Override
    public SiloCollectionSchema describeSiloCollection(String tenantKey) {
        throw new UnsupportedOperationException(
                "SILO collection describe unavailable when platform is disabled");
    }

    @Override
    public boolean hasSiloCollection(String tenantKey) {
        return false;
    }
}
