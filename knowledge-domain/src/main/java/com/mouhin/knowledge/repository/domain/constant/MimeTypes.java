package com.mouhin.knowledge.repository.domain.constant;

/**
 * MIME 类型常量。
 *
 * <p>文档解析路由（{@code CompositeExtractionService} 与各 {@code *ExtractionStrategy}）、图片抽取 （{@code
 * DocumentImageExtractorService}）、上传校验（{@code DocumentUploadController}）等处统一在此维护 MIME
 * 字面量，禁止硬编码（AGENTS.md 红线 #2）。此前同一批 MIME 串在 8+ 文件重复约 30 处。
 */
public final class MimeTypes {

    private MimeTypes() {}

    // ── 文档 ──────────────────────────────────────────────

    /** PDF */
    public static final String PDF = "application/pdf";

    /** Word（.docx，OOXML） */
    public static final String DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /** Word（.doc，二进制） */
    public static final String DOC = "application/msword";

    /** Excel（.xlsx，OOXML） */
    public static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** Excel（.xls，二进制） */
    public static final String XLS = "application/vnd.ms-excel";

    /** PowerPoint（.pptx，OOXML） */
    public static final String PPTX =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    /** PowerPoint（.ppt，二进制） */
    public static final String PPT = "application/vnd.ms-powerpoint";

    /** 纯文本 */
    public static final String TEXT_PLAIN = "text/plain";

    /** CSV */
    public static final String TEXT_CSV = "text/csv";

    /** HTML */
    public static final String TEXT_HTML = "text/html";

    /** Markdown */
    public static final String TEXT_MARKDOWN = "text/markdown";

    /** JSON（LLM OpenAI 兼容协议 body 的 Content-Type） */
    public static final String APPLICATION_JSON = "application/json";

    /** SSE 事件流（流式对话 Accept 头） */
    public static final String TEXT_EVENT_STREAM = "text/event-stream";

    /** 未知/二进制流 */
    public static final String OCTET_STREAM = "application/octet-stream";

    // ── 图片 ──────────────────────────────────────────────

    /** {@code image/*} 通用前缀（用于 {@code startsWith("image/")} 类型粗判） */
    public static final String IMAGE_PREFIX = "image/";

    /** PNG */
    public static final String IMAGE_PNG = "image/png";

    /** JPEG（规范值） */
    public static final String IMAGE_JPEG = "image/jpeg";

    /** JPEG（非规范别名） */
    public static final String IMAGE_JPG = "image/jpg";

    /** GIF */
    public static final String IMAGE_GIF = "image/gif";

    /** BMP */
    public static final String IMAGE_BMP = "image/bmp";

    /** TIFF */
    public static final String IMAGE_TIFF = "image/tiff";

    /** WebP */
    public static final String IMAGE_WEBP = "image/webp";
}
