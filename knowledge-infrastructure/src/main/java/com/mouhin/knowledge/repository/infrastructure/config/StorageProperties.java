package com.mouhin.knowledge.repository.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对象存储配置属性（P5-T1）。
 *
 * <p>绑定 {@code knowledge.storage.*} 命名空间，控制存储后端选择与 MinIO 连接参数。 双驱动装配策略：
 *
 * <ul>
 *   <li>{@code backend=local}（默认）→ 装配 {@code LocalObjectStorageGatewayImpl}，行为与 Phase 1~4 等价。
 *   <li>{@code backend=minio} → 装配 {@code MinioObjectStorageGatewayImpl}，需 MinIO 容器可达。
 * </ul>
 *
 * <p>{@code saas.enabled=false} 关闭态下，{@code local} 模式行为零变化（向后兼容）。
 *
 * @author mouhinU
 * @date 2026-10-12
 */
@Component
@ConfigurationProperties(prefix = "knowledge.storage")
@Getter
@Setter
public class StorageProperties {

    /** 存储后端：{@code local}（默认）或 {@code minio}。 */
    private String backend = "local";

    /** 本地磁盘模式下的文档存储根目录。 */
    private String path = "./data/documents";

    /** 本地磁盘模式下的考试配图存储根目录。 */
    private String examAssetPath = "./data/exam-assets";

    /** MinIO 子配置。 */
    private Minio minio = new Minio();

    /** 当前是否使用 MinIO 后端。 */
    public boolean isMinioBackend() {
        return "minio".equalsIgnoreCase(backend);
    }

    /** MinIO 连接参数。 */
    @Getter
    @Setter
    public static class Minio {

        /** MinIO 服务端地址（含 scheme），如 {@code http://localhost:9000}。 */
        private String endpoint = "http://localhost:9000";

        /** 访问密钥。 */
        private String accessKey = "minioadmin";

        /** 秘密密钥。 */
        private String secretKey = "minioadmin";

        /** 存储桶名称。 */
        private String bucket = "knowledge";

        /** 区域（MinIO 单节点可忽略，集群模式需匹配）。 */
        private String region = "";

        /** 启动时是否自动创建桶（{@code MakeBucketIfNotExists}）。 */
        private boolean autoCreateBucket = true;
    }
}
