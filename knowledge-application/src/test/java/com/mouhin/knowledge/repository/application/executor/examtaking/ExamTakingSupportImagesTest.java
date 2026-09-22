package com.mouhin.knowledge.repository.application.executor.examtaking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 看图题配图注入开考快照单测（阶段 3）。
 *
 * <p>锁定「按印刷题号把 {@code images_json} 回填进 Markdown 渲染快照」这一口径：题号命中才注入 {@code images}、无绑定不误加、题号漂移不误绑、空 /
 * 异常输入原样返回，且注入不破坏原有字段。 {@code injectImagesIntoSnapshot} 只依赖快照 JSON 与结构化题目行，不触及网关，故以空依赖构造，纯函数测试。
 *
 * @author mouhinU
 * @date 2026-09-20
 */
@DisplayName("看图题配图注入开考快照 (按印刷题号回填 images)")
class ExamTakingSupportImagesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 不触网关注入逻辑，六个协作对象传 null 即可。 */
    private final ExamTakingSupport support =
            new ExamTakingSupport(null, null, null, null, null, null);

    private static ExamQuestion q(Integer number, String imagesJson) {
        ExamQuestion question = new ExamQuestion();
        question.setQuestionNumber(number);
        question.setImagesJson(imagesJson);
        return question;
    }

    private static List<Map<String, Object>> parse(String json) throws Exception {
        return MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
    }

    @Test
    @DisplayName("题号命中：把 assetKey 数组写入对应题 images，保留原字段")
    void injectByPrintedNumber() throws Exception {
        String snapshot =
                "[{\"index\":1,\"number\":1,\"type\":\"SHORT_ANSWER\",\"content\":\"看图写话\",\"maxScore\":10},"
                        + "{\"index\":2,\"number\":2,\"type\":\"SINGLE_CHOICE\",\"content\":\"纯文本题\",\"maxScore\":3}]";
        List<ExamQuestion> rows = Arrays.asList(q(1, "[\"k1\",\"k2\"]"), q(2, null));

        String out = support.injectImagesIntoSnapshot(snapshot, rows);
        List<Map<String, Object>> questions = parse(out);

        @SuppressWarnings("unchecked")
        List<String> images = (List<String>) questions.get(0).get("images");
        assertEquals(Arrays.asList("k1", "k2"), images);
        // 原字段保留
        assertEquals("看图写话", questions.get(0).get("content"));
        assertEquals(10, ((Number) questions.get(0).get("maxScore")).intValue());
        // 无绑定的题不加 images
        assertNull(questions.get(1).get("images"));
    }

    @Test
    @DisplayName("题号漂移：绑定图片不误注入到无关题目")
    void driftDoesNotRebind() throws Exception {
        // 快照题号为 5、6；结构化行仅有题号 7 的绑定
        String snapshot = "[{\"index\":1,\"number\":5},{\"index\":2,\"number\":6}]";
        List<ExamQuestion> rows = List.of(q(7, "[\"orphan\"]"));

        String out = support.injectImagesIntoSnapshot(snapshot, rows);
        // 无命中 → 原样返回，不含 images
        assertFalse(out.contains("images"));
        assertEquals(snapshot, out);
    }

    @Test
    @DisplayName("空 / 异常输入：原样返回快照，绝不抛断开考")
    void nullAndMalformedInputsReturnOriginal() {
        String snapshot = "[{\"number\":1}]";
        assertNull(support.injectImagesIntoSnapshot(null, List.of(q(1, "[\"k\"]"))));
        assertEquals(snapshot, support.injectImagesIntoSnapshot(snapshot, null));
        assertEquals(snapshot, support.injectImagesIntoSnapshot(snapshot, List.of()));
        // imagesJson 脏数据不阻断，仅该题忽略
        String out = support.injectImagesIntoSnapshot(snapshot, List.of(q(1, "not-json")));
        assertFalse(out.contains("images"));
    }

    @Test
    @DisplayName("题号宽松匹配：字符串 number 也能命中并去空白 assetKey")
    void stringNumberAndTrim() throws Exception {
        String snapshot = "[{\"index\":1,\"number\":\"3\"}]";
        List<ExamQuestion> rows = List.of(q(3, "[\" k1 \", \"\"]"));

        String out = support.injectImagesIntoSnapshot(snapshot, rows);
        List<Map<String, Object>> questions = parse(out);
        @SuppressWarnings("unchecked")
        List<String> images = (List<String>) questions.get(0).get("images");
        assertEquals(List.of("k1"), images);
    }

    @Test
    @DisplayName("全部题号均无配图：原样返回避免无谓重序列化")
    void noBindingsReturnOriginal() {
        String snapshot = "[{\"index\":1,\"number\":1}]";
        List<ExamQuestion> rows = List.of(q(1, "[]"), q(2, null));
        String out = support.injectImagesIntoSnapshot(snapshot, rows);
        assertEquals(snapshot, out);
        assertTrue(out.equals(snapshot));
    }
}
