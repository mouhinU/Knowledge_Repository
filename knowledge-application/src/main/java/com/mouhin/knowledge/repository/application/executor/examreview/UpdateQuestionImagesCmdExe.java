package com.mouhin.knowledge.repository.application.executor.examreview;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mouhin.knowledge.repository.domain.gateway.ExamQuestionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 校对页绑定配图命令执行器（app 层用例，事务边界，阶段 2）
 *
 * <p>接收一组有序的配图 assetKey（可空表示清除），做基本清洗（去空白、去重、限量）后序列化为 {@code images_json} 数组，按「试卷标识 + 印刷题号」就地回写
 * {@code kb_exam_question.images_json}， 不改变试卷状态。图片本体经公开的 {@code /api/exam/assets/{assetKey}}
 * 端点由浏览器加载。
 *
 * @author mouhinU
 * @date 2026-09-20
 */
@Component
@Slf4j
public class UpdateQuestionImagesCmdExe {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 单题配图数量上限，防止异常入参撑爆展示。 */
    private static final int MAX_IMAGES = 20;

    private final PaperReviewSupport support;
    private final ExamQuestionGateway examQuestionGateway;

    public UpdateQuestionImagesCmdExe(
            PaperReviewSupport support, ExamQuestionGateway examQuestionGateway) {
        this.support = support;
        this.examQuestionGateway = examQuestionGateway;
    }

    /**
     * 保存某题的配图绑定。
     *
     * @param sessionKey 试卷标识
     * @param questionNumber 印刷题号
     * @param assetKeys 有序 assetKey 列表（可空 / 空集合表示清除绑定）
     * @return 实际持久化的 assetKey 列表（清洗后）
     */
    @Transactional
    public List<String> execute(String sessionKey, Integer questionNumber, List<String> assetKeys) {
        if (questionNumber == null) {
            throw new IllegalArgumentException("题号不能为空");
        }
        ExamHistory history = support.requireHistory(sessionKey);
        List<String> cleaned = sanitize(assetKeys);
        String imagesJson = writeJson(cleaned);
        examQuestionGateway.updateImagesJson(history.getSessionId(), questionNumber, imagesJson);
        log.info(
                "校对页配图绑定 [session={}, number={}, images={}]",
                history.getSessionId(),
                questionNumber,
                cleaned.size());
        return cleaned;
    }

    private List<String> sanitize(List<String> assetKeys) {
        List<String> result = new ArrayList<>();
        if (assetKeys == null) {
            return result;
        }
        for (String key : assetKeys) {
            if (key == null) {
                continue;
            }
            String trimmed = key.trim();
            if (trimmed.isEmpty() || result.contains(trimmed)) {
                continue;
            }
            result.add(trimmed);
            if (result.size() >= MAX_IMAGES) {
                break;
            }
        }
        return result;
    }

    private String writeJson(List<String> assetKeys) {
        if (assetKeys.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(assetKeys);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("配图 assetKey 序列化失败", e);
        }
    }
}
