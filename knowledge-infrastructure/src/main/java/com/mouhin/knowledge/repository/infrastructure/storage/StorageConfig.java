package com.mouhin.knowledge.repository.infrastructure.storage;

import com.mouhin.knowledge.repository.domain.gateway.ObjectStorageGateway;
import com.mouhin.knowledge.repository.infrastructure.config.StorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对象存储双驱动装配配置（P5-T1）。
 *
 * <p>按 {@code knowledge.storage.backend} 属性值条件装配：
 *
 * <ul>
 *   <li>{@code local}（默认）→ {@link LocalObjectStorageGatewayImpl}
 *   <li>{@code minio} → {@link MinioObjectStorageGatewayImpl}
 * </ul>
 *
 * <p>{@code saas.enabled=false} 关闭态下默认走 {@code local}，行为与 Phase 1~4 等价。 两个实现互斥，同一时刻只有一个 Bean 存在于
 * Spring 上下文。
 *
 * @author mouhinU
 * @date 2026-10-12
 */
@Configuration
@Slf4j
public class StorageConfig {

    /**
     * 本地磁盘存储后端（默认）。
     *
     * <p>当 {@code knowledge.storage.backend} 未设置或为 {@code local} 时装配。 {@code matchIfMissing = true}
     * 保证无配置时默认走本地磁盘。
     */
    @Bean
    @ConditionalOnProperty(
            name = "knowledge.storage.backend",
            havingValue = "local",
            matchIfMissing = true)
    public ObjectStorageGateway localObjectStorageGateway(StorageProperties props) {
        log.info(
                "对象存储后端：本地磁盘 [path={}, examAssetPath={}]",
                props.getPath(),
                props.getExamAssetPath());
        return new LocalObjectStorageGatewayImpl(props);
    }

    /**
     * MinIO 对象存储后端。
     *
     * <p>当 {@code knowledge.storage.backend=minio} 时装配。需 MinIO 容器可达。
     */
    @Bean
    @ConditionalOnProperty(name = "knowledge.storage.backend", havingValue = "minio")
    public ObjectStorageGateway minioObjectStorageGateway(StorageProperties props) {
        log.info(
                "对象存储后端：MinIO [endpoint={}, bucket={}]",
                props.getMinio().getEndpoint(),
                props.getMinio().getBucket());
        return new MinioObjectStorageGatewayImpl(props);
    }
}
