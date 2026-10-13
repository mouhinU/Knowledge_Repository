package com.mouhin.knowledge.repository.client.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 部门树节点视图对象
 *
 * <p>字段与既有 /tree 端点的嵌套 Map 完全一致：id、departmentKey、departmentName、 parentId（可为
 * null，保持原始语义）、children（子节点列表，默认空数组）。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Getter
@Setter
public class DepartmentTreeNodeVO {

    private Long id;
    private String departmentKey;
    private String departmentName;
    private Long parentId;
    private List<DepartmentTreeNodeVO> children = new ArrayList<>();
}
