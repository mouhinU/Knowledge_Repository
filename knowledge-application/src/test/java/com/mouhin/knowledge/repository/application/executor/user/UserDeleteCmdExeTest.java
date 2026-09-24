package com.mouhin.knowledge.repository.application.executor.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 删除用户命令执行器单测：当前实现仅"校验存在 + 二次查一次"，不做实际 delete —— 锁定既有语义（迁移不改变运行）， 并对找不到 / null key
 * 场景断言异常。未来如启用真删除需同步更新本测试。
 *
 * @author mouhinU
 * @date 2026-09-24 16:39:00
 */
@DisplayName("删除用户命令执行器 (UserDeleteCmdExe)")
class UserDeleteCmdExeTest {

    private final UserGateway userGateway = mock(UserGateway.class);
    private final UserDeleteCmdExe exe = new UserDeleteCmdExe(userGateway);

    @Test
    @DisplayName("用户不存在 → IllegalArgumentException('User not found: xxx')")
    void missingUserRejected() {
        when(userGateway.findByUserKey("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exe.execute("ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found")
                .hasMessageContaining("ghost");
    }

    @Test
    @DisplayName("用户存在 → 当前实现不执行任何写/删（TODO(行为可疑): 应改为软删或明确禁止调用此入口）")
    void presentUserCurrentlyNoOp() {
        User u = new User();
        u.setUserKey("k1");
        u.setUsername("alice");
        when(userGateway.findByUserKey("k1")).thenReturn(Optional.of(u));

        exe.execute("k1");

        // 迁移保留原语义：无 update / 无 deleteById / 无 status 变更
        verify(userGateway, never()).update(any());
        // 若未来接入 deleteById，请在此改为 verify(userGateway).deleteById(...)
    }
}
