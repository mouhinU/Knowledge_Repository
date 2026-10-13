package com.mouhin.knowledge.repository.domain.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * 部门实体
 *
 * @author mouhinU
 * @date 2026-09-02
 */
@Getter
@Setter
public class Department {

    private Long id;

    /** 部门唯一标识（UUID） */
    private String departmentKey;

    /** 部门名称 */
    private String departmentName;

    /** 父部门 ID（null 表示顶级部门） */
    private Long parentId;

    private LocalDateTime createdTime;

    // ==================== 业务方法 ====================

    /** 判断是否为顶级部门 */
    public boolean isTopLevel() {
        return parentId == null;
    }

    // ==================== equals / hashCode / toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Department that = (Department) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Department{"
                + "id="
                + id
                + ", departmentKey='"
                + departmentKey
                + '\''
                + ", departmentName='"
                + departmentName
                + '\''
                + ", parentId="
                + parentId
                + '}';
    }
}
