package com.mouhin.knowledge.repository.infrastructure.storage;

import com.mouhin.knowledge.repository.domain.gateway.ObjectStorageGateway;
import com.mouhin.knowledge.repository.domain.model.valueobject.StorageCategory;
import com.mouhin.knowledge.repository.domain.model.valueobject.StoredObject;
import com.mouhin.knowledge.repository.domain.util.PathGuard;
import com.mouhin.knowledge.repository.infrastructure.config.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * MinIO 对象存储实现（P5-T1）。
 *
 * <p>对象 key 格式：{@code {tenantKey}/{category}/{yyyy}/{mm}/{uuid}.{ext}}，租户隔离由路径前缀保证。
 *
 * <p>装配条件：{@code knowledge.storage.backend=minio}，由 {@link StorageConfig} 按条件注入。
 * 启动时校验桶存在性（可配自动创建），运行期通过 MinIO Java SDK 执行 CRUD。
 *
 * <p>安全约束：
 *
 * <ul>
 *   <li>扩展名白名单由 {@link PathGuard#sanitizeExtension} 保证，杜绝路径穿越。
 *   <li>对象 key 不含 {@code ..} / 绝对路径，纯拼接生成，不接受用户输入。
 * </ul>
 *
 * @author mouhinU
 * @date 2026-10-12
 */
@Slf4j
public class MinioObjectStorageGatewayImpl implements ObjectStorageGateway {

    private final StorageProperties.Minio minioProps;
    private MinioClient minioClient;

    public MinioObjectStorageGatewayImpl(StorageProperties props) {
        this.minioProps = props.getMinio();
    }

    @PostConstruct
    void init() {
        minioClient =
                MinioClient.builder()
                        .endpoint(minioProps.getEndpoint())
                        .credentials(minioProps.getAccessKey(), minioProps.getSecretKey())
                        .build();

        if (minioProps.isAutoCreateBucket()) {
            ensureBucketExists();
        }
        log.info(
                "MinIO 对象存储已初始化 [endpoint={}, bucket={}, region={}]",
                minioProps.getEndpoint(),
                minioProps.getBucket(),
                minioProps.getRegion());
    }

    @Override
    public StoredObject store(
            InputStream content, String tenantKey, StorageCategory category, String originalName)
            throws IOException {
        String ext = PathGuard.sanitizeExtension(originalName);
        String fileName = UUID.randomUUID() + ext;
        String objectKey = buildObjectKey(tenantKey, category, fileName);

        try {
            // MinIO PutObjectArgs 需要 content size，-1 表示分片上传（partSize 自动）
            // 对于小文件（< 5MB）直接单次 PUT；大文件自动分片
            byte[] bytes = content.readAllBytes();
            minioClient.putObject(
                    PutObjectArgs.builder().bucket(minioProps.getBucket()).object(objectKey).stream(
                                    new java.io.ByteArrayInputStream(bytes), bytes.length, -1)
                            .build());

            log.debug(
                    "MinIO 存储完成 [tenant={}, category={}, key={}, size={}]",
                    tenantKey,
                    category,
                    objectKey,
                    bytes.length);

            return new StoredObject(
                    objectKey,
                    originalName,
                    null,
                    bytes.length,
                    tenantKey,
                    category,
                    Instant.now());
        } catch (Exception e) {
            throw new IOException(
                    "MinIO store failed [key=" + objectKey + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public StoredObject store(
            InputStream content,
            String tenantKey,
            StorageCategory category,
            String subDirectory,
            String fileName)
            throws IOException {
        String objectKey = buildObjectKeyWithSubDir(tenantKey, category, subDirectory, fileName);

        try {
            byte[] bytes = content.readAllBytes();
            minioClient.putObject(
                    PutObjectArgs.builder().bucket(minioProps.getBucket()).object(objectKey).stream(
                                    new java.io.ByteArrayInputStream(bytes), bytes.length, -1)
                            .build());

            log.debug(
                    "MinIO 存储完成（含子目录）[tenant={}, category={}, key={}, size={}]",
                    tenantKey,
                    category,
                    objectKey,
                    bytes.length);

            return new StoredObject(
                    objectKey, fileName, null, bytes.length, tenantKey, category, Instant.now());
        } catch (Exception e) {
            throw new IOException(
                    "MinIO store failed [key=" + objectKey + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream retrieve(String storageKey) throws IOException {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioProps.getBucket())
                            .object(storageKey)
                            .build());
        } catch (Exception e) {
            throw new IOException(
                    "MinIO retrieve failed [key=" + storageKey + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean delete(String storageKey) throws IOException {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProps.getBucket())
                            .object(storageKey)
                            .build());
            log.debug("MinIO 删除完成 [key={}]", storageKey);
            return true;
        } catch (Exception e) {
            // MinIO SDK 对不存在的对象删除不抛异常，但网络错误等会抛
            throw new IOException(
                    "MinIO delete failed [key=" + storageKey + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String storageKey) throws IOException {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(minioProps.getBucket())
                            .object(storageKey)
                            .build());
            return true;
        } catch (Exception e) {
            // MinIO SDK 对不存在的对象 statObject 抛 ErrorResponseException(code=NoSuchKey)
            String msg = e.getMessage();
            if (msg != null && (msg.contains("NoSuchKey") || msg.contains("not found"))) {
                return false;
            }
            throw new IOException(
                    "MinIO exists check failed [key=" + storageKey + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<StoredObject> getMetadata(String storageKey) throws IOException {
        try {
            StatObjectResponse stat =
                    minioClient.statObject(
                            StatObjectArgs.builder()
                                    .bucket(minioProps.getBucket())
                                    .object(storageKey)
                                    .build());
            return Optional.of(
                    new StoredObject(
                            storageKey,
                            extractFileName(storageKey),
                            stat.contentType(),
                            stat.size(),
                            extractTenantKey(storageKey),
                            extractCategory(storageKey),
                            stat.lastModified().toInstant()));
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("NoSuchKey") || msg.contains("not found"))) {
                return Optional.empty();
            }
            throw new IOException(
                    "MinIO metadata failed [key=" + storageKey + "]: " + e.getMessage(), e);
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 构建对象 key：{@code {tenantKey}/{category}/{yyyy}/{mm}/{uuid}.{ext}}。
     *
     * <p>日期分桶按月粒度，万级文件分布均匀。
     */
    private String buildObjectKey(String tenantKey, StorageCategory category, String fileName) {
        YearMonth now = YearMonth.now(ZoneOffset.UTC);
        return tenantKey
                + "/"
                + category.pathSegment()
                + "/"
                + now.getYear()
                + "/"
                + String.format("%02d", now.getMonthValue())
                + "/"
                + fileName;
    }

    /**
     * 构建含子目录的对象 key：{@code {tenantKey}/{category}/{subDirectory}/{fileName}}。
     *
     * <p>用于考试配图等需要按 documentKey 分组的场景。
     */
    private String buildObjectKeyWithSubDir(
            String tenantKey, StorageCategory category, String subDirectory, String fileName) {
        return tenantKey + "/" + category.pathSegment() + "/" + subDirectory + "/" + fileName;
    }

    private void ensureBucketExists() {
        try {
            boolean exists =
                    minioClient.bucketExists(
                            BucketExistsArgs.builder().bucket(minioProps.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(minioProps.getBucket()).build());
                log.info("MinIO 桶已自动创建 [bucket={}]", minioProps.getBucket());
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "MinIO bucket check/create failed [bucket="
                            + minioProps.getBucket()
                            + "]: "
                            + e.getMessage(),
                    e);
        }
    }

    private String extractFileName(String objectKey) {
        int slash = objectKey.lastIndexOf('/');
        return slash >= 0 ? objectKey.substring(slash + 1) : objectKey;
    }

    private String extractTenantKey(String objectKey) {
        int slash = objectKey.indexOf('/');
        return slash > 0 ? objectKey.substring(0, slash) : "default";
    }

    private StorageCategory extractCategory(String objectKey) {
        // key 格式：{tenantKey}/{category}/...
        String[] parts = objectKey.split("/");
        if (parts.length >= 2) {
            for (StorageCategory cat : StorageCategory.values()) {
                if (cat.pathSegment().equals(parts[1])) {
                    return cat;
                }
            }
        }
        return StorageCategory.DOCUMENT;
    }
}
