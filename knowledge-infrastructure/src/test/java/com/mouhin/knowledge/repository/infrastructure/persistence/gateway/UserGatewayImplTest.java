package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.model.entity.User;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.UserDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.UserMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.UserRoleMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link UserGatewayImpl} 契约单测：锁定 Entity⇄DO 字段回写、查询转换与空/缺失分支。
 *
 * <p>Mapper 以 {@code mock(UserMapper.class)} 手工注入，不启 Spring；SQL 行为由 Flyway 冒烟与 E2E
 * 覆盖，本类仅验证仓储实现的转换契约。
 *
 * @author mouhinU
 * @date 2026-09-24 18:21:00
 */
@DisplayName("用户仓储实现契约单测 (UserGatewayImpl)")
class UserGatewayImplTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
    private final UserGatewayImpl gateway = new UserGatewayImpl(userMapper, userRoleMapper);

    private UserDO sampleDO() {
        UserDO doObj = new UserDO();
        doObj.setId(7L);
        doObj.setUserKey("uuid-7");
        doObj.setUsername("alice");
        doObj.setDepartmentId("dept-1");
        doObj.setAdmin(Boolean.TRUE);
        doObj.setPasswordHash("$2a$10$hash");
        doObj.setStatus("ACTIVE");
        return doObj;
    }

    @Test
    @DisplayName("findById 命中 → DO→Entity 字段逐一映射")
    void findByIdMapsDoToEntity() {
        when(userMapper.selectById(7L)).thenReturn(sampleDO());

        Optional<User> result = gateway.findById(7L);

        assertThat(result).isPresent();
        User user = result.get();
        assertThat(user.getId()).isEqualTo(7L);
        assertThat(user.getUserKey()).isEqualTo("uuid-7");
        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getDepartmentId()).isEqualTo("dept-1");
        assertThat(user.getAdmin()).isTrue();
        assertThat(user.getPasswordHash()).isEqualTo("$2a$10$hash");
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("findById 未命中 → Optional.empty()")
    void findByIdMissingReturnsEmpty() {
        when(userMapper.selectById(99L)).thenReturn(null);

        assertThat(gateway.findById(99L)).isEmpty();
    }

    @Test
    @DisplayName("findByUsername → 条件查询命中转 Entity，缺失回空")
    void findByUsernameUsesWrapper() {
        when(userMapper.selectOne(any())).thenReturn(sampleDO());
        Optional<User> hit = gateway.findByUsername("alice");
        assertThat(hit).isPresent();
        assertThat(hit.get().getUsername()).isEqualTo("alice");

        when(userMapper.selectOne(any())).thenReturn(null);
        assertThat(gateway.findByUsername("ghost")).isEmpty();
    }

    @Test
    @DisplayName("save → Entity→DO 字段回写正确 + 生成主键回填 + 时间戳非空")
    void saveWritesDoFieldsAndBackfillsId() {
        User user = new User();
        user.setUsername("bob");
        user.setUserKey("uuid-bob");
        user.setDepartmentId("dept-2");
        user.setAdmin(Boolean.FALSE);
        user.setStatus("ACTIVE");

        when(userMapper.insert(any(UserDO.class)))
                .thenAnswer(
                        inv -> {
                            inv.getArgument(0, UserDO.class).setId(50L);
                            return 1;
                        });

        gateway.save(user);

        ArgumentCaptor<UserDO> cap = ArgumentCaptor.forClass(UserDO.class);
        verify(userMapper, times(1)).insert(cap.capture());
        UserDO saved = cap.getValue();
        assertThat(saved.getUsername()).isEqualTo("bob");
        assertThat(saved.getUserKey()).isEqualTo("uuid-bob");
        assertThat(saved.getDepartmentId()).isEqualTo("dept-2");
        assertThat(saved.getAdmin()).isFalse();
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(saved.getUpdateTime()).isNotNull();
        assertThat(user.getId()).isEqualTo(50L);
    }

    @Test
    @DisplayName("update → 走 updateById；save(null) 抛 NPE 且不落库")
    void updateUsesUpdateByIdAndNullSaveRejected() {
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        gateway.update(user);
        verify(userMapper, times(1)).updateById(any(UserDO.class));

        assertThatThrownBy(() -> gateway.save(null)).isInstanceOf(NullPointerException.class);
        verify(userMapper, never()).insert(any(UserDO.class));
    }

    @Test
    @DisplayName("listAll → selectList(null) 结果批量转 Entity")
    void listAllMapsEveryRow() {
        when(userMapper.selectList(any())).thenReturn(List.of(sampleDO(), sampleDO()));

        List<User> users = gateway.listAll();

        assertThat(users).hasSize(2);
        assertThat(users).allMatch(u -> "alice".equals(u.getUsername()));
    }
}
