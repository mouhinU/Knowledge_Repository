package com.mouhin.knowledge.repository.infrastructure.extractor;

import com.mouhin.knowledge.repository.domain.gateway.ContentExtractor;
import com.mouhin.knowledge.repository.domain.gateway.DocumentExtractionGateway;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionCandidate;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionStrategyEnum;
import com.mouhin.knowledge.repository.infrastructure.config.ExtractorRoutingProperties;
import com.mouhin.knowledge.repository.infrastructure.extraction.ExtractionSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 组合式文档提取服务：MIME 检测 + 策略路由 + 顺序回退，顶替原 {@code DocumentExtractionService} 的 switch 分派。
 *
 * <p>作为 {@link DocumentExtractionGateway} 的唯一 {@link Primary} 实现，应用层无需感知具体策略。路由栈优先级： 配置 {@code
 * knowledge.extractor.routing} &gt; 内置 MIME 映射 &gt; {@code default-stack}。逐个尝试栈内已启用且 {@code
 * supports} 命中的策略，遇 {@link IOException} 回退到下一策略；全部失败则冒泡最后一个异常，无命中则返回 {@link
 * ExtractionResult#empty()}。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Service
@Primary
@Slf4j
public class CompositeExtractionService implements DocumentExtractionGateway {

    /** 最大文件大小：200MB。 */
    private static final long MAX_FILE_SIZE = 200L * 1024 * 1024;

    /** 支持的文件扩展名。 */
    private static final List<String> SUPPORTED_EXTENSIONS =
            List.of(
                    "pdf", "docx", "doc", "xlsx", "xls", "pptx", "ppt", "txt", "csv", "md", "html",
                    "htm", "rtf");

    /** 未配置 routing 时的内置 MIME → 策略栈映射，与历史 switch 分支严格等价。 */
    private static final Map<String, List<String>> BUILTIN_ROUTING = buildBuiltinRouting();

    private final Map<String, ContentExtractor> extractorByName;
    private final ExtractorRoutingProperties routing;
    private final Tika tika = new Tika();

    public CompositeExtractionService(
            List<ContentExtractor> extractors, ExtractorRoutingProperties routing) {
        this.routing = routing;
        this.extractorByName =
                extractors.stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        ContentExtractor::name, Function.identity()));
    }

    @Override
    public void validateFile(Path filePath, long fileSize, String fileName) {
        if (filePath == null || !Files.exists(filePath)) {
            throw new IllegalArgumentException("File does not exist");
        }
        if (fileSize <= 0) {
            throw new IllegalArgumentException("File must not be empty");
        }
        if (fileSize > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    String.format(
                            "File size %d exceeds maximum %d bytes", fileSize, MAX_FILE_SIZE));
        }
        if (fileName != null) {
            String ext = ExtractionSupport.getExtension(fileName);
            if (!SUPPORTED_EXTENSIONS.contains(ext.toLowerCase())) {
                throw new IllegalArgumentException(
                        "Unsupported file type: " + ext + ". Supported: " + SUPPORTED_EXTENSIONS);
            }
        }
    }

    @Override
    public ExtractionResult extractText(Path filePath, long fileSize, String fileName)
            throws IOException {
        validateFile(filePath, fileSize, fileName);
        Path sanitized = filePath.toAbsolutePath().normalize();
        String mimeType = tika.detect(sanitized);
        log.info("Extracting text from: {} (type={}, size={})", fileName, mimeType, fileSize);

        ExtractionCandidate candidate =
                ExtractionCandidate.basic(sanitized, fileSize, fileName, mimeType);
        List<ContentExtractor> stack = resolveStack(mimeType);

        IOException lastError = null;
        for (ContentExtractor extractor : stack) {
            if (!extractor.supports(candidate)) {
                continue;
            }
            try {
                ExtractionResult result = extractor.extract(candidate);
                log.debug("extract ok [strategy={}, doc={}]", extractor.name(), fileName);
                return result;
            } catch (IOException e) {
                lastError = e;
                log.warn(
                        "extract failed, fallback [strategy={}, msg={}]",
                        extractor.name(),
                        e.getMessage());
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        return ExtractionResult.empty();
    }

    @Override
    public ExtractionResult extractFromPath(Path filePath) throws IOException {
        long fileSize = Files.size(filePath);
        String fileName = filePath.getFileName() != null ? filePath.getFileName().toString() : null;
        return extractText(filePath, fileSize, fileName);
    }

    @Override
    public String calculateChecksum(Path filePath) throws IOException {
        return ExtractionSupport.calculateChecksum(filePath);
    }

    /** 依据配置 / 内置映射解析 MIME 对应的有序策略栈，过滤掉未注册或未启用的策略名； 过滤后为空时回退到 {@code default-stack}。 */
    private List<ContentExtractor> resolveStack(String mimeType) {
        List<String> names = configuredStack(mimeType);
        List<ContentExtractor> resolved = filterEnabled(names);
        if (resolved.isEmpty()) {
            resolved = filterEnabled(routing.getDefaultStack());
        }
        return resolved;
    }

    private List<String> configuredStack(String mimeType) {
        Map<String, List<String>> configured = routing.getRouting();
        if (configured != null && configured.containsKey(mimeType)) {
            return configured.get(mimeType);
        }
        return BUILTIN_ROUTING.getOrDefault(mimeType, List.of());
    }

    private List<ContentExtractor> filterEnabled(List<String> names) {
        List<String> enabled = routing.getEnabledStrategies();
        List<ContentExtractor> result = new ArrayList<>();
        for (String name : names) {
            ContentExtractor extractor = extractorByName.get(name);
            if (extractor == null) {
                continue;
            }
            if (enabled != null && !enabled.isEmpty() && !enabled.contains(name)) {
                continue;
            }
            result.add(extractor);
        }
        return result;
    }

    private static Map<String, List<String>> buildBuiltinRouting() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("application/pdf", List.of(ExtractionStrategyEnum.PDF_BOX.name()));
        map.put(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                List.of(ExtractionStrategyEnum.DOCX.name()));
        map.put("application/msword", List.of(ExtractionStrategyEnum.DOCX.name()));
        map.put(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                List.of(ExtractionStrategyEnum.XLSX.name()));
        map.put("application/vnd.ms-excel", List.of(ExtractionStrategyEnum.XLSX.name()));
        map.put(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                List.of(ExtractionStrategyEnum.PPTX.name()));
        map.put("application/vnd.ms-powerpoint", List.of(ExtractionStrategyEnum.PPTX.name()));
        map.put("text/plain", List.of(ExtractionStrategyEnum.PLAIN_TEXT.name()));
        map.put("text/csv", List.of(ExtractionStrategyEnum.PLAIN_TEXT.name()));
        map.put("text/html", List.of(ExtractionStrategyEnum.PLAIN_TEXT.name()));
        map.put("text/markdown", List.of(ExtractionStrategyEnum.PLAIN_TEXT.name()));
        return Map.copyOf(map);
    }
}
