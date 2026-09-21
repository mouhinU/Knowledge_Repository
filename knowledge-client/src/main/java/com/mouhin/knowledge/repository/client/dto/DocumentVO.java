package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 文档视图对象
 *
 * <p>字段与原 {@code DocumentAdminController.buildDocumentResponse} 完全一致：
 * fileSize/totalPages（null→0）、status（枚举 name）、visibility（null→""）、 tags（null→""）、createdTime（{@code
 * LocalDateTime#toString}，null→""）。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Getter
@Setter
public class DocumentVO {

    private String documentKey;
    private String fileName;
    private Long fileSize;
    private Integer totalPages;
    private String status;
    private String visibility;
    private String ownerId;
    private String departmentId;
    private String tags;
    private String createdTime;
}
