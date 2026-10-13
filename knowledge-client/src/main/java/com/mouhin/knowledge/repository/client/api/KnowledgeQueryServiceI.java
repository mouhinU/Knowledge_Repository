package com.mouhin.knowledge.repository.client.api;

import com.mouhin.knowledge.repository.client.dto.SearchCmd;
import com.mouhin.knowledge.repository.client.dto.SearchResponseVO;

/**
 * 知识库查询应用服务契约（client 层）
 *
 * @author mouhinU
 * @date 2026-09-17
 */
public interface KnowledgeQueryServiceI {

    /**
     * 语义检索知识库（权限过滤 + 分类过滤 + 文档名补全）。
     *
     * @param cmd 检索命令，包含查询文本、过滤条件与分页参数
     * @return 检索响应，包含匹配的文档片段列表与相关元数据
     */
    SearchResponseVO search(SearchCmd cmd);
}
