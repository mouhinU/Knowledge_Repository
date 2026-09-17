package com.mouhin.knowledge.repository.application.converter;

import com.mouhin.knowledge.repository.client.dto.UserVO;
import com.mouhin.knowledge.repository.domain.model.entity.User;

/**
 * 用户 领域实体 → 视图对象 转换器（app 层）
 *
 * <p>映射与原 {@code UserAdminController.toResponse} 一致：departmentId null→""，
 * admin null→false，createdTime 使用 {@code LocalDateTime#toString}（null→""）。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
public final class UserConverter {

    private UserConverter() {
    }

    public static UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setUserKey(user.getUserKey());
        vo.setUsername(user.getUsername());
        vo.setDepartmentId(user.getDepartmentId() != null ? user.getDepartmentId() : "");
        vo.setAdmin(user.getAdmin() != null ? user.getAdmin() : false);
        vo.setCreatedTime(user.getCreatedTime() != null ? user.getCreatedTime().toString() : "");
        return vo;
    }
}
