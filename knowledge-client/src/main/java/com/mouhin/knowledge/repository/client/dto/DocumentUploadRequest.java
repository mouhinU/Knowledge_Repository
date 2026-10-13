package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 文档上传请求参数
 *
 * @author mouhinU
 * @date 2026-09-11
 */
@Getter
@Setter
public class DocumentUploadRequest {

    /** 所有者用户 ID */
    private String ownerId;

    /** 所属部门 ID */
    private String departmentId;

    /** 可见性（PUBLIC / INTERNAL / RESTRICTED / PRIVATE） */
    private String visibility;

    /** 允许访问的角色（逗号分隔） */
    private String allowedRoles;

    /** 标签（逗号分隔） */
    private String tags;

    /** 文档分类（如：工作、学习、休闲） */
    private String category;

    /**
     * 可选：强制指定解析策略栈（{@code ExtractionStrategyEnum} 枚举名，逗号分隔）。留空或 {@code auto} 走配置默认路由；非法枚举名按白名单过滤丢弃。
     */
    private String parsingStrategy;
}
