package com.mouhin.knowledge.repository.application.executor.department;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.client.dto.DepartmentUpdateCmd;
import com.mouhin.knowledge.repository.client.dto.DepartmentVO;
import com.mouhin.knowledge.repository.domain.gateway.DepartmentGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Department;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 修改部门命令执行器单测：锁定 null 保持原值、字段独立更新、找不到 → 'Department\ not\ found'；VO\ 归一化契约同步。
 *
 * @author mouhinU
 * @date 2026-09-24 16:41:00
 */
@DisplayName("修改部门命令执行器 (DepartmentUpdateCmdExe)")
class DepartmentUpdateCmdExeTest {

    private final DepartmentGateway departmentGateway = mock(DepartmentGateway.class);
    private final DepartmentUpdateCmdExe exe = new DepartmentUpdateCmdExe(departmentGateway);

    private Department existing() {
        Department d = new Department();
        d.setId(1L);
        d.setDepartmentKey("k1");
        d.setDepartmentName("Sales");
        d.setParentId(0L);
        return d;
    }

    private DepartmentUpdateCmd cmd(String key, String name, Long parent) {
        DepartmentUpdateCmd c = new DepartmentUpdateCmd();
        c.setDepartmentKey(key);
        c.setDepartmentName(name);
        c.setParentId(parent);
        return c;
    }

    @Test
    @DisplayName("departmentKey 不存在 → 'Department not found'，不落 update")
    void missingDepartmentRejected() {
        when(departmentGateway.findByDepartmentKey("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exe.execute(cmd("ghost", "NewName", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Department not found")
                .hasMessageContaining("ghost");
        verify(departmentGateway, never()).update(any());
    }

    @Test
    @DisplayName("name/parent 均 null → 保持原值，仍走一次 update（沿用既有语义）")
    void allNullKeepsOriginalFields() {
        when(departmentGateway.findByDepartmentKey("k1")).thenReturn(Optional.of(existing()));

        DepartmentVO vo = exe.execute(cmd("k1", null, null));

        ArgumentCaptor<Department> cap = ArgumentCaptor.forClass(Department.class);
        verify(departmentGateway, times(1)).update(cap.capture());
        Department saved = cap.getValue();
        assertThat(saved.getDepartmentName()).isEqualTo("Sales");
        assertThat(saved.getParentId()).isEqualTo(0L);
        assertThat(vo.getDepartmentName()).isEqualTo("Sales");
    }

    @Test
    @DisplayName("仅改名称 → 保留原 parentId")
    void nameOnlyPreservesParent() {
        when(departmentGateway.findByDepartmentKey("k1")).thenReturn(Optional.of(existing()));

        exe.execute(cmd("k1", "Marketing", null));

        ArgumentCaptor<Department> cap = ArgumentCaptor.forClass(Department.class);
        verify(departmentGateway).update(cap.capture());
        assertThat(cap.getValue().getDepartmentName()).isEqualTo("Marketing");
        assertThat(cap.getValue().getParentId()).isEqualTo(0L);
    }

    @Test
    @DisplayName("parentId 变更 → 覆盖；VO 归一化保留")
    void parentUpdated() {
        when(departmentGateway.findByDepartmentKey("k1")).thenReturn(Optional.of(existing()));

        DepartmentVO vo = exe.execute(cmd("k1", null, 7L));

        ArgumentCaptor<Department> cap = ArgumentCaptor.forClass(Department.class);
        verify(departmentGateway).update(cap.capture());
        assertThat(cap.getValue().getParentId()).isEqualTo(7L);
        assertThat(cap.getValue().getDepartmentName()).isEqualTo("Sales");
        assertThat(vo.getParentId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("TODO(行为可疑): 允许 parentId 指向自身 → 会形成自环；建议在 Executor 前置环检测")
    void selfParentCurrentlyNotDetected() {
        Department d = existing();
        d.setId(9L);
        when(departmentGateway.findByDepartmentKey(anyString())).thenReturn(Optional.of(d));

        // 断言当前行为：自环不阻止
        exe.execute(cmd("k1", null, 9L));
        ArgumentCaptor<Department> cap = ArgumentCaptor.forClass(Department.class);
        verify(departmentGateway).update(cap.capture());
        assertThat(cap.getValue().getParentId()).isEqualTo(9L);
    }
}
