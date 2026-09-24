package com.mouhin.knowledge.repository.application.executor.wronganswer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.client.dto.StudentOptionVO;
import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 考生下拉选项查询执行器单测：锁定全量转换（id / username / displayName / studentNo 逐字段映射）与空库回空列表语义。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("考生下拉选项查询执行器 (WrongAnswerStudentOptionsQryExe)")
class WrongAnswerStudentOptionsQryExeTest {

    private final StudentGateway studentGateway = mock(StudentGateway.class);
    private final WrongAnswerStudentOptionsQryExe exe =
            new WrongAnswerStudentOptionsQryExe(studentGateway);

    @Test
    @DisplayName("正常转换：四个展示字段逐一对齐")
    void mapsAllDisplayFields() {
        Student s = new Student();
        s.setId(1L);
        s.setUsername("alice");
        s.setDisplayName("爱丽丝");
        s.setStudentNo("2026001");
        when(studentGateway.listAll()).thenReturn(List.of(s));

        List<StudentOptionVO> options = exe.execute();

        assertThat(options)
                .singleElement()
                .satisfies(
                        vo -> {
                            assertThat(vo.getId()).isEqualTo(1L);
                            assertThat(vo.getUsername()).isEqualTo("alice");
                            assertThat(vo.getDisplayName()).isEqualTo("爱丽丝");
                            assertThat(vo.getStudentNo()).isEqualTo("2026001");
                        });
    }

    @Test
    @DisplayName("保持网关返回顺序")
    void preservesOrder() {
        Student a = new Student();
        a.setId(1L);
        a.setUsername("a");
        Student b = new Student();
        b.setId(2L);
        b.setUsername("b");
        when(studentGateway.listAll()).thenReturn(List.of(a, b));

        assertThat(exe.execute())
                .extracting(StudentOptionVO::getUsername)
                .containsExactly("a", "b");
    }

    @Test
    @DisplayName("无考生 → 返回空列表，不抛异常")
    void emptyGatewayReturnsEmptyList() {
        when(studentGateway.listAll()).thenReturn(List.of());

        assertThat(exe.execute()).isEmpty();
        verify(studentGateway).listAll();
    }
}
