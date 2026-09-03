package com.mouhin.knowledge.repository.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.repository.DocumentRepository;
import com.mouhin.knowledge.repository.infrastructure.persistence.converter.DocumentConverter;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.DocumentDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.DocumentMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 文档仓储实现
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@Repository
public class DocumentRepositoryImpl implements DocumentRepository {

    private final DocumentMapper documentMapper;

    public DocumentRepositoryImpl(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    @Override
    public Document save(Document document) {
        document.setCreatedTime(LocalDateTime.now());
        document.setUpdatedTime(LocalDateTime.now());
        DocumentDO doObj = DocumentConverter.toDO(document);
        documentMapper.insert(doObj);
        document.setId(doObj.getId());
        return document;
    }

    @Override
    public void update(Document document) {
        document.setUpdatedTime(LocalDateTime.now());
        DocumentDO doObj = DocumentConverter.toDO(document);
        documentMapper.updateById(doObj);
    }

    @Override
    public Optional<Document> findById(Long id) {
        DocumentDO doObj = documentMapper.selectById(id);
        return Optional.ofNullable(DocumentConverter.toDomain(doObj));
    }

    @Override
    public Optional<Document> findByDocumentKey(String documentKey) {
        LambdaQueryWrapper<DocumentDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentDO::getDocumentKey, documentKey);
        DocumentDO doObj = documentMapper.selectOne(wrapper);
        return Optional.ofNullable(DocumentConverter.toDomain(doObj));
    }

    @Override
    public Optional<Document> findByFileChecksum(String fileChecksum) {
        LambdaQueryWrapper<DocumentDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentDO::getFileChecksum, fileChecksum);
        DocumentDO doObj = documentMapper.selectOne(wrapper);
        return Optional.ofNullable(DocumentConverter.toDomain(doObj));
    }

    @Override
    public List<Document> listByStatus(DocumentStatusEnum status) {
        LambdaQueryWrapper<DocumentDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentDO::getStatus, status.name());
        wrapper.orderByDesc(DocumentDO::getCreateTime);
        return documentMapper.selectList(wrapper).stream()
                .map(DocumentConverter::toDomain)
                .toList();
    }

    @Override
    public List<Document> listByOwnerId(String ownerId, int page, int size) {
        LambdaQueryWrapper<DocumentDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentDO::getOwnerId, ownerId);
        wrapper.orderByDesc(DocumentDO::getCreateTime);
        wrapper.last("LIMIT " + size + " OFFSET " + (page - 1) * size);
        return documentMapper.selectList(wrapper).stream()
                .map(DocumentConverter::toDomain)
                .toList();
    }

    @Override
    public List<Document> listByDepartmentId(String departmentId, int page, int size) {
        LambdaQueryWrapper<DocumentDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentDO::getDepartmentId, departmentId);
        wrapper.orderByDesc(DocumentDO::getCreateTime);
        wrapper.last("LIMIT " + size + " OFFSET " + (page - 1) * size);
        return documentMapper.selectList(wrapper).stream()
                .map(DocumentConverter::toDomain)
                .toList();
    }

    @Override
    public long countByStatus(DocumentStatusEnum status) {
        LambdaQueryWrapper<DocumentDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentDO::getStatus, status.name());
        return documentMapper.selectCount(wrapper);
    }

    @Override
    public void deleteById(Long id) {
        documentMapper.deleteById(id);
    }
}
