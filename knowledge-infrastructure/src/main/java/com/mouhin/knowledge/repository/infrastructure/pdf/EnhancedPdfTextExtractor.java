package com.mouhin.knowledge.repository.infrastructure.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
/**
 * 增强型 PDF 文本提取器
 * <p>
 * 功能特性：
 * <ul>
 *     <li>文本规范化：连字、特殊字符、空白字符处理</li>
 *     <li>扫描型 PDF 检测：基于文本密度分析</li>
 *     <li>加密检测：识别密码保护和权限限制</li>
 *     <li>页眉页脚过滤：基于位置和重复模式检测</li>
 *     <li>多栏排版检测：基于文本位置分析</li>
 *     <li>图像检测：统计每页图像数量</li>
 *     <li>元数据提取：作者、标题、创建时间等</li>
 * </ul>
 *
 * @author Knowledge-Repository
 * @date 2026-09-11
 */
public class EnhancedPdfTextExtractor {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedPdfTextExtractor.class);

    // ==================== 常量定义 ====================

    /** 扫描页检测：每页少于此字符数视为扫描页 */
    private static final int SCAN_PAGE_CHAR_THRESHOLD = 50;

    /** 扫描页检测：超过此比例的页面为扫描页则整体标记 */
    private static final double SCAN_DOCUMENT_RATIO = 0.3;

    /** 页眉页脚检测：出现在页面顶部/底部此比例区域内的文本 */
    private static final double HEADER_FOOTER_ZONE_RATIO = 0.15;

    /** 页眉页脚检测：至少在 N 页中重复出现才判定 */
    private static final int HEADER_FOOTER_MIN_OCCURRENCE = 3;

    /** 连字映射表 */
    private static final Map<Character, String> LIGATURE_MAP = createLigatureMap();

    private static Map<Character, String> createLigatureMap() {
        Map<Character, String> map = new HashMap<>();
        map.put('\uFB00', "ff");
        map.put('\uFB01', "fi");
        map.put('\uFB02', "fl");
        map.put('\uFB03', "ffi");
        map.put('\uFB04', "ffl");
        map.put('\uFB05', "ft");
        map.put('\uFB06', "st");
        map.put('\u00C6', "AE");
        map.put('\u00E6', "ae");
        map.put('\u0152', "OE");
        map.put('\u0153', "oe");
        map.put('\u00DF', "ss");
        return Collections.unmodifiableMap(map);
    }

    /** 空白字符规范化映射 */
    private static final Map<Character, Character> WHITESPACE_MAP = createWhitespaceMap();

    private static Map<Character, Character> createWhitespaceMap() {
        Map<Character, Character> map = new HashMap<>();
        map.put('\u00A0', ' ');  // 不换行空格
        map.put('\u2000', ' ');  // 恩格空格
        map.put('\u2001', ' ');  // 全角空格
        map.put('\u2002', ' ');  // 半角空格
        map.put('\u2003', ' ');  // 全角空格
        map.put('\u2004', ' ');  // 三分之一空格
        map.put('\u2005', ' ');  // 四分之一空格
        map.put('\u2006', ' ');  // 六分之一空格
        map.put('\u2007', ' ');  // 数字空格
        map.put('\u2008', ' ');  // 标点空格
        map.put('\u2009', ' ');  // 瘦空格
        map.put('\u200A', ' ');  // 超瘦空格
        map.put('\u202F', ' ');  // 窄不换行空格
        map.put('\u205F', ' ');  // 中等数学空格
        map.put('\u3000', ' ');  // 全角空格
        return Collections.unmodifiableMap(map);
    }

    /** 软连字符 */
    private static final char SOFT_HYPHEN = '\u00AD';

    /** 页码模式 */
    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile(
            "^\\s*(-?\\d+|-\\s*\\d+|\\d+\\s*/\\s*\\d+|第\\s*\\d+\\s*页|Page\\s+\\d+)\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    // ==================== 结果类定义 ====================

    /**
     * PDF 提取结果
     */
    public record PdfExtractionResult(
            List<PageContent> pages,
            PdfMetadata metadata,
            List<String> warnings,
            boolean encrypted,
            boolean ocrRecommended
    ) {
        public List<String> getAllTexts() {
            return pages.stream().map(PageContent::text).toList();
        }

        public int getTotalPages() {
            return pages.size();
        }
    }

    /**
     * 单页内容
     */
    public record PageContent(
            int pageNumber,
            String text,
            String filteredText,  // 过滤页眉页脚后的文本
            boolean likelyScanned,
            int imageCount,
            boolean hasMultiColumns,
            PageMetadata pageMetadata
    ) {}

    /**
     * PDF 文档元数据
     */
    public record PdfMetadata(
            String title,
            String author,
            String subject,
            String keywords,
            String creator,
            String producer,
            String creationDate,
            String modificationDate,
            String pdfVersion
    ) {}

    /**
     * 页面元数据
     */
    public record PageMetadata(
            float width,
            float height,
            int rotation
    ) {}

    // ==================== 公开 API ====================

    /**
     * 从 PDF 文件提取文本
     *
     * @param filePath PDF 文件路径
     * @return 提取结果
     */
    public PdfExtractionResult extract(java.nio.file.Path filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            return extractFromDocument(document);
        }
    }

    /**
     * 从 PDF 文件提取文本（支持密码）
     *
     * @param filePath PDF 文件路径
     * @param password 打开密码
     * @return 提取结果
     */
    public PdfExtractionResult extract(java.nio.file.Path filePath, String password) throws IOException {
        try (PDDocument document = Loader.loadPDF(filePath.toFile(), password)) {
            return extractFromDocument(document);
        }
    }

    // ==================== 核心提取逻辑 ====================

    private PdfExtractionResult extractFromDocument(PDDocument document) throws IOException {
        List<String> warnings = new ArrayList<>();
        int totalPages = document.getNumberOfPages();

        // 1. 检测加密状态
        boolean encrypted = document.isEncrypted();
        AccessPermission permissions = document.getCurrentAccessPermission();
        if (encrypted) {
            if (permissions != null) {
                if (!permissions.canExtractContent()) {
                    warnings.add("PDF 禁止提取文本内容");
                }
                if (!permissions.canPrint()) {
                    warnings.add("PDF 禁止打印");
                }
                if (!permissions.canModify()) {
                    warnings.add("PDF 禁止修改");
                }
            }
            logger.info("PDF is encrypted with restrictions: {}", warnings);
        }

        // 2. 提取元数据
        PdfMetadata metadata = extractMetadata(document);

        // 3. 逐页提取
        List<PageContent> pages = new ArrayList<>(totalPages);
        int scannedPages = 0;

        // 第一遍：提取原始文本和位置信息
        List<String> rawTexts = new ArrayList<>(totalPages);
        List<List<TextPosition>> pageTextPositions = new ArrayList<>(totalPages);

        for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
            PositionCapturingStripper stripper = new PositionCapturingStripper();
            stripper.setStartPage(pageNum);
            stripper.setEndPage(pageNum);
            String text = stripper.getText(document);
            rawTexts.add(text != null ? text : "");
            pageTextPositions.add(stripper.getCapturedPositions());
        }

        // 4. 检测页眉页脚（跨页分析）
        Set<String> headerFooterPatterns = detectHeaderFooter(rawTexts);
        if (!headerFooterPatterns.isEmpty()) {
            logger.info("Detected header/footer patterns: {}", headerFooterPatterns);
        }

        // 5. 处理每页内容
        for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
            String rawText = rawTexts.get(pageNum - 1);
            List<TextPosition> positions = pageTextPositions.get(pageNum - 1);

            // 文本规范化
            String normalizedText = normalizeText(rawText);

            // 页眉页脚过滤
            String filteredText = filterHeaderFooter(normalizedText, headerFooterPatterns);

            // 扫描页检测
            boolean likelyScanned = normalizedText.trim().length() < SCAN_PAGE_CHAR_THRESHOLD;
            if (likelyScanned) {
                scannedPages++;
            }

            // 图像检测
            int imageCount = countPageImages(document, pageNum);

            // 多栏检测
            boolean hasMultiColumns = detectMultiColumn(positions, document.getPage(pageNum - 1));

            // 页面元数据
            PageMetadata pageMetadata = extractPageMetadata(document.getPage(pageNum - 1));

            pages.add(new PageContent(
                    pageNum, normalizedText, filteredText, likelyScanned,
                    imageCount, hasMultiColumns, pageMetadata
            ));
        }

        // 6. 整体扫描检测
        boolean ocrRecommended = totalPages > 0
                && (double) scannedPages / totalPages > SCAN_DOCUMENT_RATIO;

        if (ocrRecommended) {
            warnings.add(String.format("PDF 疑似扫描件 (%d/%d 页无文本)，建议使用 OCR", scannedPages, totalPages));
        }

        // 7. 跨页段落合并（可选）
        // pages = mergeCrossPageParagraphs(pages);

        logger.info("PDF extraction completed: {} pages, {} scanned, {} warnings",
                totalPages, scannedPages, warnings.size());

        return new PdfExtractionResult(pages, metadata, warnings, encrypted, ocrRecommended);
    }

    // ==================== 文本规范化 ====================

    /**
     * 文本规范化处理
     */
    private String normalizeText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // 连字展开
            if (LIGATURE_MAP.containsKey(c)) {
                sb.append(LIGATURE_MAP.get(c));
                continue;
            }

            // 软连字符处理
            if (c == SOFT_HYPHEN) {
                continue;  // 跳过软连字符
            }

            // 空白字符规范化
            if (WHITESPACE_MAP.containsKey(c)) {
                sb.append(WHITESPACE_MAP.get(c));
                continue;
            }

            // 控制字符过滤（保留换行和制表符）
            if (c < 32 && c != '\n' && c != '\r' && c != '\t') {
                continue;
            }

            sb.append(c);
        }

        // 多余空白行合并
        return sb.toString()
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    // ==================== 页眉页脚检测 ====================

    /**
     * 检测页眉页脚模式
     */
    private Set<String> detectHeaderFooter(List<String> pageTexts) {
        Map<String, Integer> lineFrequency = new HashMap<>();

        for (String text : pageTexts) {
            if (text == null || text.isEmpty()) {
                continue;
            }

            String[] lines = text.split("\\n");
            if (lines.length < 3) {
                continue;
            }

            // 检查前 3 行和后 3 行
            for (int i = 0; i < Math.min(3, lines.length); i++) {
                String line = lines[i].trim();
                if (line.length() > 0 && line.length() < 100) {
                    lineFrequency.merge(line, 1, Integer::sum);
                }
            }

            for (int i = Math.max(0, lines.length - 3); i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.length() > 0 && line.length() < 100) {
                    lineFrequency.merge(line, 1, Integer::sum);
                }
            }
        }

        // 筛选高频出现的行
        Set<String> patterns = new HashSet<>();
        int threshold = Math.max(HEADER_FOOTER_MIN_OCCURRENCE, pageTexts.size() / 3);
        for (Map.Entry<String, Integer> entry : lineFrequency.entrySet()) {
            if (entry.getValue() >= threshold) {
                patterns.add(entry.getKey());
            }
        }

        return patterns;
    }

    /**
     * 过滤页眉页脚
     */
    private String filterHeaderFooter(String text, Set<String> patterns) {
        if (text == null || patterns.isEmpty()) {
            return text;
        }

        String[] lines = text.split("\\n");
        StringBuilder sb = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            // 跳过页眉页脚模式和纯页码
            if (patterns.contains(trimmed) || PAGE_NUMBER_PATTERN.matcher(trimmed).matches()) {
                continue;
            }
            sb.append(line).append("\n");
        }

        return sb.toString().trim();
    }

    // ==================== 图像检测 ====================

    /**
     * 统计页面图像数量
     */
    private int countPageImages(PDDocument document, int pageNum) throws IOException {
        PDPage page = document.getPage(pageNum - 1);
        PDResources resources = page.getResources();
        if (resources == null) {
            return 0;
        }

        int count = 0;
        for (COSName name : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(name);
            if (xObject instanceof PDImageXObject) {
                count++;
            }
        }
        return count;
    }

    // ==================== 多栏检测 ====================

    /**
     * 检测多栏排版
     */
    private boolean detectMultiColumn(List<TextPosition> positions, PDPage page) {
        if (positions == null || positions.isEmpty()) {
            return false;
        }

        PDRectangle mediaBox = page.getMediaBox();
        float pageWidth = mediaBox.getWidth();
        float pageCenter = pageWidth / 2;
        float gapThreshold = pageWidth * 0.05f;  // 5% 页面宽度作为中缝阈值

        // 统计中缝附近的文本位置
        int leftCount = 0;
        int rightCount = 0;
        int centerGap = 0;

        for (TextPosition pos : positions) {
            float x = pos.getXDirAdj();
            if (x < pageCenter - gapThreshold) {
                leftCount++;
            } else if (x > pageCenter + gapThreshold) {
                rightCount++;
            } else {
                centerGap++;
            }
        }

        // 如果左右两侧都有大量文本，且中缝较少，可能是多栏
        int total = positions.size();
        return total > 20
                && leftCount > total * 0.3
                && rightCount > total * 0.3
                && centerGap < total * 0.1;
    }

    // ==================== 元数据提取 ====================

    /**
     * 提取文档元数据
     */
    private PdfMetadata extractMetadata(PDDocument document) {
        PDDocumentInformation info = document.getDocumentInformation();
        return new PdfMetadata(
                info.getTitle(),
                info.getAuthor(),
                info.getSubject(),
                info.getKeywords(),
                info.getCreator(),
                info.getProducer(),
                info.getCreationDate() != null ? info.getCreationDate().getTime().toString() : null,
                info.getModificationDate() != null ? info.getModificationDate().getTime().toString() : null,
                String.format("%.1f", document.getVersion())
        );
    }

    /**
     * 提取页面元数据
     */
    private PageMetadata extractPageMetadata(PDPage page) {
        PDRectangle mediaBox = page.getMediaBox();
        return new PageMetadata(
                mediaBox.getWidth(),
                mediaBox.getHeight(),
                page.getRotation()
        );
    }

    // ==================== 跨页段落合并 ====================

    /**
     * 合并跨页段落（实验性）
     */
    @SuppressWarnings("unused")
    private List<PageContent> mergeCrossPageParagraphs(List<PageContent> pages) {
        if (pages.size() < 2) {
            return pages;
        }

        List<PageContent> merged = new ArrayList<>();
        StringBuilder carryOver = new StringBuilder();

        for (int i = 0; i < pages.size(); i++) {
            PageContent page = pages.get(i);
            String text = page.filteredText();

            if (carryOver.length() > 0) {
                text = carryOver.toString() + text;
                carryOver.setLength(0);
            }

            // 检查页面末尾是否是未完成的段落（不以标点结尾）
            if (!text.isEmpty() && !endsWithPunctuation(text)) {
                // 检查下一页是否以相同方式继续
                if (i < pages.size() - 1) {
                    String nextText = pages.get(i + 1).filteredText();
                    if (!nextText.isEmpty() && !Character.isUpperCase(nextText.charAt(0))) {
                        // 可能是跨页段落，暂存
                        carryOver.append(text);
                        continue;
                    }
                }
            }

            merged.add(new PageContent(
                    page.pageNumber(), text, text,
                    page.likelyScanned(), page.imageCount(),
                    page.hasMultiColumns(), page.pageMetadata()
            ));
        }

        // 处理最后一页的 carryOver
        if (carryOver.length() > 0 && !merged.isEmpty()) {
            PageContent last = merged.get(merged.size() - 1);
            merged.set(merged.size() - 1, new PageContent(
                    last.pageNumber(), last.text() + carryOver,
                    last.filteredText() + carryOver,
                    last.likelyScanned(), last.imageCount(),
                    last.hasMultiColumns(), last.pageMetadata()
            ));
        }

        return merged;
    }

    private boolean endsWithPunctuation(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        char last = text.charAt(text.length() - 1);
        return ".。!！?？;；:：,\"'）)】」』\"".indexOf(last) >= 0;
    }

    // ==================== 内部类：位置捕获文本提取器 ====================

    /**
     * 自定义文本提取器，捕获文本位置信息
     */
    private static class PositionCapturingStripper extends PDFTextStripper {
        private final List<TextPosition> capturedPositions = new ArrayList<>();

        public PositionCapturingStripper() throws IOException {
            super();
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            capturedPositions.addAll(textPositions);
            super.writeString(text, textPositions);
        }

        public List<TextPosition> getCapturedPositions() {
            return capturedPositions;
        }
    }
}
