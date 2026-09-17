package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 部门视图对象（扁平结构，用于列表 / 新增 / 更新响应）
 *
 * <p>字段与既有 REST JSON 完全一致：id、departmentKey、departmentName、parentId。
 * 为保持向后兼容，parentId 为 null 时由转换器归一为 0。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Getter
@Setter
public class DepartmentVO {

    private Long id;
    private String departmentKey;
    private String departmentName;
    private Long parentId;
}
