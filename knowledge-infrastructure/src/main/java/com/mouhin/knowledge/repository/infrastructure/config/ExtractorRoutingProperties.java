package com.mouhin.knowledge.repository.infrastructure.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 解析策略路由配置（{@code knowledge.extractor.*}）。
 *
 * <p>供 {@code CompositeExtractionService} 按 MIME 类型解析出候选策略栈。三项均为可选：
 *
 * <ul>
 *   <li>{@code enabled-strategies}：白名单，为空表示全部策略启用；
 *   <li>{@code routing}：MIME → 有序策略名列表，未配置则回退 Composite 内置映射；
 *   <li>{@code default-stack}：MIME 未命中任何 routing / 内置映射时的兜底栈，默认 {@code [TIKA_FALLBACK]}。
 * </ul>
 *
 * <p>Phase B/C 起新增的 {@code vision} 等子节点本类暂不绑定（默认忽略未知字段）。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "knowledge.extractor")
public class ExtractorRoutingProperties {

    /** 启用的策略名白名单（对应 {@code ExtractionStrategyEnum}）；为空表示全部启用。 */
    private List<String> enabledStrategies = new ArrayList<>();

    /** MIME 类型 → 有序策略名栈；为空则使用 Composite 内置映射。 */
    private Map<String, List<String>> routing = new LinkedHashMap<>();

    /** 未命中任何映射时的兜底策略栈。 */
    private List<String> defaultStack = new ArrayList<>(List.of("TIKA_FALLBACK"));
}
