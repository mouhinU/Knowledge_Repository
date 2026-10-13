package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.valueobject.StorageCategory;
import com.mouhin.knowledge.repository.domain.model.valueobject.StoredObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * 对象存储端口（P5-T1）。
 *
 * <p>domain 层定义的文件存储抽象，隔离 app 层与具体存储后端（本地磁盘 / MinIO / S3 兼容存储）。 infra 层提供实现：
 *
 * <ul>
 *   <li>{@code LocalObjectStorageGatewayImpl} — 本地磁盘，{@code knowledge.storage.backend=local}（默认）。
 *   <li>{@code MinioObjectStorageGatewayImpl} — MinIO 对象存储，{@code knowledge.storage.backend=minio}。
 * </ul>
 *
 * <p>设计约束：
 *
 * <ol>
 *   <li>返回值不含 infra 特有类型（红线 #7 domain 纯净）。
 *   <li>{@link StoredObject#storageKey()} 为 opaque 标识符，调用方存入 DB 后不做语义解析。
 *   <li>所有方法声明受检 {@link IOException}，由调用方决定异常处理策略。
 *   <li>租户隔离由实现保证——存储路径 / 对象 key 必须含 {@code tenantKey} 前缀。
 * </ol>
 *
 * @author mouhinU
 * @date 2026-10-12
 */
public interface ObjectStorageGateway {

    /**
     * 存储文件。
     *
     * @param content      文件内容流，调用方负责关闭（实现不得持有引用）
     * @param tenantKey    租户键，用于路径隔离
     * @param category     存储分类（决定路径前缀）
     * @param originalName 原始文件名，用于扩展名截取与路径穿越防护
     * @return 存储句柄，含 opaque {@code storageKey}
     * @throws IOException 存储失败（磁盘满 / MinIO 不可达等）
     */
    StoredObject store(
            InputStream content, String tenantKey, StorageCategory category, String originalName)
            throws IOException;

    /**
     * 存储文件到指定子目录下（考试配图场景需要 {@code {documentKey}/} 子目录层级）。
     *
     * @param content      文件内容流
     * @param tenantKey    租户键
     * @param category     存储分类
     * @param subDirectory 子目录段（如 documentKey）
     * @param fileName     目标文件名（如 {@code {assetKey}.png}）
     * @return 存储句柄
     * @throws IOException 存储失败
     */
    StoredObject store(
            InputStream content,
            String tenantKey,
            StorageCategory category,
            String subDirectory,
            String fileName)
            throws IOException;

    /**
     * 读取文件内容。
     *
     * @param storageKey 存储句柄（由 {@link #store} 返回）
     * @return 文件内容流，调用方负责关闭
     * @throws IOException 文件不存在 / 不可读 / 存储后端不可达
     */
    InputStream retrieve(String storageKey) throws IOException;

    /**
     * 删除文件。
     *
     * @param storageKey 存储句柄
     * @return {@code true} 成功删除；{@code false} 文件不存在
     * @throws IOException 存储后端不可达
     */
    boolean delete(String storageKey) throws IOException;

    /**
     * 检查文件是否存在。
     *
     * @param storageKey 存储句柄
     * @return {@code true} 存在
     * @throws IOException 存储后端不可达
     */
    boolean exists(String storageKey) throws IOException;

    /**
     * 查询文件元数据。
     *
     * @param storageKey 存储句柄
     * @return 文件元数据；不存在时返回空
     * @throws IOException 存储后端不可达
     */
    Optional<StoredObject> getMetadata(String storageKey) throws IOException;
}
