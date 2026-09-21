package com.mouhin.knowledge.repository.client.api;

import com.mouhin.knowledge.repository.client.dto.SearchCmd;
import com.mouhin.knowledge.repository.client.dto.SearchResponseVO;

/**
 * 知识库查询应用服务契约（client 层）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
public interface KnowledgeQueryServiceI {

    /** 语义检索知识库（权限过滤 + 分类过滤 + 文档名补全） */
    SearchResponseVO search(SearchCmd cmd);
}
