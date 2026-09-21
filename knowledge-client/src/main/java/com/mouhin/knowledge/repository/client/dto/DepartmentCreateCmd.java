package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 新增部门命令
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Getter
@Setter
public class DepartmentCreateCmd {

    /** 部门名称 */
    private String departmentName;

    /** 上级部门 ID（可空，表示顶级） */
    private Long parentId;
}
