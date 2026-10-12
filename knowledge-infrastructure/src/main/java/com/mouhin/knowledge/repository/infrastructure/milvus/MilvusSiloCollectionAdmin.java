package com.mouhin.knowledge.repository.infrastructure.milvus;

import com.mouhin.knowledge.repository.domain.gateway.SiloCollectionAdmin;
import com.mouhin.knowledge.repository.domain.model.valueobject.SiloCollectionSchema;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SILO 集合管理 Milvus SDK 实现（P3-T6，T-3=方案 A collection-only）。
 *
 * <p>使用 {@link MilvusClientV2} 直连 Milvus，提供集合列举与 schema 描述能力。仅当 {@code
 * knowledge.platform.enabled=true} 时装配（与 {@code TenantDeletionReconciler} 对齐双闸门）。
 *
 * <p><b>连接管理</b>：本类持有独立于 LangChain4j {@code MilvusEmbeddingStore} 的 {@link MilvusClientV2} 实例， 复用同一
 * Milvus host/port/database 配置。采用懒初始化 + 线性退避重试（镜像 {@code MilvusConfig} 模式），避免启动期 Milvus 未就绪导致上下文失败。
 *
 * <p><b>范围</b>：本类只实现读方法（list / describe / has）；写方法（provision / drop）属 P3-T5 续交范围。
 *
 * @author QoderWork
 * @date 2026-10-12
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "knowledge.platform.enabled", havingValue = "true")
public class MilvusSiloCollectionAdmin implements SiloCollectionAdmin {

    /** 建连失败最大重试次数。 */
    private static final int MAX_CONNECT_ATTEMPTS = 3;

    /** 重试基础退避（毫秒）。 */
    private static final long RETRY_BACKOFF_BASE_MS = 1000L;

    private final String host;
    private final int port;
    private final String databaseName;

    /** 懒初始化 Milvus 客户端（首次调用时建连）。 */
    private volatile MilvusClientV2 client;

    private final Object clientLock = new Object();

    public MilvusSiloCollectionAdmin(
            @Value("${knowledge.milvus.host:localhost}") String host,
            @Value("${knowledge.milvus.port:19530}") int port,
            @Value("${knowledge.milvus.database-name:default}") String databaseName) {
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
    }

    @Override
    public List<SiloCollectionSchema> listSiloCollections() {
        ListCollectionsResp resp = getClient().listCollections();
        List<String> allNames = resp.getCollectionNames();
        if (allNames == null || allNames.isEmpty()) {
            return List.of();
        }
        List<SiloCollectionSchema> result = new ArrayList<>();
        for (String name : allNames) {
            if (!SiloCollectionNaming.isSiloCollectionName(name)) {
                continue;
            }
            try {
                SiloCollectionSchema schema = describeByName(name);
                result.add(schema);
            } catch (RuntimeException e) {
                log.warn(
                        "[schema-runner] describe SILO collection '{}' failed, skip: {}",
                        name,
                        e.getMessage());
            }
        }
        return result;
    }

    @Override
    public SiloCollectionSchema describeSiloCollection(String tenantKey) {
        SiloCollectionNaming.assertValidForProvisioning(tenantKey);
        String collectionName = SiloCollectionNaming.toCollectionName(tenantKey);
        if (!hasSiloCollection(tenantKey)) {
            throw new IllegalArgumentException(
                    "SILO collection not found: "
                            + collectionName
                            + " (tenantKey="
                            + tenantKey
                            + ")");
        }
        return describeByName(collectionName);
    }

    @Override
    public boolean hasSiloCollection(String tenantKey) {
        if (!SiloCollectionNaming.isValid(tenantKey)) {
            return false;
        }
        String collectionName = SiloCollectionNaming.toCollectionName(tenantKey);
        try {
            Boolean exists =
                    getClient()
                            .hasCollection(
                                    io.milvus.v2.service.collection.request.HasCollectionReq
                                            .builder()
                                            .collectionName(collectionName)
                                            .build());
            return Boolean.TRUE.equals(exists);
        } catch (RuntimeException e) {
            log.warn(
                    "[schema-runner] hasCollection check failed for '{}': {}",
                    collectionName,
                    e.getMessage());
            return false;
        }
    }

    private SiloCollectionSchema describeByName(String collectionName) {
        DescribeCollectionResp resp =
                getClient()
                        .describeCollection(
                                DescribeCollectionReq.builder()
                                        .collectionName(collectionName)
                                        .build());
        String tenantKey = SiloCollectionNaming.fromCollectionName(collectionName);
        return new SiloCollectionSchema(
                collectionName,
                tenantKey,
                resp.getFieldNames(),
                resp.getVectorFieldNames(),
                Boolean.TRUE.equals(resp.getEnableDynamicField()),
                resp.getNumOfPartitions() != null ? resp.getNumOfPartitions() : 0L);
    }

    /** 双重检查锁懒初始化 {@link MilvusClientV2}。 */
    private MilvusClientV2 getClient() {
        if (client == null) {
            synchronized (clientLock) {
                if (client == null) {
                    client = buildClientWithRetry();
                }
            }
        }
        return client;
    }

    private MilvusClientV2 buildClientWithRetry() {
        String uri = "http://" + host + ":" + port;
        ConnectConfig config = ConnectConfig.builder().uri(uri).dbName(databaseName).build();
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= MAX_CONNECT_ATTEMPTS; attempt++) {
            try {
                MilvusClientV2 c = new MilvusClientV2(config);
                if (attempt > 1) {
                    log.info("MilvusClientV2 建连成功（第 {} 次尝试）", attempt);
                }
                return c;
            } catch (RuntimeException e) {
                lastError = e;
                log.warn(
                        "MilvusClientV2 建连失败（第 {}/{} 次）：{}",
                        attempt,
                        MAX_CONNECT_ATTEMPTS,
                        e.getMessage());
                if (attempt < MAX_CONNECT_ATTEMPTS) {
                    sleepBackoff(attempt);
                }
            }
        }
        throw new IllegalStateException(
                "MilvusClientV2 初始化失败，已重试 " + MAX_CONNECT_ATTEMPTS + " 次", lastError);
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(RETRY_BACKOFF_BASE_MS * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MilvusClientV2 初始化重试被中断", ie);
        }
    }
}
