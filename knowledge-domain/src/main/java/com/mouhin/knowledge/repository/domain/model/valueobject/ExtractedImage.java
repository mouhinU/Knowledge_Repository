package com.mouhin.knowledge.repository.domain.model.valueobject;

import java.util.Arrays;
import java.util.Objects;

/**
 * 文档内嵌图片提取项（值对象）。
 *
 * <p>{@code DocumentImageExtractorGateway} 的返回契约：从原始文件中抽取到的单张位图，携带二进制与 尺寸、来源页/序号等元信息，交由应用层落盘并持久化为
 * {@code DocumentImage} 实体。字节数组 （{@code byte[]}）是纯数据而非基础设施技术，故领域层可安全持有，符合依赖倒置。
 *
 * <p>java:S6218：record 默认 equals/hashCode/toString 对数组字段按引用比较，会把内容相同的两张图误判为不等；此处显式覆写为按 Arrays 语义 比较
 * {@code bytes}， 且 toString 只报字节长度避免原始数据泄漏到日志。
 *
 * @param bytes 图片二进制内容
 * @param mimeType MIME 类型（如 image/png、image/jpeg）
 * @param width 像素宽
 * @param height 像素高
 * @param pageOrSeq 来源页码 / 工作表序号 / 幻灯片序号（1 起始），无法定位时可为 null
 * @param seqOnPage 同一页内的图片序号（0 起始），用于稳定排序
 * @author mouhinU
 * @date 2026-09-20
 */
public record ExtractedImage(
        byte[] bytes,
        String mimeType,
        Integer width,
        Integer height,
        Integer pageOrSeq,
        int seqOnPage) {

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExtractedImage other)) {
            return false;
        }
        return seqOnPage == other.seqOnPage
                && Arrays.equals(bytes, other.bytes)
                && Objects.equals(mimeType, other.mimeType)
                && Objects.equals(width, other.width)
                && Objects.equals(height, other.height)
                && Objects.equals(pageOrSeq, other.pageOrSeq);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mimeType, width, height, pageOrSeq, seqOnPage);
        return 31 * result + Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "ExtractedImage[bytes.length="
                + (bytes == null ? 0 : bytes.length)
                + ", mimeType="
                + mimeType
                + ", width="
                + width
                + ", height="
                + height
                + ", pageOrSeq="
                + pageOrSeq
                + ", seqOnPage="
                + seqOnPage
                + "]";
    }
}
