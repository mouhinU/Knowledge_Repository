package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.ExamQuestionDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.ExamQuestionMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ExamQuestionGatewayImpl} 契约单测：锁定条件查询转换、批量插入主键回填与删除委托。
 *
 * <p>Mapper 以 {@code mock(ExamQuestionMapper.class)} 手工注入，不启 Spring。
 *
 * <p>注：{@code updateCorrectAnswer} / {@code updateCorrection} / {@code updateImagesJson} 依赖 {@code
 * LambdaUpdateWrapper.set(...)}，需 TableInfo 缓存，交由 E2E 覆盖，本类不建测。
 *
 * @author mouhinU
 * @date 2026-09-24 18:21:00
 */
@DisplayName("结构化题目仓储实现契约单测 (ExamQuestionGatewayImpl)")
class ExamQuestionGatewayImplTest {

    private final ExamQuestionMapper examQuestionMapper = mock(ExamQuestionMapper.class);
    private final ExamQuestionGatewayImpl gateway = new ExamQuestionGatewayImpl(examQuestionMapper);

    private ExamQuestionDO doOf(Long id, Integer number) {
        ExamQuestionDO doObj = new ExamQuestionDO();
        doObj.setId(id);
        doObj.setSessionKey("sk-1");
        doObj.setQuestionNumber(number);
        doObj.setStem("题目" + number);
        doObj.setQuestionType("SINGLE_CHOICE");
        return doObj;
    }

    @Test
    @DisplayName("listBySessionKey → selectList 批量转 Entity")
    void listBySessionKeyMapsEveryRow() {
        when(examQuestionMapper.selectList(any())).thenReturn(List.of(doOf(1L, 1), doOf(2L, 2)));

        List<ExamQuestion> questions = gateway.listBySessionKey("sk-1");

        assertThat(questions).hasSize(2);
        assertThat(questions).extracting(ExamQuestion::getQuestionNumber).containsExactly(1, 2);
    }

    @Test
    @DisplayName("findBySessionKeyAndNumber → 条件查询命中转 Entity；未命中回 empty")
    void findBySessionKeyAndNumberMapsAndHandlesMissing() {
        when(examQuestionMapper.selectOne(any())).thenReturn(doOf(1L, 3));
        Optional<ExamQuestion> hit = gateway.findBySessionKeyAndNumber("sk-1", 3);
        assertThat(hit).isPresent();
        assertThat(hit.get().getStem()).isEqualTo("题目3");

        when(examQuestionMapper.selectOne(any())).thenReturn(null);
        assertThat(gateway.findBySessionKeyAndNumber("sk-1", 99)).isEmpty();
    }

    @Test
    @DisplayName("batchInsert 空/null → 短路返回，不落库")
    void batchInsertShortCircuitsOnEmpty() {
        gateway.batchInsert(List.of());
        gateway.batchInsert(null);
        verify(examQuestionMapper, never()).insert(any(ExamQuestionDO.class));
    }

    @Test
    @DisplayName("batchInsert 非空 → 逐条 insert 并回填各自主键")
    void batchInsertWritesEachAndBackfillsIds() {
        ExamQuestion q1 = new ExamQuestion();
        q1.setSessionKey("sk-1");
        q1.setQuestionNumber(1);
        ExamQuestion q2 = new ExamQuestion();
        q2.setSessionKey("sk-1");
        q2.setQuestionNumber(2);
        final long[] seq = {200L};
        when(examQuestionMapper.insert(any(ExamQuestionDO.class)))
                .thenAnswer(
                        inv -> {
                            inv.getArgument(0, ExamQuestionDO.class).setId(seq[0]++);
                            return 1;
                        });

        gateway.batchInsert(List.of(q1, q2));

        verify(examQuestionMapper, times(2)).insert(any(ExamQuestionDO.class));
        assertThat(q1.getId()).isEqualTo(200L);
        assertThat(q2.getId()).isEqualTo(201L);
    }

    @Test
    @DisplayName("deleteBySessionKey → 委托 delete")
    void deleteBySessionKeyDelegatesToDelete() {
        gateway.deleteBySessionKey("sk-1");
        verify(examQuestionMapper, times(1)).delete(any());
    }
}
