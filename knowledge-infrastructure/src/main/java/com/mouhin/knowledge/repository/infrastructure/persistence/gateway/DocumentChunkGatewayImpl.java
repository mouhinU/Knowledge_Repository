package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.knowledge.repository.domain.gateway.DocumentChunkGateway;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.infrastructure.persistence.converter.DocumentChunkConverter;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.DocumentChunkDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.DocumentChunkMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 文档分块仓储实现
 *
 * @author mouhinU
 * @date 2026-09-02
 */
@Repository
public class DocumentChunkGatewayImpl implements DocumentChunkGateway {

    private final DocumentChunkMapper chunkMapper;

    public DocumentChunkGatewayImpl(DocumentChunkMapper chunkMapper) {
        this.chunkMapper = chunkMapper;
    }

    @Override
    public void saveBatch(List<DocumentChunk> chunks) {
        for (DocumentChunk chunk : chunks) {
            chunk.setCreatedTime(LocalDateTime.now());
            DocumentChunkDO doObj = DocumentChunkConverter.toDO(chunk);
            chunkMapper.insert(doObj);
            chunk.setId(doObj.getId());
        }
    }

    @Override
    public List<DocumentChunk> listByDocumentId(Long documentId) {
        LambdaQueryWrapper<DocumentChunkDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChunkDO::getDocumentId, documentId);
        wrapper.orderByAsc(DocumentChunkDO::getChunkIndex);
        return chunkMapper.selectList(wrapper).stream()
                .map(DocumentChunkConverter::toDomain)
                .toList();
    }

    @Override
    public Optional<DocumentChunk> findByChunkKey(String chunkKey) {
        LambdaQueryWrapper<DocumentChunkDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChunkDO::getChunkKey, chunkKey);
        DocumentChunkDO doObj = chunkMapper.selectOne(wrapper);
        return Optional.ofNullable(DocumentChunkConverter.toDomain(doObj));
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        LambdaQueryWrapper<DocumentChunkDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChunkDO::getDocumentId, documentId);
        chunkMapper.delete(wrapper);
    }

    @Override
    public long countByDocumentId(Long documentId) {
        LambdaQueryWrapper<DocumentChunkDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChunkDO::getDocumentId, documentId);
        return chunkMapper.selectCount(wrapper);
    }
}
