package com.mouhin.knowledge.repository.application.executor.examreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.ExamQuestionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 校对页配图绑定执行器单测（阶段 2）。
 *
 * <p>锁定四项行为：（1）有序 assetKey 原样序列化为 images_json 并就地回写；（2）空白项与重复项被清洗、 保留首次出现顺序；（3）空列表 / null 触发清除（回写
 * null）；（4）题号为空抛非法参数、不落库。
 *
 * @author Knowledge-Repository
 * @date 2026-09-20
 */
@DisplayName("校对页配图绑定 (UpdateQuestionImagesCmdExe)")
class UpdateQuestionImagesCmdExeTest {

    private static final String SESSION = "sess-2026";

    private final PaperReviewSupport support = mock(PaperReviewSupport.class);
    private final ExamQuestionGateway gateway = mock(ExamQuestionGateway.class);
    private final UpdateQuestionImagesCmdExe exe = new UpdateQuestionImagesCmdExe(support, gateway);

    private void stubHistory() {
        ExamHistory history = new ExamHistory();
        history.setSessionId(SESSION);
        when(support.requireHistory(SESSION)).thenReturn(history);
    }

    @Test
    @DisplayName("有序 assetKey → 序列化 JSON 就地回写")
    void persistsOrderedKeys() {
        stubHistory();
        List<String> saved = exe.execute(SESSION, 3, Arrays.asList("aaa", "bbb"));
        assertEquals(Arrays.asList("aaa", "bbb"), saved);
        verify(gateway).updateImagesJson(eq(SESSION), eq(3), eq("[\"aaa\",\"bbb\"]"));
    }

    @Test
    @DisplayName("清洗：去空白 / 去重，保留首次出现顺序")
    void sanitizesAndDedupes() {
        stubHistory();
        List<String> saved =
                exe.execute(SESSION, 1, Arrays.asList("  x  ", "y", "", null, "x", "  ", "z"));
        assertEquals(Arrays.asList("x", "y", "z"), saved);
        verify(gateway).updateImagesJson(eq(SESSION), eq(1), eq("[\"x\",\"y\",\"z\"]"));
    }

    @Test
    @DisplayName("空列表清除绑定（回写 null）")
    void emptyClearsBinding() {
        stubHistory();
        List<String> saved = exe.execute(SESSION, 2, List.of());
        assertEquals(List.of(), saved);
        verify(gateway).updateImagesJson(eq(SESSION), eq(2), any());
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(gateway).updateImagesJson(eq(SESSION), eq(2), captor.capture());
        assertNull(captor.getValue(), "空绑定应回写 null 以清除列");
    }

    @Test
    @DisplayName("题号为空抛非法参数且不落库")
    void blankNumberRejected() {
        assertThrows(
                IllegalArgumentException.class, () -> exe.execute(SESSION, null, List.of("a")));
        verify(gateway, never()).updateImagesJson(any(), any(), any());
    }
}
