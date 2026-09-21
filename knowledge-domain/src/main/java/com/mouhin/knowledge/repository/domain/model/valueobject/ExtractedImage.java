package com.mouhin.knowledge.repository.domain.model.valueobject;

/**
 * 文档内嵌图片提取项（值对象）。
 *
 * <p>{@code DocumentImageExtractorGateway} 的返回契约：从原始文件中抽取到的单张位图，携带二进制与 尺寸、来源页/序号等元信息，交由应用层落盘并持久化为
 * {@code DocumentImage} 实体。字节数组 （{@code byte[]}）是纯数据而非基础设施技术，故领域层可安全持有，符合依赖倒置。
 *
 * @param bytes 图片二进制内容
 * @param mimeType MIME 类型（如 image/png、image/jpeg）
 * @param width 像素宽
 * @param height 像素高
 * @param pageOrSeq 来源页码 / 工作表序号 / 幻灯片序号（1 起始），无法定位时可为 null
 * @param seqOnPage 同一页内的图片序号（0 起始），用于稳定排序
 * @author Knowledge-Repository
 * @date 2026-09-20
 */
public record ExtractedImage(
        byte[] bytes,
        String mimeType,
        Integer width,
        Integer height,
        Integer pageOrSeq,
        int seqOnPage) {}
