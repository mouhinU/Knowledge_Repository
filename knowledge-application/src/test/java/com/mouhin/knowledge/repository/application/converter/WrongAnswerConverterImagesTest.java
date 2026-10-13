package com.mouhin.knowledge.repository.application.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mouhin.knowledge.repository.client.dto.WrongAnswerVO;
import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 错题转换器配图字段映射单测（阶段 4）。
 *
 * <p>锁定错题本（学生 / 教师端共用本转换器）对看图题配图的取数口径：从结构化题目行 {@code kb_exam_question.images_json} 解析为 {@code
 * images} assetKey 数组；无绑定行或 脏数据归一化为 {@code null}（配合 {@code @JsonInclude} 省略字段）。纯函数测试，确定且离线。
 *
 * @author mouhinU
 * @date 2026-09-20
 */
@DisplayName("错题转换器映射看图题配图 (WrongAnswerConverter)")
class WrongAnswerConverterImagesTest {

    private static ExamAnswer answer() {
        ExamAnswer a = new ExamAnswer();
        a.setId(1L);
        a.setSessionId(10L);
        a.setQuestionIndex(32);
        a.setQuestionType("ESSAY");
        a.setQuestionContent("看图写话");
        a.setMaxScore(10);
        a.setCorrect(Boolean.FALSE);
        return a;
    }

    private static ExamSession session() {
        ExamSession s = new ExamSession();
        s.setSessionKey("sess-1");
        s.setTopic("语文期末");
        s.setStudentId(7L);
        return s;
    }

    private static ExamQuestion question(String imagesJson) {
        ExamQuestion q = new ExamQuestion();
        q.setQuestionNumber(32);
        q.setImagesJson(imagesJson);
        return q;
    }

    @Test
    @DisplayName("题目有配图：images 解析为 assetKey 数组")
    void mapsImagesFromQuestion() {
        WrongAnswerVO vo =
                WrongAnswerConverter.toVO(answer(), session(), "张三", question("[\"a1\",\"b2\"]"));
        assertEquals(List.of("a1", "b2"), vo.getImages());
    }

    @Test
    @DisplayName("题目无配图（空数组）：images 归一化为 null")
    void emptyBindingsBecomeNull() {
        WrongAnswerVO vo = WrongAnswerConverter.toVO(answer(), session(), "张三", question("[]"));
        assertNull(vo.getImages());
    }

    @Test
    @DisplayName("结构化题目行缺失：images 为 null 不抛异常")
    void nullQuestionYieldsNullImages() {
        WrongAnswerVO vo = WrongAnswerConverter.toVO(answer(), session(), "张三", null);
        assertNull(vo.getImages());
    }
}
