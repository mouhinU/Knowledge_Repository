package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.application.executor.docingestion.DocumentImageSupport;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentImage;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 看图题配图对外访问接口（adapter 层）。
 *
 * <p>位于 {@code /api/exam/assets/{assetKey}}，属考生侧放行前缀（{@link
 * com.mouhin.knowledge.repository.web.security.AdminTokenAuthFilter}）， 浏览器 {@code <img>}
 * 可直接拉取而无需附带令牌。鉴权通过<b>不可枚举的随机 {@code assetKey}</b> 承载 （UUID 无连字符，32 位）：仅持有句柄者可访问，句柄无效 / 缺失即
 * 404，避免枚举与目录穿越。 命中后按 {@code assetKey} 稳定不变的特点返回长时强缓存。
 *
 * @author Knowledge-Repository
 * @date 2026-09-20
 */
@RestController
@RequestMapping("/api/exam/assets")
@Slf4j
public class ExamAssetController {

    /** 只接受 32 位十六进制字符（UUID.randomUUID().toString().replace("-","")） */
    private static final Pattern ASSET_KEY_PATTERN = Pattern.compile("^[a-f0-9]{32}$");

    private final DocumentImageSupport documentImageSupport;

    public ExamAssetController(DocumentImageSupport documentImageSupport) {
        this.documentImageSupport = documentImageSupport;
    }

    /** 拉取图片二进制内容。句柄非法 / 未命中 / 文件缺失一律 404，不泄露任何内部信息。 */
    @GetMapping("/{assetKey}")
    public ResponseEntity<Resource> get(@PathVariable String assetKey) {
        if (assetKey == null || !ASSET_KEY_PATTERN.matcher(assetKey).matches()) {
            return ResponseEntity.notFound().build();
        }
        Optional<DocumentImage> image = documentImageSupport.findByAssetKey(assetKey);
        if (image.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Optional<byte[]> bytes = documentImageSupport.readBytes(image.get());
        if (bytes.isEmpty()) {
            log.warn("配图句柄命中但文件缺失 [assetKey={}]", assetKey);
            return ResponseEntity.notFound().build();
        }
        MediaType mt = resolveMediaType(image.get().getMimeType());
        return ResponseEntity.ok()
                .contentType(mt)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                .header(HttpHeaders.ETAG, "\"" + image.get().getSha256() + "\"")
                .body(new ByteArrayResource(bytes.get()));
    }

    private MediaType resolveMediaType(String mime) {
        if (mime == null || mime.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(mime);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
