package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 文档配图展示对象（client 层对外契约）。
 *
 * <p>供管理端图片检索 / 选图界面渲染缩略图与元信息；{@code url} 为浏览器直接可取的公开句柄路径。 仅暴露展示所需字段，不含磁盘路径等实现细节。
 *
 * @author mouhinU
 * @date 2026-09-20
 */
@Getter
@Setter
public class ExamDocumentImageVO {

    /** 对外访问句柄 */
    private String assetKey;

    /** 所属文档 ID */
    private Long documentId;

    /** 所属文档 Key */
    private String documentKey;

    /** 来源文档文件名（全局检索展示用，可为空） */
    private String sourceDocumentName;

    /** 直接可访问的图片 URL 路径 */
    private String url;

    /** 来源页码 / 工作表序号 / 幻灯片序号（1 起始） */
    private Integer pageNo;

    /** 同页内序号 */
    private Integer seqOnPage;

    /** MIME 类型 */
    private String mimeType;

    /** 像素宽 */
    private Integer width;

    /** 像素高 */
    private Integer height;

    /** 字节数 */
    private Long byteSize;
}
