package com.mouhin.knowledge.repository.application.executor.examgeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 重新切分的配图保留逻辑单测（阶段 2）。
 *
 * <p>锁定「按印刷题号保留人工配图」这一保证：{@code snapshotImagesByNumber} 只收录非空绑定， {@code applyImageSnapshot}
 * 仅回填题号命中的行、题号漂移的行不误绑、空快照快速返回 0。 纯函数测试，不触发切分 / 解析，确定且离线。
 *
 * @author mouhinU
 * @date 2026-09-20
 */
@DisplayName("重新切分保留人工配图 (按题号回填)")
class ExamQuestionSplitSupportImageTest {

    private static ExamQuestion q(Integer number, String imagesJson) {
        ExamQuestion question = new ExamQuestion();
        question.setQuestionNumber(number);
        question.setImagesJson(imagesJson);
        return question;
    }

    @Test
    @DisplayName("快照仅收录题号非空且有配图的行")
    void snapshotSkipsBlankAndEmptyImages() {
        List<ExamQuestion> existing =
                Arrays.asList(q(1, "[\"k1\"]"), q(2, null), q(3, "   "), q(null, "[\"ignored\"]"));
        Map<Integer, String> snapshot = ExamQuestionSplitSupport.snapshotImagesByNumber(existing);
        assertEquals(1, snapshot.size());
        assertEquals("[\"k1\"]", snapshot.get(1));
    }

    @Test
    @DisplayName("回填只命中题号一致的行；漂移题号不误绑")
    void applyMatchesByNumberOnly() {
        Map<Integer, String> snapshot = Map.of(2, "[\"a\",\"b\"]");
        List<ExamQuestion> split =
                new ArrayList<>(
                        Arrays.asList(
                                q(1, null), // 快照无 → 保持 null
                                q(2, null), // 命中 → 回填
                                q(4, null))); // 原第2题漂到第4，题号不再命中 → 不误绑
        int preserved = ExamQuestionSplitSupport.applyImageSnapshot(split, snapshot);
        assertEquals(1, preserved);
        assertNull(split.get(0).getImagesJson());
        assertEquals("[\"a\",\"b\"]", split.get(1).getImagesJson());
        assertNull(split.get(2).getImagesJson());
    }

    @Test
    @DisplayName("空快照快速返回 0 且不改行")
    void emptySnapshotNoOp() {
        List<ExamQuestion> split = new ArrayList<>(Arrays.asList(q(1, null), q(2, null)));
        int preserved = ExamQuestionSplitSupport.applyImageSnapshot(split, Map.of());
        assertEquals(0, preserved);
        assertNull(split.get(0).getImagesJson());
    }
}
