package com.mouhin.knowledge.repository.application.converter;

import com.mouhin.knowledge.repository.client.dto.UserVO;
import com.mouhin.knowledge.repository.domain.model.entity.User;

/**
 * 用户 领域实体 → 视图对象 转换器（app 层）
 *
 * <p>映射与原 {@code UserAdminController.toResponse} 一致：departmentId null→""， admin
 * null→false，createdTime 使用 {@code LocalDateTime#toString}（null→""）。
 *
 * <p>例外登记（红线 #6「优先用 MapStruct」的例外）：VO 六字段中 {@code departmentId}/{@code admin}/{@code status}/{@code
 * createdTime} 均需 null 缺省或格式化，MapStruct {@code defaultValue} 只能落常量串、不能落布尔原语或调用 {@code toString}，改用
 * {@code expression} 则退化为逐字段样板，可读性无提升。故保留手写；纯 1:1 与轻派生字段已优先 MapStruct（见同包 {@code @Mapper}）。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
public final class UserConverter {

    private UserConverter() {}

    public static UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setUserKey(user.getUserKey());
        vo.setUsername(user.getUsername());
        vo.setDepartmentId(user.getDepartmentId() != null ? user.getDepartmentId() : "");
        vo.setAdmin(user.getAdmin() != null ? user.getAdmin() : false);
        vo.setCreatedTime(user.getCreatedTime() != null ? user.getCreatedTime().toString() : "");
        vo.setStatus(user.getStatus() != null ? user.getStatus() : User.STATUS_ACTIVE);
        return vo;
    }
}
