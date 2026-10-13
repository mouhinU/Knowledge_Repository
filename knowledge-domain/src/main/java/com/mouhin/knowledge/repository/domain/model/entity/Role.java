package com.mouhin.knowledge.repository.domain.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色实体
 *
 * @author mouhinU
 * @date 2026-09-02
 */
@Getter
@Setter
public class Role {

    private Long id;

    /** 角色唯一标识（UUID） */
    private String roleKey;

    /** 角色名称 */
    private String roleName;

    /** 角色描述 */
    private String description;

    private LocalDateTime createdTime;

    // ==================== equals / hashCode / toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Role role = (Role) o;
        return Objects.equals(id, role.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Role{"
                + "id="
                + id
                + ", roleKey='"
                + roleKey
                + '\''
                + ", roleName='"
                + roleName
                + '\''
                + '}';
    }
}
