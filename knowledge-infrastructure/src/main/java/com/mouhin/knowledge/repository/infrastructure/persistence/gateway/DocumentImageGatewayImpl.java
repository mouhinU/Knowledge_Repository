package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.knowledge.repository.domain.gateway.DocumentImageGateway;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentImage;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentImageHit;
import com.mouhin.knowledge.repository.infrastructure.persistence.converter.DocumentImageConverter;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.DocumentImageDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.DocumentImageMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 文档图片仓储实现（基础设施层）
 *
 * @author mouhinU
 * @date 2026-09-20
 */
@Repository
public class DocumentImageGatewayImpl implements DocumentImageGateway {

    private final DocumentImageMapper imageMapper;

    public DocumentImageGatewayImpl(DocumentImageMapper imageMapper) {
        this.imageMapper = imageMapper;
    }

    @Override
    public void save(DocumentImage image) {
        LocalDateTime now = LocalDateTime.now();
        if (image.getCreateTime() == null) {
            image.setCreateTime(now);
        }
        image.setUpdateTime(now);
        DocumentImageDO doObj = DocumentImageConverter.toDO(image);
        imageMapper.insert(doObj);
        image.setId(doObj.getId());
    }

    @Override
    public Optional<DocumentImage> findByAssetKey(String assetKey) {
        LambdaQueryWrapper<DocumentImageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentImageDO::getAssetKey, assetKey);
        return Optional.ofNullable(DocumentImageConverter.toDomain(imageMapper.selectOne(wrapper)));
    }

    @Override
    public List<DocumentImage> listByDocumentId(Long documentId) {
        LambdaQueryWrapper<DocumentImageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentImageDO::getDocumentId, documentId);
        wrapper.orderByAsc(DocumentImageDO::getPageNo).orderByAsc(DocumentImageDO::getSeqOnPage);
        return imageMapper.selectList(wrapper).stream()
                .map(DocumentImageConverter::toDomain)
                .toList();
    }

    @Override
    public List<DocumentImageHit> search(
            String keyword, String documentKey, int limit, int offset) {
        return imageMapper.searchRows(keyword, documentKey, limit, offset).stream()
                .map(
                        row ->
                                new DocumentImageHit(
                                        DocumentImageConverter.toDomain(row),
                                        row.getSourceDocumentName()))
                .toList();
    }

    @Override
    public long countSearch(String keyword, String documentKey) {
        return imageMapper.countSearchRows(keyword, documentKey);
    }

    @Override
    public boolean existsByDocumentIdAndSha256(Long documentId, String sha256) {
        LambdaQueryWrapper<DocumentImageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentImageDO::getDocumentId, documentId);
        wrapper.eq(DocumentImageDO::getSha256, sha256);
        return imageMapper.selectCount(wrapper) > 0;
    }

    @Override
    public long countByDocumentId(Long documentId) {
        LambdaQueryWrapper<DocumentImageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentImageDO::getDocumentId, documentId);
        return imageMapper.selectCount(wrapper);
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        LambdaQueryWrapper<DocumentImageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentImageDO::getDocumentId, documentId);
        imageMapper.delete(wrapper);
    }
}
