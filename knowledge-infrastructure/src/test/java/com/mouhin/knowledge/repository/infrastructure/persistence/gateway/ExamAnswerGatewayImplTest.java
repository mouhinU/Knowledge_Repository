package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.ExamAnswerDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.ExamAnswerMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link ExamAnswerGatewayImpl} 契约单测：锁定 CRUD 转换、批量落库与待复核计数。
 *
 * <p>Mapper 以 {@code mock(ExamAnswerMapper.class)} 手工注入，不启 Spring。
 *
 * <p>注：{@code clearReviewOverride} / {@code clearAiTrace} 依赖 {@code LambdaUpdateWrapper.set(...)}
 * 清空列，需 TableInfo 缓存，交由 E2E 覆盖，本类不建测。
 *
 * @author mouhinU
 * @date 2026-09-24 18:21:00
 */
@DisplayName("答题记录仓储实现契约单测 (ExamAnswerGatewayImpl)")
class ExamAnswerGatewayImplTest {

    private final ExamAnswerMapper examAnswerMapper = mock(ExamAnswerMapper.class);
    private final ExamAnswerGatewayImpl gateway = new ExamAnswerGatewayImpl(examAnswerMapper);

    private ExamAnswerDO doOf(Long id, Integer index) {
        ExamAnswerDO doObj = new ExamAnswerDO();
        doObj.setId(id);
        doObj.setSessionId(5L);
        doObj.setQuestionIndex(index);
        doObj.setQuestionType("SINGLE_CHOICE");
        doObj.setStudentAnswer("A");
        return doObj;
    }

    @Test
    @DisplayName("findById 命中 → DO→Entity 映射；未命中回 empty")
    void findByIdMapsAndHandlesMissing() {
        when(examAnswerMapper.selectById(1L)).thenReturn(doOf(1L, 0));
        Optional<ExamAnswer> hit = gateway.findById(1L);
        assertThat(hit).isPresent();
        assertThat(hit.get().getSessionId()).isEqualTo(5L);
        assertThat(hit.get().getQuestionIndex()).isZero();

        when(examAnswerMapper.selectById(99L)).thenReturn(null);
        assertThat(gateway.findById(99L)).isEmpty();
    }

    @Test
    @DisplayName("save → Entity→DO 回写 + 主键回填")
    void saveWritesAndBackfillsId() {
        ExamAnswer answer = new ExamAnswer();
        answer.setSessionId(5L);
        answer.setQuestionIndex(3);
        answer.setStudentAnswer("B");

        when(examAnswerMapper.insert(any(ExamAnswerDO.class)))
                .thenAnswer(
                        inv -> {
                            inv.getArgument(0, ExamAnswerDO.class).setId(42L);
                            return 1;
                        });

        gateway.save(answer);

        ArgumentCaptor<ExamAnswerDO> cap = ArgumentCaptor.forClass(ExamAnswerDO.class);
        verify(examAnswerMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getSessionId()).isEqualTo(5L);
        assertThat(cap.getValue().getQuestionIndex()).isEqualTo(3);
        assertThat(answer.getId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("saveAll → 逐条 insert 并回填各自主键")
    void saveAllInsertsEachAndBackfillsIds() {
        ExamAnswer a1 = new ExamAnswer();
        a1.setSessionId(5L);
        a1.setQuestionIndex(0);
        ExamAnswer a2 = new ExamAnswer();
        a2.setSessionId(5L);
        a2.setQuestionIndex(1);
        final long[] seq = {100L};
        when(examAnswerMapper.insert(any(ExamAnswerDO.class)))
                .thenAnswer(
                        inv -> {
                            inv.getArgument(0, ExamAnswerDO.class).setId(seq[0]++);
                            return 1;
                        });

        gateway.saveAll(List.of(a1, a2));

        verify(examAnswerMapper, times(2)).insert(any(ExamAnswerDO.class));
        assertThat(a1.getId()).isEqualTo(100L);
        assertThat(a2.getId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("listBySessionId → selectList 批量转 Entity")
    void listBySessionIdMapsEveryRow() {
        when(examAnswerMapper.selectList(any())).thenReturn(List.of(doOf(1L, 0), doOf(2L, 1)));

        List<ExamAnswer> answers = gateway.listBySessionId(5L);

        assertThat(answers).hasSize(2);
        assertThat(answers).extracting(ExamAnswer::getQuestionIndex).containsExactly(0, 1);
    }

    @Test
    @DisplayName("countNeedsReview → selectCount 透传")
    void countNeedsReviewReturnsSelectCount() {
        when(examAnswerMapper.selectCount(any())).thenReturn(6L);
        assertThat(gateway.countNeedsReview(5L)).isEqualTo(6L);
    }
}
