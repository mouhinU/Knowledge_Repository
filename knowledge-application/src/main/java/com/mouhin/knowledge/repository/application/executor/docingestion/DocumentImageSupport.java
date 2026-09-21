package com.mouhin.knowledge.repository.application.executor.docingestion;

import com.mouhin.knowledge.repository.domain.gateway.DocumentImageExtractorGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentImageGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentImage;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentImageHit;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 文档图片落盘 / 读取 / 回填共享支撑（app 层）。
 *
 * <p>收敛「抽取 → 去重 → 落盘 → 持久化」与「按句柄回读字节」两类跨用例复用的协作逻辑：上传摄入用例在 文本提取后调用 {@link #extractAndPersist}
 * 顺带处理配图（内部吞异常，绝不阻断文本入库）；管理端回填 用例调用 {@link #backfill} 对已入库文档按其 {@code storage_path}
 * 重跑一次图片提取；图片流接口调用 {@link #findByAssetKey} + {@link #readBytes} 回读。二进制落 {@code
 * knowledge.exam.asset-path} 独立目录。
 *
 * @author Knowledge-Repository
 * @date 2026-09-20
 */
@Component
public class DocumentImageSupport {

    private static final Logger logger = LoggerFactory.getLogger(DocumentImageSupport.class);

    private final DocumentImageExtractorGateway extractor;
    private final DocumentImageGateway imageGateway;
    private final Path assetRoot;

    public DocumentImageSupport(
            DocumentImageExtractorGateway extractor,
            DocumentImageGateway imageGateway,
            @Value("${knowledge.exam.asset-path:./data/exam-assets}") String assetDir) {
        this.extractor = extractor;
        this.imageGateway = imageGateway;
        this.assetRoot = Path.of(assetDir);
        try {
            Files.createDirectories(this.assetRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create exam asset directory: " + assetDir, e);
        }
    }

    /**
     * 从源文件抽取内嵌图片并落盘 + 持久化，返回新增图片数。
     *
     * <p>文档内按 SHA-256 去重；任何异常吞掉并记日志，返回已处理数，避免影响调用方主流程。
     */
    public int extractAndPersist(Document doc, Path sourcePath) {
        if (doc == null || doc.getId() == null || sourcePath == null || !Files.exists(sourcePath)) {
            return 0;
        }
        List<ExtractedImage> images;
        try {
            images = extractor.extractImages(sourcePath, doc.getFileName());
        } catch (Exception e) {
            logger.warn("图片抽取失败，跳过 [documentKey={}]: {}", doc.getDocumentKey(), e.getMessage());
            return 0;
        }
        int added = 0;
        for (ExtractedImage img : images) {
            try {
                String sha = sha256(img.bytes());
                if (imageGateway.existsByDocumentIdAndSha256(doc.getId(), sha)) {
                    continue;
                }
                String assetKey = UUID.randomUUID().toString().replace("-", "");
                Path dir = assetRoot.resolve(doc.getDocumentKey());
                Files.createDirectories(dir);
                Path file = dir.resolve(assetKey + extFromMime(img.mimeType()));
                Files.write(file, img.bytes());

                DocumentImage entity = new DocumentImage();
                entity.setAssetKey(assetKey);
                entity.setDocumentId(doc.getId());
                entity.setDocumentKey(doc.getDocumentKey());
                entity.setStoragePath(file.toString());
                entity.setSha256(sha);
                entity.setMimeType(img.mimeType());
                entity.setPageNo(img.pageOrSeq());
                entity.setSeqOnPage(img.seqOnPage());
                entity.setWidth(img.width());
                entity.setHeight(img.height());
                entity.setByteSize((long) img.bytes().length);
                try {
                    imageGateway.save(entity);
                } catch (Exception dup) {
                    // 唯一键冲突等：清理刚落盘的孤儿文件后继续
                    Files.deleteIfExists(file);
                    logger.debug("图片落库冲突，跳过并清理: {}", file);
                    continue;
                }
                added++;
            } catch (Exception e) {
                logger.warn("单张图片落盘失败 [documentKey={}]: {}", doc.getDocumentKey(), e.getMessage());
            }
        }
        if (added > 0) {
            logger.info("文档图片落盘完成 [documentKey={}, added={}]", doc.getDocumentKey(), added);
        }
        return added;
    }

    /**
     * 清除某文档的全部配图：先删落盘文件（仅 assetRoot 之内，越界跳过），再删关系表记录。
     *
     * <p>用于「重新入库」前清理旧配图（用户选定「删图并重抽」）。文件删除为尽力而为，单个失败仅记日志， 不阻断主流程；记录删除始终执行。此处删除的是应用生成的 asset
     * 文件（非用户上传源文档），符合重抽语义。
     *
     * @return 成功删除的磁盘文件数
     */
    public int deleteImages(Document doc) {
        if (doc == null || doc.getId() == null) {
            return 0;
        }
        List<DocumentImage> images = imageGateway.listByDocumentId(doc.getId());
        int filesRemoved = 0;
        for (DocumentImage image : images) {
            if (image.getStoragePath() == null || image.getStoragePath().isBlank()) {
                continue;
            }
            try {
                Path file = Path.of(image.getStoragePath()).normalize();
                if (!file.startsWith(assetRoot.normalize())) {
                    logger.warn(
                            "配图文件越界，跳过删除 [assetKey={}, path={}]",
                            image.getAssetKey(),
                            image.getStoragePath());
                    continue;
                }
                if (Files.deleteIfExists(file)) {
                    filesRemoved++;
                }
            } catch (IOException e) {
                logger.warn(
                        "删除配图文件失败 [assetKey={}, path={}]: {}",
                        image.getAssetKey(),
                        image.getStoragePath(),
                        e.getMessage());
            }
        }
        imageGateway.deleteByDocumentId(doc.getId());
        logger.info(
                "文档配图已清除 [documentKey={}, filesRemoved={}]", doc.getDocumentKey(), filesRemoved);
        return filesRemoved;
    }

    /**
     * 回填：对已入库文档按其持久存储路径重跑一次图片提取。
     *
     * @return 新增图片数；源文件缺失或不可读时返回 0
     */
    public int backfill(Document doc) {
        if (doc == null || doc.getStoragePath() == null || doc.getStoragePath().isBlank()) {
            return 0;
        }
        Path source = Path.of(doc.getStoragePath());
        if (!Files.exists(source)) {
            logger.warn("回填跳过：源文件不存在 [documentKey={}, path={}]", doc.getDocumentKey(), source);
            return 0;
        }
        return extractAndPersist(doc, source);
    }

    /** 按对外句柄查找图片元数据。 */
    public Optional<DocumentImage> findByAssetKey(String assetKey) {
        return imageGateway.findByAssetKey(assetKey);
    }

    /** 回读图片二进制；文件缺失 / 越权访问时返回空。 */
    public Optional<byte[]> readBytes(DocumentImage image) {
        if (image == null || image.getStoragePath() == null) {
            return Optional.empty();
        }
        try {
            Path file = Path.of(image.getStoragePath()).normalize();
            if (!file.startsWith(assetRoot.normalize()) || !Files.exists(file)) {
                logger.warn("图片文件越界或缺失，拒绝读取: {}", image.getStoragePath());
                return Optional.empty();
            }
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException e) {
            logger.warn("读取图片失败 [assetKey={}]: {}", image.getAssetKey(), e.getMessage());
            return Optional.empty();
        }
    }

    /** 列出某文档全部图片。 */
    public List<DocumentImage> listByDocument(Document doc) {
        if (doc == null || doc.getId() == null) {
            return List.of();
        }
        return imageGateway.listByDocumentId(doc.getId());
    }

    /** 全局图片检索（校对页选图），命中项携带来源文档名。 */
    public List<DocumentImageHit> searchImages(
            String keyword, String documentKey, int limit, int offset) {
        return imageGateway.search(keyword, documentKey, limit, offset);
    }

    /** 统计全局图片检索同条件命中总数。 */
    public long countImages(String keyword, String documentKey) {
        return imageGateway.countSearch(keyword, documentKey);
    }

    // ==================== 工具 ====================

    private String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String extFromMime(String mime) {
        if (mime == null) {
            return ".png";
        }
        return switch (mime.toLowerCase(java.util.Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/bmp" -> ".bmp";
            case "image/webp" -> ".webp";
            case "image/tiff" -> ".tiff";
            default -> ".png";
        };
    }
}
