package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.model.entity.Department;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.DepartmentDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.DepartmentMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link DepartmentGatewayImpl} 契约单测：锁定转换、父链上溯与空/缺失分支。
 *
 * <p>Mapper 以 {@code mock(DepartmentMapper.class)} 手工注入，不启 Spring。
 *
 * @author mouhinU
 * @date 2026-09-24 18:21:00
 */
@DisplayName("部门仓储实现契约单测 (DepartmentGatewayImpl)")
class DepartmentGatewayImplTest {

    private final DepartmentMapper departmentMapper = mock(DepartmentMapper.class);
    private final DepartmentGatewayImpl gateway = new DepartmentGatewayImpl(departmentMapper);

    private DepartmentDO doOf(Long id, Long parentId, String name) {
        DepartmentDO doObj = new DepartmentDO();
        doObj.setId(id);
        doObj.setParentId(parentId);
        doObj.setDepartmentKey("key-" + id);
        doObj.setDepartmentName(name);
        return doObj;
    }

    @Test
    @DisplayName("findById 命中 → DO→Entity 字段映射；未命中回 empty")
    void findByIdMapsAndHandlesMissing() {
        when(departmentMapper.selectById(1L)).thenReturn(doOf(1L, 2L, "研发部"));
        Optional<Department> hit = gateway.findById(1L);
        assertThat(hit).isPresent();
        assertThat(hit.get().getDepartmentName()).isEqualTo("研发部");
        assertThat(hit.get().getParentId()).isEqualTo(2L);

        when(departmentMapper.selectById(99L)).thenReturn(null);
        assertThat(gateway.findById(99L)).isEmpty();
    }

    @Test
    @DisplayName("findByDepartmentKey → 条件查询命中转 Entity")
    void findByDepartmentKeyUsesWrapper() {
        when(departmentMapper.selectOne(any())).thenReturn(doOf(5L, null, "总部"));
        Optional<Department> hit = gateway.findByDepartmentKey("key-5");
        assertThat(hit).isPresent();
        assertThat(hit.get().isTopLevel()).isTrue();
    }

    @Test
    @DisplayName("save → Entity→DO 字段回写 + createTime 非空 + 主键回填")
    void saveWritesDoFieldsAndBackfillsId() {
        Department dept = new Department();
        dept.setDepartmentKey("key-9");
        dept.setDepartmentName("测试部");
        dept.setParentId(1L);

        when(departmentMapper.insert(any(DepartmentDO.class)))
                .thenAnswer(
                        inv -> {
                            inv.getArgument(0, DepartmentDO.class).setId(9L);
                            return 1;
                        });

        gateway.save(dept);

        ArgumentCaptor<DepartmentDO> cap = ArgumentCaptor.forClass(DepartmentDO.class);
        verify(departmentMapper, times(1)).insert(cap.capture());
        DepartmentDO saved = cap.getValue();
        assertThat(saved.getDepartmentKey()).isEqualTo("key-9");
        assertThat(saved.getDepartmentName()).isEqualTo("测试部");
        assertThat(saved.getParentId()).isEqualTo(1L);
        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(dept.getId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("findSelfAndAncestorIds → 沿父链上溯收集自身与祖先 ID")
    void findSelfAndAncestorIdsWalksParentChain() {
        when(departmentMapper.selectById(1L)).thenReturn(doOf(1L, 2L, "子部门"));
        when(departmentMapper.selectById(2L)).thenReturn(doOf(2L, null, "父部门"));

        List<Long> ids = gateway.findSelfAndAncestorIds(1L);

        assertThat(ids).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("findSelfAndAncestorIds 起点即缺失 → 仅返回入参 ID；save(null) 抛 NPE")
    void ancestorChainStopsOnMissingAndNullSaveRejected() {
        when(departmentMapper.selectById(7L)).thenReturn(null);
        assertThat(gateway.findSelfAndAncestorIds(7L)).containsExactly(7L);

        assertThatThrownBy(() -> gateway.save(null)).isInstanceOf(NullPointerException.class);
        verify(departmentMapper, never()).insert(any(DepartmentDO.class));
    }
}
