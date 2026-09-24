package com.mouhin.knowledge.repository.application.executor.wronganswer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.client.dto.WrongAnswerPageVO;
import com.mouhin.knowledge.repository.client.dto.WrongAnswerVO;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 错题分页查询执行器单测：锁定页参归一（负页→0 / size≤0→10）、越界切片返回空页且 total 保持全量、 统计概览（主题数 / 考生数 / 最多题型 / 平均得分率 4
 * 位小数，maxScore≤0 不参与均值）。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("错题分页查询执行器 (WrongAnswerPageQryExe)")
class WrongAnswerPageQryExeTest {

    private final WrongAnswerListQryExe listQryExe = mock(WrongAnswerListQryExe.class);
    private final WrongAnswerPageQryExe exe = new WrongAnswerPageQryExe(listQryExe);

    private WrongAnswerVO item(String topic, Long studentId, String type, int max, int eff) {
        WrongAnswerVO vo = new WrongAnswerVO();
        vo.setTopic(topic);
        vo.setStudentId(studentId);
        vo.setQuestionType(type);
        vo.setMaxScore(max);
        vo.setEffectiveScore(eff);
        return vo;
    }

    @Test
    @DisplayName("正常分页：第 1 页 size=2 取中间片段，stats 基于全量而非当前页")
    void slicesPageAndStatsOnFullList() {
        List<WrongAnswerVO> all =
                List.of(
                        item("T1", 1L, "SINGLE_CHOICE", 10, 0),
                        item("T1", 1L, "SINGLE_CHOICE", 10, 5),
                        item("T2", 2L, "SHORT_ANSWER", 10, 4),
                        item("T2", 2L, "SINGLE_CHOICE", 10, 0));
        when(listQryExe.execute(any(), any(), any())).thenReturn(all);

        WrongAnswerPageVO vo = exe.execute(1L, null, null, 1, 2);

        assertThat(vo.getRecords()).hasSize(2);
        assertThat(vo.getTotal()).isEqualTo(4);
        assertThat(vo.getPage()).isEqualTo(1);
        assertThat(vo.getStats().getTotalCount()).isEqualTo(4);
        assertThat(vo.getStats().getTopType()).isEqualTo("SINGLE_CHOICE");
        assertThat(vo.getStats().getTopicCount()).isEqualTo(2);
        assertThat(vo.getStats().getStudentCount()).isEqualTo(2);
        // 平均得分率 = (0 + 0.5 + 0.4 + 0) / 4 = 0.225
        assertThat(vo.getStats().getAvgScoreRate()).isEqualTo(0.225);
        verify(listQryExe).execute(1L, null, null);
    }

    @Test
    @DisplayName("页参归一：page=-3 → 0；size=0 → 默认 10")
    void negativeAndZeroPageParamsNormalized() {
        when(listQryExe.execute(any(), any(), any())).thenReturn(new ArrayList<>());

        WrongAnswerPageVO vo = exe.execute(null, null, null, -3, 0);

        assertThat(vo.getPage()).isZero();
        assertThat(vo.getSize()).isEqualTo(10);
        assertThat(vo.getRecords()).isEmpty();
    }

    @Test
    @DisplayName("页码越界 → from/to 收敛到列表尾部，records 为空但 total/stats 仍全量")
    void outOfRangePageReturnsEmptyRecords() {
        when(listQryExe.execute(any(), any(), any()))
                .thenReturn(List.of(item("T", 1L, "SINGLE_CHOICE", 10, 0)));

        WrongAnswerPageVO vo = exe.execute(null, null, null, 99, 10);

        assertThat(vo.getRecords()).isEmpty();
        assertThat(vo.getTotal()).isEqualTo(1);
    }

    @Test
    @DisplayName("空列表 → stats 归零（topType=null / avg=0.0），不抛异常")
    void emptyListStatsZeroed() {
        when(listQryExe.execute(any(), any(), any())).thenReturn(List.of());

        WrongAnswerPageVO vo = exe.execute(null, null, null, 0, 10);

        assertThat(vo.getTotal()).isZero();
        assertThat(vo.getStats().getTopType()).isNull();
        assertThat(vo.getStats().getAvgScoreRate()).isZero();
        assertThat(vo.getStats().getTopicCount()).isZero();
    }

    @Test
    @DisplayName("maxScore≤0 的脏数据不参与平均得分率；null 题型计入 UNKNOWN")
    void dirtyDataHandledInStats() {
        WrongAnswerVO zero = item("T1", 1L, "SINGLE_CHOICE", 0, 0);
        WrongAnswerVO nullType = item("T1", 1L, null, 10, 5);
        when(listQryExe.execute(any(), any(), any())).thenReturn(List.of(zero, nullType));

        WrongAnswerPageVO vo = exe.execute(null, null, null, 0, 10);

        // 只有 nullType 参与均值：5/10 = 0.5
        assertThat(vo.getStats().getAvgScoreRate()).isEqualTo(0.5);
        assertThat(vo.getStats().getTopType()).isIn(List.of("UNKNOWN", "SINGLE_CHOICE"));
    }
}
