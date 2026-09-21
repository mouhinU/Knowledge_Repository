package com.mouhin.knowledge.repository.infrastructure.observability;

import com.mouhin.knowledge.repository.domain.gateway.ExamAlertGateway;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExamAlertType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 考试链路告警网关实现（2-F，基础设施层）。
 *
 * <p>双通道落地，二者自洽、互不依赖外部系统：
 *
 * <ol>
 *   <li><b>指标</b>：以 {@code knowledge.exam.alert} 计数器 + {@code type} 标签上报 Micrometer， 随 actuator 暴露于
 *       {@code /actuator/metrics}，供时序库 / 看板采集与阈值告警。
 *   <li><b>结构化日志</b>：输出带固定标记 {@code EXAM_ALERT} 的 WARN 行，供 ELK / 日志告警按关键字抓取。
 * </ol>
 *
 * {@link MeterRegistry} 由 actuator 自动装配提供；缺失时降级为仅日志，绝不影响主流程。
 *
 * @author Knowledge-Repository
 * @date 2026-09-18
 */
@Component
@Slf4j
public class ExamAlertGatewayImpl implements ExamAlertGateway {

    /** 指标名：考试告警计数 */
    private static final String METRIC_NAME = "knowledge.exam.alert";

    /** 指标标签键：告警类型 */
    private static final String TAG_TYPE = "type";

    /** 结构化日志标记，供日志告警规则关键字匹配 */
    private static final String LOG_MARKER = "EXAM_ALERT";

    private final MeterRegistry meterRegistry;

    public ExamAlertGatewayImpl(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void validationFailed(String paperSessionKey, int issueCount) {
        alert(ExamAlertType.VALIDATION_FAILED, paperSessionKey, "issues=" + issueCount);
    }

    @Override
    public void lowQualityScore(String paperSessionKey, int quality, int threshold) {
        alert(
                ExamAlertType.LOW_QUALITY,
                paperSessionKey,
                "quality=" + quality + ",threshold=" + threshold);
    }

    @Override
    public void answerKeyMissing(Long sessionId, Integer questionNumber) {
        alert(
                ExamAlertType.ANSWER_KEY_MISSING,
                String.valueOf(sessionId),
                "questionNumber=" + questionNumber);
    }

    @Override
    public void gradingTimeout(Long sessionId) {
        alert(ExamAlertType.GRADING_TIMEOUT, String.valueOf(sessionId), null);
    }

    @Override
    public void alert(ExamAlertType type, String objectId, String detail) {
        try {
            if (meterRegistry != null) {
                Counter.builder(METRIC_NAME)
                        .tag(TAG_TYPE, type.getTag())
                        .description("考试链路需人工 / 运维关注的告警计数")
                        .register(meterRegistry)
                        .increment();
            }
        } catch (Exception e) {
            // 指标上报失败绝不影响主流程
            log.debug("考试告警指标上报失败 [type={}]: {}", type, e.getMessage());
        }
        log.warn(
                "{} type={} objectId={} detail={} desc={}",
                LOG_MARKER,
                type.getTag(),
                objectId,
                detail == null ? "" : detail,
                type.getDescription());
    }
}
