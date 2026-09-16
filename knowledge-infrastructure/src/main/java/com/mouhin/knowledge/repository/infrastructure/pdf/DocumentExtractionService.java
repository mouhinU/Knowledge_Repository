package com.mouhin.knowledge.repository.infrastructure.pdf;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * 多格式文档提取服务
 * <p>
 * 支持的文件格式：
 * <ul>
 *     <li>PDF (.pdf) — PDFBox 按页提取</li>
 *     <li>Word (.docx) — Apache POI 按段落提取</li>
 *     <li>Excel (.xlsx) — Apache POI 按工作表提取</li>
 *     <li>PowerPoint (.pptx) — Apache POI 按幻灯片提取</li>
 *     <li>其他格式 — Apache Tika 通用解析（TXT/HTML/CSV/RTF 等）</li>
 * </ul>
 * <p>
 * 每种格式都尽量保留结构边界（页/幻灯片/工作表），以便下游分块时保持语义完整性。
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@Service
public class DocumentExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentExtractionService.class);

    /**
     * 最大文件大小：200MB
     */
    private static final long MAX_FILE_SIZE = 200L * 1024 * 1024;

    /**
     * 扫描型 PDF 检测阈值：每页少于 50 字符视为扫描页
     */
    private static final int SCAN_PAGE_CHAR_THRESHOLD = 50;

    /**
     * 扫描型 PDF 检测：超过 30% 的页面为扫描页则整体标记
     */
    private static final double SCAN_DOCUMENT_RATIO = 0.3;

    /**
     * 支持的文件扩展名
     */
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(
            "pdf", "docx", "doc", "xlsx", "xls", "pptx", "ppt",
            "txt", "csv", "md", "html", "htm", "rtf"
    );

    private final Tika tika = new Tika();

    /**
     * 校验文件
     */
    public void validateFile(Path filePath, long fileSize, String fileName) {
        if (filePath == null || !Files.exists(filePath)) {
            throw new IllegalArgumentException("File does not exist");
        }
        if (fileSize <= 0) {
            throw new IllegalArgumentException("File must not be empty");
        }
        if (fileSize > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    String.format("File size %d exceeds maximum %d bytes", fileSize, MAX_FILE_SIZE));
        }
        if (fileName != null) {
            String ext = getExtension(fileName);
            if (!SUPPORTED_EXTENSIONS.contains(ext.toLowerCase())) {
                throw new IllegalArgumentException(
                        "Unsupported file type: " + ext + ". Supported: " + SUPPORTED_EXTENSIONS);
            }
        }
    }

    // ==================== 公开 API ====================

    /**
     * 从文件中提取文本（按页/结构单元拆分）
     *
     * @param filePath 文件路径
     * @param fileSize 文件大小
     * @param fileName 文件名
     * @return 提取结果
     */
    public ExtractionResult extractText(Path filePath, long fileSize, String fileName) throws IOException {
        validateFile(filePath, fileSize, fileName);
        String checksum = calculateChecksum(filePath);
        String mimeType = tika.detect(filePath);

        logger.info("Extracting text from: {} (type={}, size={})", fileName, mimeType, fileSize);

        return switch (mimeType) {
            case "application/pdf" -> extractPdf(filePath);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                 "application/msword" -> extractWord(filePath);
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                 "application/vnd.ms-excel" -> extractExcel(filePath);
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                 "application/vnd.ms-powerpoint" -> extractPowerPoint(filePath);
            case "text/plain", "text/csv", "text/html", "text/markdown" -> extractPlainText(filePath, mimeType);
            default -> extractGeneric(filePath, mimeType);
        };
    }

    /**
     * 从文件路径提取（简化版）
     */
    public ExtractionResult extractFromPath(Path filePath) throws IOException {
        long fileSize = Files.size(filePath);
        String fileName = filePath.getFileName() != null ? filePath.getFileName().toString() : null;
        return extractText(filePath, fileSize, fileName);
    }

    private ExtractionResult extractPdf(Path filePath) throws IOException {
        EnhancedPdfTextExtractor extractor = new EnhancedPdfTextExtractor();
        EnhancedPdfTextExtractor.PdfExtractionResult result = extractor.extract(filePath);

        // 使用过滤后的文本（已去除页眉页脚）
        List<String> pageTexts = result.pages().stream()
                .map(EnhancedPdfTextExtractor.PageContent::filteredText)
                .toList();

        // 记录警告
        for (String warning : result.warnings()) {
            logger.warn("PDF extraction warning: {}", warning);
        }

        logger.info("PDF extracted (enhanced): {} pages, encrypted={}, ocrRecommended={}, warnings={}",
                result.getTotalPages(), result.encrypted(), result.ocrRecommended(), result.warnings().size());

        return new ExtractionResult(
                pageTexts,
                result.getTotalPages(),
                result.ocrRecommended(),
                calculateChecksum(filePath),
                "pdf",
                result.warnings(),
                result.encrypted(),
                result.metadata().title(),
                result.metadata().author()
        );
    }

    // ==================== PDF 提取 ====================

    private ExtractionResult extractWord(Path filePath) throws IOException {
        try (InputStream is = Files.newInputStream(filePath);
             XWPFDocument document = new XWPFDocument(is)) {

            List<XWPFParagraph> paragraphs = document.getParagraphs();
            List<String> sections = new ArrayList<>();
            StringBuilder currentSection = new StringBuilder();
            int sectionBreakCount = 0;

            for (XWPFParagraph para : paragraphs) {
                String text = para.getText();
                if (text == null || text.isBlank()) {
                    // 空段落作为段落分隔
                    if (currentSection.length() > 0) {
                        currentSection.append("\n");
                    }
                    continue;
                }

                currentSection.append(text.trim()).append("\n");

                // 每 30 个段落切一个section（近似页面）
                if (currentSection.toString().split("\n").length > 30) {
                    sections.add(currentSection.toString().trim());
                    currentSection = new StringBuilder();
                    sectionBreakCount++;
                }
            }

            if (currentSection.length() > 0) {
                sections.add(currentSection.toString().trim());
            }

            if (sections.isEmpty()) {
                sections.add("");
            }

            logger.info("Word document extracted: {} paragraphs, {} sections",
                    paragraphs.size(), sections.size());
            return new ExtractionResult(sections, sections.size(), false, calculateChecksum(filePath), "docx");
        }
    }

    // ==================== Word 提取 ====================

    private ExtractionResult extractExcel(Path filePath) throws IOException {
        try (InputStream is = Files.newInputStream(filePath);
             XSSFWorkbook workbook = new XSSFWorkbook(is)) {

            List<String> sheetTexts = new ArrayList<>();
            int totalSheets = workbook.getNumberOfSheets();

            for (int sheetIdx = 0; sheetIdx < totalSheets; sheetIdx++) {
                XSSFSheet sheet = workbook.getSheetAt(sheetIdx);
                StringBuilder sheetContent = new StringBuilder();
                sheetContent.append("[Sheet: ").append(sheet.getSheetName()).append("]\n");

                for (int rowIdx = 0; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                    XSSFRow row = sheet.getRow(rowIdx);
                    if (row == null) {
                        continue;
                    }

                    List<String> cells = new ArrayList<>();
                    for (int colIdx = 0; colIdx < row.getLastCellNum(); colIdx++) {
                        XSSFCell cell = row.getCell(colIdx);
                        cells.add(cell != null ? getCellValueAsString(cell) : "");
                    }

                    String rowText = String.join("\t", cells);
                    if (!rowText.isBlank()) {
                        sheetContent.append(rowText).append("\n");
                    }
                }

                sheetTexts.add(sheetContent.toString().trim());
            }

            if (sheetTexts.isEmpty()) {
                sheetTexts.add("");
            }

            logger.info("Excel extracted: {} sheets", totalSheets);
            return new ExtractionResult(sheetTexts, totalSheets, false, calculateChecksum(filePath), "xlsx");
        }
    }

    // ==================== Excel 提取 ====================

    private ExtractionResult extractPowerPoint(Path filePath) throws IOException {
        try (InputStream is = Files.newInputStream(filePath);
             XMLSlideShow slideShow = new XMLSlideShow(is)) {

            List<XSLFSlide> slides = slideShow.getSlides();
            List<String> slideTexts = new ArrayList<>(slides.size());

            for (int i = 0; i < slides.size(); i++) {
                XSLFSlide slide = slides.get(i);
                StringBuilder slideContent = new StringBuilder();
                slideContent.append("[Slide ").append(i + 1).append("]\n");

                slide.getShapes().forEach(shape -> {
                    if (shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            slideContent.append(text.trim()).append("\n");
                        }
                    }
                });

                slideTexts.add(slideContent.toString().trim());
            }

            if (slideTexts.isEmpty()) {
                slideTexts.add("");
            }

            logger.info("PowerPoint extracted: {} slides", slides.size());
            return new ExtractionResult(slideTexts, slides.size(), false, calculateChecksum(filePath), "pptx");
        }
    }

    // ==================== PowerPoint 提取 ====================

    private ExtractionResult extractPlainText(Path filePath, String mimeType) throws IOException {
        String fullText = Files.readString(filePath).trim();

        List<String> sections = new ArrayList<>();
        if (!fullText.isEmpty()) {
            String[] paragraphs = fullText.split("\\n\\s*\\n");
            for (String para : paragraphs) {
                String trimmed = para.trim();
                if (!trimmed.isEmpty()) {
                    sections.add(trimmed);
                }
            }
        }

        if (sections.isEmpty()) {
            sections.add(fullText.isEmpty() ? "" : fullText);
        }

        logger.info("Plain text extraction ({}): {} sections, {} chars", mimeType, sections.size(), fullText.length());
        return new ExtractionResult(sections, sections.size(), false, calculateChecksum(filePath), "text");
    }

    // ==================== 纯文本格式提取 ====================

    private ExtractionResult extractGeneric(Path filePath, String mimeType) throws IOException {
        try (InputStream is = Files.newInputStream(filePath)) {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1); // -1 = unlimited
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filePath.getFileName().toString());

            parser.parse(is, handler, metadata);
            String fullText = handler.toString().trim();

            // 按段落分割
            List<String> sections = new ArrayList<>();
            if (!fullText.isEmpty()) {
                String[] paragraphs = fullText.split("\\n\\s*\\n");
                for (String para : paragraphs) {
                    String trimmed = para.trim();
                    if (!trimmed.isEmpty()) {
                        sections.add(trimmed);
                    }
                }
            }

            if (sections.isEmpty()) {
                sections.add(fullText.isEmpty() ? "" : fullText);
            }

            logger.info("Generic extraction ({}): {} sections, {} chars", mimeType, sections.size(), fullText.length());
            return new ExtractionResult(sections, sections.size(), false, calculateChecksum(filePath), "generic");
        } catch (Exception e) {
            throw new IOException("Failed to parse file with Tika: " + e.getMessage(), e);
        }
    }

    // ==================== 通用格式提取（Tika） ====================

    private String getCellValueAsString(XSSFCell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                yield val == Math.floor(val) ? String.valueOf((long) val) : String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            case BLANK -> "";
            default -> "";
        };
    }

    // ==================== 工具方法 ====================

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 ? fileName.substring(dotIndex + 1) : "";
    }

    /**
     * 计算文件 MD5 校验和
     */
    public String calculateChecksum(Path filePath) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] digest = md.digest(fileBytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("MD5 algorithm not available", e);
        }
    }

    /**
     * 文档提取结果
     */
    public record ExtractionResult(
            List<String> pageTexts,
            int totalPages,
            boolean likelyScanned,
            String checksum,
            String detectedFormat,
            List<String> warnings,
            boolean encrypted,
            String title,
            String author
    ) {
        // 向后兼容的简化构造器
        public ExtractionResult(List<String> pageTexts, int totalPages, boolean likelyScanned,
                                String checksum, String detectedFormat) {
            this(pageTexts, totalPages, likelyScanned, checksum, detectedFormat,
                    List.of(), false, null, null);
        }
    }
}
