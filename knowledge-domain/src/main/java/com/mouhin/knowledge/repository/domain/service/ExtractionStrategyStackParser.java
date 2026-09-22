package com.mouhin.knowledge.repository.domain.service;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionStrategyEnum;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 解析策略栈解析器（领域服务，无副作用）。
 *
 * <p>把外部传入的「策略名 CSV」（如上传 / 重解析接口的 {@code parsingStrategy} 参数）解析为一组<b>合法</b>的 {@link
 * ExtractionStrategyEnum} 名称：逐个 trim、大写归一、按枚举 {@values()} 白名单校验，丢弃非法值，保序去重。 空串 / {@code auto} / 全非法
 * → 返回空列表，语义为「回退配置默认路由」，调用方据此不施加任何强制栈。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
public final class ExtractionStrategyStackParser {

    /** 表示"交由配置默认路由"的取值。 */
    public static final String AUTO = "auto";

    private static final Set<String> KNOWN =
            Arrays.stream(ExtractionStrategyEnum.values())
                    .map(Enum::name)
                    .collect(Collectors.toUnmodifiableSet());

    private ExtractionStrategyStackParser() {}

    /**
     * 解析策略名 CSV 为合法强制栈（保序去重）。
     *
     * @param csv 逗号分隔的策略名，可为 {@code null} / 空 / {@code auto}
     * @return 合法的 {@link ExtractionStrategyEnum} 名称列表；无合法项时为空列表（回退配置默认）
     */
    public static List<String> parse(String csv) {
        if (csv == null || csv.isBlank() || AUTO.equalsIgnoreCase(csv.trim())) {
            return List.of();
        }
        Set<String> ordered = new LinkedHashSet<>();
        for (String token : csv.split(",")) {
            String name = token.trim().toUpperCase();
            if (!name.isEmpty() && KNOWN.contains(name)) {
                ordered.add(name);
            }
        }
        return new ArrayList<>(ordered);
    }

    /**
     * 判断单个策略名是否为白名单合法值。
     *
     * @param name 策略名（大小写不敏感）
     * @return 合法返回 true
     */
    public static boolean isKnown(String name) {
        return name != null && KNOWN.contains(name.trim().toUpperCase());
    }
}
