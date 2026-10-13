package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.knowledge.repository.domain.gateway.ExtractionCacheRepository;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionCacheKey;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.infrastructure.persistence.converter.ExtractionResultCodec;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.ExtractionCacheDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.ExtractionCacheMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 解析结果持久缓存仓储实现（Phase C）。
 *
 * <p>以 {@code checksum + strategy + model_name + prompt_hash + render_mode} 复合业务键做 upsert： 命中即还原历史
 * {@link ExtractionResult}，未命中或写入冲突时以最新结果覆盖，保证幂等。
 *
 * <p><b>租户隔离（P4-T3）</b>：本仓储不在业务键里显式拼 {@code tenant_id}；{@code kb_extraction_cache} 已从 {@code
 * KnowledgeTenantLineHandler.IGNORE_TABLES} 移除、V30 已为其加 {@code tenant_id} 列，因此 {@code
 * TenantLineInnerInterceptor} 会自动对 {@link #find}/{@link #save} 的 SELECT / INSERT / UPDATE 追加 {@code
 * tenant_id} 谓词与列值。效果：A 租户写入的缓存，B 租户以相同业务键查询不再命中（修复 G7 跨租户解析缓存泄漏）。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Repository
public class ExtractionCacheRepositoryImpl implements ExtractionCacheRepository {

    private final ExtractionCacheMapper mapper;

    public ExtractionCacheRepositoryImpl(ExtractionCacheMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ExtractionResult> find(ExtractionCacheKey key) {
        ExtractionCacheDO found = mapper.selectOne(byKey(key));
        if (found == null || found.getResultJson() == null) {
            return Optional.empty();
        }
        return Optional.of(ExtractionResultCodec.decode(found.getResultJson()));
    }

    @Override
    public void save(ExtractionCacheKey key, ExtractionResult result) {
        String json = ExtractionResultCodec.encode(result);
        LocalDateTime now = LocalDateTime.now();
        ExtractionCacheDO existing = mapper.selectOne(byKey(key));
        if (existing != null) {
            existing.setResultJson(json);
            existing.setUpdateTime(now);
            mapper.updateById(existing);
            return;
        }
        ExtractionCacheDO entity = new ExtractionCacheDO();
        entity.setChecksum(key.checksum());
        entity.setStrategy(key.strategy());
        entity.setModelName(key.modelName());
        entity.setPromptHash(key.promptHash());
        entity.setRenderMode(key.renderMode());
        entity.setResultJson(json);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        mapper.insert(entity);
    }

    private LambdaQueryWrapper<ExtractionCacheDO> byKey(ExtractionCacheKey key) {
        return new LambdaQueryWrapper<ExtractionCacheDO>()
                .eq(ExtractionCacheDO::getChecksum, key.checksum())
                .eq(ExtractionCacheDO::getStrategy, key.strategy())
                .eq(ExtractionCacheDO::getModelName, key.modelName())
                .eq(ExtractionCacheDO::getPromptHash, key.promptHash())
                .eq(ExtractionCacheDO::getRenderMode, key.renderMode());
    }
}
