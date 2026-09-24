package com.mouhin.knowledge.repository.infrastructure.extractor;

import com.mouhin.knowledge.repository.domain.gateway.ContentExtractor;
import com.mouhin.knowledge.repository.domain.gateway.DocumentExtractionGateway;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionCandidate;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionStrategyEnum;
import com.mouhin.knowledge.repository.infrastructure.config.ExtractorRoutingProperties;
import com.mouhin.knowledge.repository.infrastructure.extraction.ExtractionSupport;
import com.mouhin.knowledge.repository.infrastructure.observability.ExtractionMetrics;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    /** 校验失败对外统一回显消息，消除文件是否存在 / 大小是否越界 / 扩展名是否受支持等 oracle（java:S6549）。 */
    private static final String GENERIC_INVALID_REFERENCE = "Invalid file reference";

    /** 支持的文件扩展名。 */
    private static final List<String> SUPPORTED_EXTENSIONS =
            List.of(
                    "pdf", "docx", "doc", "xlsx", "xls", "pptx", "ppt", "txt", "csv", "md", "html",
                    "htm", "rtf");

    /** 未配置 routing 时的内置 MIME → 策略栈映射，与历史 switch 分支严格等价。 */
    private static final Map<String, List<String>> BUILTIN_ROUTING = buildBuiltinRouting();

    private final Map<String, ContentExtractor> extractorByName;
    private final ExtractorRoutingProperties routing;
    private final ExtractionMetrics metrics;

    /**
     * 允许读取的根目录白名单（已 normalize 的绝对路径）。空集合表示不启用校验，仅供测试构造器使用； 生产路径始终由 {@code knowledge.storage.path} +
     * {@code java.io.tmpdir} 两项构成，二者皆系统可控常量。
     */
    private final List<Path> allowedRoots;

    private final Tika tika = new Tika();

    /** 测试专用构造器：跳过 base-dir 白名单，只校验路由与策略。生产 Spring 装配请使用 4 参构造。 */
    public CompositeExtractionService(
            List<ContentExtractor> extractors, ExtractorRoutingProperties routing) {
        this(extractors, routing, null, null);
    }

    /** 测试专用构造器：跳过 base-dir 白名单。 */
    public CompositeExtractionService(
            List<ContentExtractor> extractors,
            ExtractorRoutingProperties routing,
            ExtractionMetrics metrics) {
        this(extractors, routing, metrics, null);
    }

    /**
     * 生产装配构造器：注入 {@code knowledge.storage.path} 与 JVM 临时目录构成 base-dir 白名单，配合统一异常消息 满足
     * java:S6549「Filesystem Oracle」整改要求。
     *
     * @param extractors 所有已注册的抽取策略实现，Spring 自动收集
     * @param routing 路由与启用白名单配置
     * @param metrics 观测指标（可空）
     * @param storageDir 文档永久存储根目录；空串或 {@code null} 时 base-dir 校验关闭（仅测试路径）
     */
    @Autowired
    public CompositeExtractionService(
            List<ContentExtractor> extractors,
            ExtractorRoutingProperties routing,
            ExtractionMetrics metrics,
            @Value("${knowledge.storage.path:./data/documents}") String storageDir) {
        this.routing = routing;
        this.metrics = metrics;
        this.allowedRoots = resolveAllowedRoots(storageDir);
        this.extractorByName =
                extractors.stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        ContentExtractor::name, Function.identity()));
    }

    /**
     * 上传/索引前的最小防线：
     *
     * <ol>
     *   <li>base-dir 白名单（{@link Path#startsWith} 匹配 {@code knowledge.storage.path} 与 {@code
     *       java.io.tmpdir}），阻断对文件系统任意路径的探测；
     *   <li>{@link Files#isRegularFile} 存在性判定；
     *   <li>文件大小与扩展名。
     * </ol>
     *
     * <p>四类失败对外抛出同一 {@link IllegalArgumentException}（{@value #GENERIC_INVALID_REFERENCE}），仅在 {@code
     * log.debug} 保留真实原因供运维诊断——彻底消除 java:S6549 所指的 filesystem oracle（响应消息可区分 → 攻击者可枚举路径 / 大小 / 类型）。
     *
     * <p>{@code @SuppressWarnings("java:S6549")} 说明：Sonar 的污点传播引擎把 {@code filePath} 参数视为用户可控，
     * 无法识别运行时的 {@code startsWith(allowedRoots)} 白名单作为 sanitizer，因此在 {@link Files#isRegularFile}
     * 处仍会命中该规则。业务上此路径来源已限定为 app 层传入的 {@code Document.storagePath}（写库前经 {@code
     * DocumentIngestionSupport.saveToTemp} 归一到 {@code knowledge.storage.path} 之下）， 再叠加本方法内的
     * base-dir 白名单与统一异常消息，oracle 面已被完全封堵——属于<b>有据可依的抑制</b> 而非未修复项，与 {@code
     * docs/sonar-remediation-plan.md} 中 java:S6549 整改方案对齐。
     *
     * @param filePath 待校验路径；null 亦走统一异常
     * @param fileSize 客户端上送的字节数（仅做上下界，不做 stat 二次确认）
     * @param fileName 原始文件名，用于扩展名白名单；null 时跳过扩展名校验
     * @throws IllegalArgumentException 任一校验失败（对外消息恒定）
     */
    @Override
    @SuppressWarnings("java:S6549") // base-dir 白名单 + 统一消息双封堵，Sonar 污点传播不识别 startsWith sanitizer
    public void validateFile(Path filePath, long fileSize, String fileName) {
        if (filePath == null) {
            reject("null path", null);
        }
        Path normalized = filePath.toAbsolutePath().normalize();
        if (!allowedRoots.isEmpty() && allowedRoots.stream().noneMatch(normalized::startsWith)) {
            reject("path outside allowed roots: " + normalized, null);
        }
        if (!Files.isRegularFile(normalized)) {
            reject("not a regular file: " + normalized, null);
        }
        if (fileSize <= 0) {
            reject("non-positive size: " + fileSize, null);
        }
        if (fileSize > MAX_FILE_SIZE) {
            reject("size " + fileSize + " exceeds max " + MAX_FILE_SIZE + " bytes", null);
        }
        if (fileName != null) {
            String ext = ExtractionSupport.getExtension(fileName);
            if (!SUPPORTED_EXTENSIONS.contains(ext.toLowerCase())) {
                reject("unsupported extension: " + ext, null);
            }
        }
    }

    /** 记录 debug 细节后抛出统一异常；私有工具，保证 validateFile 各分支的对外信号完全同构。 */
    private void reject(String reason, Throwable cause) {
        if (log.isDebugEnabled()) {
            log.debug("validateFile rejected [{}]", reason, cause);
        }
        throw new IllegalArgumentException(GENERIC_INVALID_REFERENCE);
    }

    /**
     * 由 {@code knowledge.storage.path} 与 {@code java.io.tmpdir} 归一化生成 base-dir 白名单；storageDir 空 →
     * 关闭。
     */
    private static List<Path> resolveAllowedRoots(String storageDir) {
        if (storageDir == null || storageDir.isBlank()) {
            return List.of();
        }
        Path storageRoot = Path.of(storageDir).toAbsolutePath().normalize();
        Path tmpRoot = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        return List.of(storageRoot, tmpRoot);
    }

    @Override
    public ExtractionResult extractText(Path filePath, long fileSize, String fileName)
            throws IOException {
        return extractText(filePath, fileSize, fileName, List.of());
    }

    @Override
    public ExtractionResult extractText(
            Path filePath, long fileSize, String fileName, List<String> forcedStrategyStack)
            throws IOException {
        validateFile(filePath, fileSize, fileName);
        Path sanitized = filePath.toAbsolutePath().normalize();
        String mimeType = tika.detect(sanitized);
        log.info("Extracting text from: {} (type={}, size={})", fileName, mimeType, fileSize);

        ExtractionCandidate candidate =
                ExtractionCandidate.basic(sanitized, fileSize, fileName, mimeType);
        List<ContentExtractor> stack =
                (forcedStrategyStack == null || forcedStrategyStack.isEmpty())
                        ? resolveStack(mimeType)
                        : resolveStack(forcedStrategyStack);
        return runStack(stack, candidate, mimeType);
    }

    /** 顺序尝试栈内命中策略：成功记 ok+耗时并返回；IO 异常记 fallback 并继续；全失败冒泡最后异常。 */
    private ExtractionResult runStack(
            List<ContentExtractor> stack, ExtractionCandidate candidate, String mimeType)
            throws IOException {
        IOException lastError = null;
        for (ContentExtractor extractor : stack) {
            if (!extractor.supports(candidate)) {
                continue;
            }
            long start = System.nanoTime();
            try {
                ExtractionResult result = extractor.extract(candidate);
                recordInvocation(extractor.name(), mimeType, "ok");
                recordLatency(extractor.name(), Duration.ofNanos(System.nanoTime() - start));
                log.debug(
                        "extract ok [strategy={}, doc={}]", extractor.name(), candidate.fileName());
                return result;
            } catch (IOException e) {
                lastError = e;
                recordInvocation(extractor.name(), mimeType, "fallback");
                log.warn(
                        "extract failed, fallback [strategy={}, msg={}]",
                        extractor.name(),
                        e.getMessage());
            }
        }
        if (lastError != null) {
            recordInvocation("none", mimeType, "error");
            throw lastError;
        }
        recordInvocation("none", mimeType, "empty");
        return ExtractionResult.empty();
    }

    private void recordInvocation(String strategy, String mime, String outcome) {
        if (metrics != null) {
            metrics.recordInvocation(strategy, mime, outcome);
        }
    }

    private void recordLatency(String strategy, Duration duration) {
        if (metrics != null) {
            metrics.recordLatency(strategy, duration);
        }
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

    /** 解析按请求强制指定的策略名栈（名称已在上游按白名单校验）；全部未启用/未注册时回退默认栈。 */
    private List<ContentExtractor> resolveStack(List<String> forcedNames) {
        List<ContentExtractor> resolved = filterEnabled(forcedNames);
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
