package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionCacheKey;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import java.util.Optional;

/**
 * 解析结果持久缓存端口（COLA domain 层，实现在 infrastructure）。
 *
 * <p>相较进程内的 {@code ExtractionCacheHolder}（重启即失效），本缓存<b>跨进程持久</b>：同一文件（按内容摘要）在相同策略 / 模型 / 提示词 /
 * 渲染模式下重复解析时，可直接命中历史结果，省去昂贵的视觉识别往返。仅做存取与失效，不做业务编排。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
public interface ExtractionCacheRepository {

    /**
     * 按复合键查缓存。
     *
     * @param key 缓存键
     * @return 命中则返回历史提取结果，否则 {@link Optional#empty()}
     */
    Optional<ExtractionResult> find(ExtractionCacheKey key);

    /**
     * 写入或更新缓存（按复合键 upsert）。
     *
     * @param key 缓存键
     * @param result 提取结果
     */
    void save(ExtractionCacheKey key, ExtractionResult result);
}
