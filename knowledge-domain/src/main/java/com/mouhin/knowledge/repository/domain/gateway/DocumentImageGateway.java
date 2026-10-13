package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.DocumentImage;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentImageHit;
import java.util.List;
import java.util.Optional;

/**
 * 文档图片仓储接口（领域层）。
 *
 * <p>出入参均为领域对象，禁止出现 DO / DTO。实现类位于基础设施层。
 *
 * @author mouhinU
 * @date 2026-09-20
 */
public interface DocumentImageGateway {

    /**
     * 保存单条图片记录（回填自增主键到入参实体）。
     *
     * @param image 文档图片实体
     */
    void save(DocumentImage image);

    /**
     * 根据对外访问句柄查找图片。
     *
     * @param assetKey 图片访问句柄
     * @return 命中的图片；不存在时 {@link Optional#empty()}
     */
    Optional<DocumentImage> findByAssetKey(String assetKey);

    /**
     * 列出某文档的全部图片，按页码、页内序号升序。
     *
     * @param documentId 文档主键
     * @return 该文档下的图片列表
     */
    List<DocumentImage> listByDocumentId(Long documentId);

    /**
     * 全局图片检索（校对页选图）。
     *
     * <p>可按 {@code documentKey} 限定到某一篇文档，或按 {@code keyword} 匹配来源文档文件名 / 文档
     * Key；二者皆空则按最新入库顺序返回全部配图。结果携带来源文档名，按配图 id 倒序分页。
     *
     * @param keyword 关键词（匹配来源文档名 / documentKey，可空）
     * @param documentKey 限定来源文档 Key（可空，非空时忽略 keyword）
     * @param limit 返回上限
     * @param offset 偏移
     * @return 检索命中项列表
     */
    List<DocumentImageHit> search(String keyword, String documentKey, int limit, int offset);

    /**
     * 统计 {@link #search} 同条件下的总命中数。
     *
     * @param keyword 关键词（匹配来源文档名 / documentKey，可空）
     * @param documentKey 限定来源文档 Key（可空，非空时忽略 keyword）
     * @return 总命中数
     */
    long countSearch(String keyword, String documentKey);

    /**
     * 判断某文档内是否已存在指定 SHA-256 的图片（文档内去重）。
     *
     * @param documentId 文档主键
     * @param sha256 图片 SHA-256 摘要
     * @return true=已存在，false=不存在
     */
    boolean existsByDocumentIdAndSha256(Long documentId, String sha256);

    /**
     * 统计某文档图片数量。
     *
     * @param documentId 文档主键
     * @return 图片数量
     */
    long countByDocumentId(Long documentId);

    /**
     * 删除某文档的全部图片记录（文档删除 / 重新回填前的清理，不含磁盘文件清理）。
     *
     * @param documentId 文档主键
     */
    void deleteByDocumentId(Long documentId);
}
