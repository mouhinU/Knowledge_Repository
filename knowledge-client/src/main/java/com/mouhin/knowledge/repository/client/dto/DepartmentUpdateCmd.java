package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 修改部门命令
 *
 * <p>departmentName / parentId 为 null 时保持原值不变（沿用既有更新语义）。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Getter
@Setter
public class DepartmentUpdateCmd {

    /** 部门业务标识（路径参数注入） */
    private String departmentKey;
    /** 新部门名称（可空=不改） */
    private String departmentName;
    /** 新上级部门 ID（可空=不改） */
    private Long parentId;
}
