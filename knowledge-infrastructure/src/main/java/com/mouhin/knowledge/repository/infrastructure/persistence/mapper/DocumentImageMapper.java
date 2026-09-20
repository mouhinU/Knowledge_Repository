package com.mouhin.knowledge.repository.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.DocumentImageDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.DocumentImageSearchRowDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文档图片 Mapper
 *
 * @author Knowledge-Repository
 * @date 2026-09-20
 */
@Mapper
public interface DocumentImageMapper extends BaseMapper<DocumentImageDO> {

    /**
     * 全局图片检索：联表来源文档带出文件名，支持按 documentKey 限定单篇文档，
     * 或按 keyword 模糊匹配来源文档名 / 文档 Key；均为空则按配图 id 倒序返回全部。
     * <p>SQL 兼容 H2 与 MySQL（LIMIT ? OFFSET ?、CONCAT）。{@code di.*} 经下划线转驼峰
     * 映射到 {@link DocumentImageSearchRowDO}（含联表别名 source_document_name）。</p>
     */
    @Select("""
            <script>
            SELECT di.*, d.file_name AS source_document_name
            FROM kb_document_image di
            LEFT JOIN kb_document d ON di.document_id = d.id
            <where>
                <if test="documentKey != null and documentKey != ''">
                    di.document_key = #{documentKey}
                </if>
                <if test="(documentKey == null or documentKey == '') and keyword != null and keyword != ''">
                    (d.file_name LIKE CONCAT('%', #{keyword}, '%')
                     OR di.document_key LIKE CONCAT('%', #{keyword}, '%'))
                </if>
            </where>
            ORDER BY di.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<DocumentImageSearchRowDO> searchRows(@Param("keyword") String keyword,
                                              @Param("documentKey") String documentKey,
                                              @Param("limit") int limit,
                                              @Param("offset") int offset);

    /**
     * 统计 {@link #searchRows} 同条件下的总命中数。
     */
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM kb_document_image di
            LEFT JOIN kb_document d ON di.document_id = d.id
            <where>
                <if test="documentKey != null and documentKey != ''">
                    di.document_key = #{documentKey}
                </if>
                <if test="(documentKey == null or documentKey == '') and keyword != null and keyword != ''">
                    (d.file_name LIKE CONCAT('%', #{keyword}, '%')
                     OR di.document_key LIKE CONCAT('%', #{keyword}, '%'))
                </if>
            </where>
            </script>
            """)
    long countSearchRows(@Param("keyword") String keyword,
                         @Param("documentKey") String documentKey);
}
