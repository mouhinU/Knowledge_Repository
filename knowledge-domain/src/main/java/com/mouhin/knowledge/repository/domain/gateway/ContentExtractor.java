package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionCandidate;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import java.io.IOException;

/**
 * 单一文档族的解析策略 SPI。
 *
 * <p>遵循 COLA 依赖倒置：接口定义在 domain，实现落在 {@code infrastructure/extractor}。 {@code
 * CompositeExtractionService} 按 {@link #priority()} 升序遍历所有注入的实现，命中第一个 {@link
 * #supports(ExtractionCandidate)} 为 {@code true} 的执行；执行抛 {@link IOException} 时按栈顺序回退到下一策略。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
public interface ContentExtractor {

    /**
     * 策略唯一标识，与 {@link
     * com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionStrategyEnum} 对齐。
     *
     * @return 策略名
     */
    String name();

    /**
     * 路由优先级，数字越小越优先。Composite 据此排序以保证路由稳定。
     *
     * @return 优先级
     */
    int priority();

    /**
     * 路由谓词：候选命中本策略返回 {@code true}。实现不得有任何副作用。
     *
     * @param candidate 解析候选
     * @return 是否支持
     */
    boolean supports(ExtractionCandidate candidate);

    /**
     * 执行提取。异常向 Composite 冒泡，由 Composite 决定回退。
     *
     * @param candidate 解析候选
     * @return 提取结果
     * @throws IOException 读取或解析失败
     */
    ExtractionResult extract(ExtractionCandidate candidate) throws IOException;
}
