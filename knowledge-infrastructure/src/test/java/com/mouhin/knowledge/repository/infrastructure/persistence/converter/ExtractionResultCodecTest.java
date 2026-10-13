package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link ExtractionResultCodec} JSON 编解码往返测试（Phase C 持久缓存）。 */
@DisplayName("C 提取结果编解码")
class ExtractionResultCodecTest {

    @Test
    @DisplayName("全字段往返一致")
    void roundTripPreservesAllFields() {
        ExtractionResult original =
                new ExtractionResult(
                        List.of("第一页", "第二页"),
                        2,
                        true,
                        "md5abc",
                        "pdf-hybrid",
                        List.of("warn-1", "warn-2"),
                        false,
                        "标题",
                        "作者");
        String json = ExtractionResultCodec.encode(original);
        ExtractionResult decoded = ExtractionResultCodec.decode(json);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    @DisplayName("可空元数据（title/author=null）往返保持 null")
    void roundTripKeepsNullMetadata() {
        ExtractionResult original =
                new ExtractionResult(
                        List.of(), 0, false, "ck", "empty", List.of(), true, null, null);
        ExtractionResult decoded =
                ExtractionResultCodec.decode(ExtractionResultCodec.encode(original));
        assertThat(decoded.title()).isNull();
        assertThat(decoded.author()).isNull();
        assertThat(decoded.encrypted()).isTrue();
        assertThat(decoded.pageTexts()).isEmpty();
    }
}
