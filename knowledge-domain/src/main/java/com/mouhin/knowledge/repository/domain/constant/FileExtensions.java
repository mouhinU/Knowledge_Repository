package com.mouhin.knowledge.repository.domain.constant;

/**
 * 文件扩展名常量（含前导点）。
 *
 * <p>文档 MIME 嗅探（{@code DocumentIngestionSupport#guessMimeType}）、图片扩展名回写（{@code
 * DocumentImageSupport#extensionForMime}）、静态资源缓存控制（{@code HtmlCacheBusterFilter}）等处统一在此维护扩展名
 * 字面量，禁止硬编码（AGENTS.md 红线 #2）。
 */
public final class FileExtensions {

    private FileExtensions() {}

    // ── 文档 ──────────────────────────────────────────────

    /** PDF */
    public static final String PDF = ".pdf";

    /** Word OOXML */
    public static final String DOCX = ".docx";

    /** Word 二进制 */
    public static final String DOC = ".doc";

    /** Excel OOXML */
    public static final String XLSX = ".xlsx";

    /** Excel 二进制 */
    public static final String XLS = ".xls";

    /** PowerPoint OOXML */
    public static final String PPTX = ".pptx";

    /** PowerPoint 二进制 */
    public static final String PPT = ".ppt";

    /** 纯文本 */
    public static final String TXT = ".txt";

    /** CSV */
    public static final String CSV = ".csv";

    /** HTML */
    public static final String HTML = ".html";

    /** HTML 短别名 */
    public static final String HTM = ".htm";

    /** Markdown */
    public static final String MD = ".md";

    // ── 图片 ──────────────────────────────────────────────

    /** PNG */
    public static final String PNG = ".png";

    /** JPEG（规范） */
    public static final String JPG = ".jpg";

    /** JPEG（别名） */
    public static final String JPEG = ".jpeg";

    /** GIF */
    public static final String GIF = ".gif";

    /** BMP */
    public static final String BMP = ".bmp";

    /** TIFF（短别名） */
    public static final String TIF = ".tif";

    /** TIFF（规范） */
    public static final String TIFF = ".tiff";

    /** WebP */
    public static final String WEBP = ".webp";

    // ── 无点前缀 token（PDFBox/MIME 嗅探返回值 switch-case 使用） ──

    /** PDF token */
    public static final String PDF_TOKEN = "pdf";

    /** PNG token */
    public static final String PNG_TOKEN = "png";

    /** JPG token */
    public static final String JPG_TOKEN = "jpg";

    /** JPEG token */
    public static final String JPEG_TOKEN = "jpeg";

    /** GIF token */
    public static final String GIF_TOKEN = "gif";

    /** BMP token */
    public static final String BMP_TOKEN = "bmp";

    /** TIF token */
    public static final String TIF_TOKEN = "tif";

    /** TIFF token */
    public static final String TIFF_TOKEN = "tiff";

    /** WEBP token */
    public static final String WEBP_TOKEN = "webp";
}
