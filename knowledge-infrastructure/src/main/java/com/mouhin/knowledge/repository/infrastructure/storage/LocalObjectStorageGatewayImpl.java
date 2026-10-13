package com.mouhin.knowledge.repository.infrastructure.storage;

import com.mouhin.knowledge.repository.domain.gateway.ObjectStorageGateway;
import com.mouhin.knowledge.repository.domain.model.valueobject.StorageCategory;
import com.mouhin.knowledge.repository.domain.model.valueobject.StoredObject;
import com.mouhin.knowledge.repository.domain.util.PathGuard;
import com.mouhin.knowledge.repository.infrastructure.config.StorageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * 本地磁盘对象存储实现（P5-T1）。
 *
 * <p>行为与 Phase 1~4 的 {@code DocumentIngestionSupport.copyToStorage()} / {@code
 * DocumentImageSupport.extractAndPersist()} 等价，保证 {@code saas.enabled=false} 关闭态行为零变化。
 *
 * <p>存储路径格式：
 *
 * <ul>
 *   <li>DOCUMENT → {@code {storagePath}/{uuid}.{ext}}（沿用既有格式，向后兼容 DB 存量 {@code storage_path}）
 *   <li>EXAM_ASSET → {@code {examAssetPath}/{documentKey}/{assetKey}{ext}}（需调用方传入子目录段）
 * </ul>
 *
 * <p>{@link StoredObject#storageKey()} 返回绝对路径字符串，与既有 {@code Document.storagePath} / {@code
 * DocumentImage.storagePath} 格式一致，无需迁移存量数据。
 *
 * @author mouhinU
 * @date 2026-10-12
 */
@Slf4j
public class LocalObjectStorageGatewayImpl implements ObjectStorageGateway {

    private final Path documentRoot;
    private final Path examAssetRoot;

    public LocalObjectStorageGatewayImpl(StorageProperties props) {
        this.documentRoot = Path.of(props.getPath()).toAbsolutePath().normalize();
        this.examAssetRoot = Path.of(props.getExamAssetPath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.documentRoot);
            Files.createDirectories(this.examAssetRoot);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot create storage directories: " + e.getMessage(), e);
        }
        log.info("本地磁盘对象存储已初始化 [documentRoot={}, examAssetRoot={}]", documentRoot, examAssetRoot);
    }

    @Override
    public StoredObject store(
            InputStream content, String tenantKey, StorageCategory category, String originalName)
            throws IOException {
        String ext = PathGuard.sanitizeExtension(originalName);
        String fileName = UUID.randomUUID() + ext;

        Path root = resolveRoot(category);
        Path target = PathGuard.resolveWithin(root, fileName);
        Files.createDirectories(target.getParent());

        long bytes = Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);

        log.debug(
                "本地存储完成 [tenant={}, category={}, key={}, size={}]",
                tenantKey,
                category,
                target,
                bytes);

        return new StoredObject(
                target.toString(), originalName, null, bytes, tenantKey, category, Instant.now());
    }

    @Override
    public StoredObject store(
            InputStream content,
            String tenantKey,
            StorageCategory category,
            String subDirectory,
            String fileName)
            throws IOException {
        Path root = resolveRoot(category);
        String relativePath = subDirectory + "/" + fileName;
        Path target = PathGuard.resolveWithin(root, relativePath);
        Files.createDirectories(target.getParent());

        long bytes = Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);

        log.debug(
                "本地存储完成（含子目录）[tenant={}, category={}, key={}, size={}]",
                tenantKey,
                category,
                target,
                bytes);

        return new StoredObject(
                target.toString(), fileName, null, bytes, tenantKey, category, Instant.now());
    }

    @Override
    public InputStream retrieve(String storageKey) throws IOException {
        Path file = resolveStorageKey(storageKey);
        if (!Files.exists(file)) {
            throw new IOException("Storage file not found: " + storageKey);
        }
        return Files.newInputStream(file);
    }

    @Override
    public boolean delete(String storageKey) throws IOException {
        Path file = resolveStorageKey(storageKey);
        if (!Files.exists(file)) {
            return false;
        }
        // 安全校验：只允许删除允许根目录内的文件
        if (!isWithinAllowedRoots(file)) {
            log.warn("删除拒绝：路径越界 [key={}]", storageKey);
            return false;
        }
        return Files.deleteIfExists(file);
    }

    @Override
    public boolean exists(String storageKey) throws IOException {
        Path file = resolveStorageKey(storageKey);
        return Files.exists(file);
    }

    @Override
    public Optional<StoredObject> getMetadata(String storageKey) throws IOException {
        Path file = resolveStorageKey(storageKey);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        long size = Files.size(file);
        Instant lastModified = Files.getLastModifiedTime(file).toInstant();
        String fileName = file.getFileName().toString();
        return Optional.of(
                new StoredObject(
                        storageKey,
                        fileName,
                        Files.probeContentType(file),
                        size,
                        "default",
                        StorageCategory.DOCUMENT,
                        lastModified));
    }

    /** 返回文档存储根目录（供重构后的 DocumentIngestionSupport 使用）。 */
    public Path getDocumentRoot() {
        return documentRoot;
    }

    /** 返回考试配图存储根目录（供重构后的 DocumentImageSupport 使用）。 */
    public Path getExamAssetRoot() {
        return examAssetRoot;
    }

    // ==================== 内部方法 ====================

    private Path resolveRoot(StorageCategory category) {
        return switch (category) {
            case DOCUMENT -> documentRoot;
            case EXAM_ASSET -> examAssetRoot;
        };
    }

    /**
     * 解析 storageKey 为 Path。
     *
     * <p>向后兼容：DB 存量的 {@code storage_path} 是绝对路径字符串，直接 {@code Path.of()} 解析即可。
     * 新格式同样是绝对路径（本地模式下两者等价）。
     */
    private Path resolveStorageKey(String storageKey) {
        return Path.of(storageKey).toAbsolutePath().normalize();
    }

    /** 校验路径是否在允许的根目录之内（文档根 or 配图根）。 */
    private boolean isWithinAllowedRoots(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        return normalized.startsWith(documentRoot) || normalized.startsWith(examAssetRoot);
    }
}
