package com.mouhin.knowledge.repository.application.executor.department;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mouhin.knowledge.repository.client.dto.DepartmentCreateCmd;
import com.mouhin.knowledge.repository.client.dto.DepartmentVO;
import com.mouhin.knowledge.repository.domain.gateway.DepartmentGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Department;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 新增部门命令执行器单测：锁定 departmentKey 由 UUID 生成、parentId 直传（顶层 null 保留在 DO 层）、 VO 中 parentId null→0 归一化契约。
 *
 * @author mouhinU
 * @date 2026-09-24 16:40:00
 */
@DisplayName("新增部门命令执行器 (DepartmentCreateCmdExe)")
class DepartmentCreateCmdExeTest {

    private final DepartmentGateway departmentGateway = mock(DepartmentGateway.class);
    private final DepartmentCreateCmdExe exe = new DepartmentCreateCmdExe(departmentGateway);

    private DepartmentCreateCmd cmd(String name, Long parentId) {
        DepartmentCreateCmd c = new DepartmentCreateCmd();
        c.setDepartmentName(name);
        c.setParentId(parentId);
        return c;
    }

    @Test
    @DisplayName("顶层部门（parentId=null）→ 落库保留 null；VO 归一化 parentId=0")
    void topLevelAssignsUuidAndNormalizesVoParent() {
        DepartmentVO vo = exe.execute(cmd("Root", null));

        ArgumentCaptor<Department> cap = ArgumentCaptor.forClass(Department.class);
        verify(departmentGateway, times(1)).save(cap.capture());
        Department saved = cap.getValue();
        assertThat(saved.getDepartmentName()).isEqualTo("Root");
        assertThat(saved.getParentId()).isNull();
        assertThat(saved.getDepartmentKey()).isNotBlank().hasSize(36);
        // VO 契约：parentId null → 0
        assertThat(vo.getParentId()).isEqualTo(0L);
        assertThat(vo.getDepartmentName()).isEqualTo("Root");
    }

    @Test
    @DisplayName("子部门 → parentId 直传")
    void childKeepsExplicitParentId() {
        DepartmentVO vo = exe.execute(cmd("Sub", 100L));

        ArgumentCaptor<Department> cap = ArgumentCaptor.forClass(Department.class);
        verify(departmentGateway).save(cap.capture());
        assertThat(cap.getValue().getParentId()).isEqualTo(100L);
        assertThat(vo.getParentId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("TODO(行为可疑): 名称为空仍会落库（未做校验），建议 Executor 前置非空 + 唯一性")
    void blankNameCurrentlyNotRejected() {
        // 断言当前行为：不因空 name 短路。补上校验后本用例应改为 assertThrows。
        exe.execute(cmd("", null));
        verify(departmentGateway, times(1)).save(any());
    }
}
