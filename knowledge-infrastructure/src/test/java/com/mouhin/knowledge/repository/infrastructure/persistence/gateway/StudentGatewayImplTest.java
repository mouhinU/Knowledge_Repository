package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.model.entity.Student;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.StudentDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.StudentMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link StudentGatewayImpl} 契约单测：锁定 Entity⇄DO 字段回写、查询转换与空/缺失分支。
 *
 * <p>Mapper 以 {@code mock(StudentMapper.class)} 手工注入，不启 Spring。
 *
 * <p>注：{@code clearSessionToken} 依赖 {@code LambdaUpdateWrapper.set(...)} 清空令牌，需 MyBatis-Plus
 * TableInfo 缓存，交由 E2E 覆盖，本类不建测。
 *
 * @author mouhinU
 * @date 2026-09-24 18:21:00
 */
@DisplayName("考生仓储实现契约单测 (StudentGatewayImpl)")
class StudentGatewayImplTest {

    private final StudentMapper studentMapper = mock(StudentMapper.class);
    private final StudentGatewayImpl gateway = new StudentGatewayImpl(studentMapper);

    private StudentDO sampleDO() {
        StudentDO doObj = new StudentDO();
        doObj.setId(3L);
        doObj.setUsername("alice");
        doObj.setDisplayName("Alice");
        doObj.setStudentNo("S001");
        doObj.setDepartmentId("dept-1");
        doObj.setStatus("ACTIVE");
        doObj.setSessionToken("tok-abc");
        return doObj;
    }

    @Test
    @DisplayName("findById 命中 → DO→Entity 字段映射；未命中回 empty")
    void findByIdMapsAndHandlesMissing() {
        when(studentMapper.selectById(3L)).thenReturn(sampleDO());
        Optional<Student> hit = gateway.findById(3L);
        assertThat(hit).isPresent();
        assertThat(hit.get().getUsername()).isEqualTo("alice");
        assertThat(hit.get().getStudentNo()).isEqualTo("S001");
        assertThat(hit.get().getSessionToken()).isEqualTo("tok-abc");

        when(studentMapper.selectById(99L)).thenReturn(null);
        assertThat(gateway.findById(99L)).isEmpty();
    }

    @Test
    @DisplayName("findByUsername → 条件查询命中转 Entity，缺失回空")
    void findByUsernameUsesWrapper() {
        when(studentMapper.selectOne(any())).thenReturn(sampleDO());
        assertThat(gateway.findByUsername("alice")).isPresent();

        when(studentMapper.selectOne(any())).thenReturn(null);
        assertThat(gateway.findByUsername("ghost")).isEmpty();
    }

    @Test
    @DisplayName("save → Entity→DO 字段回写正确 + 主键回填")
    void saveWritesDoFieldsAndBackfillsId() {
        Student student = new Student();
        student.setUsername("bob");
        student.setStudentNo("S002");
        student.setDepartmentId("dept-2");
        student.setStatus("ACTIVE");

        when(studentMapper.insert(any(StudentDO.class)))
                .thenAnswer(
                        inv -> {
                            inv.getArgument(0, StudentDO.class).setId(88L);
                            return 1;
                        });

        gateway.save(student);

        ArgumentCaptor<StudentDO> cap = ArgumentCaptor.forClass(StudentDO.class);
        verify(studentMapper, times(1)).insert(cap.capture());
        StudentDO saved = cap.getValue();
        assertThat(saved.getUsername()).isEqualTo("bob");
        assertThat(saved.getStudentNo()).isEqualTo("S002");
        assertThat(saved.getDepartmentId()).isEqualTo("dept-2");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(student.getId()).isEqualTo(88L);
    }

    @Test
    @DisplayName("update → 走 updateById；save(null) 抛 NPE 且不落库")
    void updateUsesUpdateByIdAndNullSaveRejected() {
        Student student = new Student();
        student.setId(3L);
        student.setUsername("alice");
        gateway.update(student);
        verify(studentMapper, times(1)).updateById(any(StudentDO.class));

        assertThatThrownBy(() -> gateway.save(null)).isInstanceOf(NullPointerException.class);
        verify(studentMapper, never()).insert(any(StudentDO.class));
    }

    @Test
    @DisplayName("listByDepartment → selectList 结果批量转 Entity")
    void listByDepartmentMapsEveryRow() {
        when(studentMapper.selectList(any())).thenReturn(List.of(sampleDO(), sampleDO()));

        List<Student> students = gateway.listByDepartment("dept-1");

        assertThat(students).hasSize(2);
        assertThat(students).allMatch(s -> "alice".equals(s.getUsername()));
    }
}
