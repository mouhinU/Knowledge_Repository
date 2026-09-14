package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.application.service.KnowledgeQueryApplicationService;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import com.mouhin.knowledge.repository.domain.model.valueobject.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 知识库查询控制器
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeQueryController {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeQueryController.class);

    private final KnowledgeQueryApplicationService queryService;

    public KnowledgeQueryController(KnowledgeQueryApplicationService queryService) {
        this.queryService = queryService;
    }

    /**
     * 语义检索知识库
     *
     * @param body 请求体：query, userId, departmentId, roles, admin, maxResults, minScore
     */
    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestBody Map<String, Object> body) {
        String query = (String) body.get("query");
        String userId = (String) body.get("userId");
        String departmentId = (String) body.get("departmentId");
        Object rolesObj = body.get("roles");
        String roles;
        if (rolesObj instanceof List<?> list) {
            roles = String.join(",", list.stream().map(Object::toString).toList());
        } else {
            roles = (String) rolesObj;
        }
        Boolean admin = body.get("admin") != null && (Boolean) body.get("admin");
        Integer maxResults = body.get("maxResults") != null ? ((Number) body.get("maxResults")).intValue() : null;
        Double minScore = body.get("minScore") != null ? ((Number) body.get("minScore")).doubleValue() : null;
        String category = (String) body.get("category");

        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "errorCode", "BAD_REQUEST",
                    "errorMessage", "userId is required"
            ));
        }

        Permission permission = new Permission(userId, departmentId, roles, admin);
        List<SearchResult> results = queryService.search(query, permission, maxResults, minScore, category);

        return ResponseEntity.ok(Map.of(
                "query", query,
                "totalResults", results.size(),
                "results", results.stream().map(r -> Map.of(
                        "text", r.getText(),
                        "documentKey", r.getDocumentId(),
                        "documentName", r.getDocumentName() != null ? r.getDocumentName() : "",
                        "pageNumber", r.getPageNumber() != null ? r.getPageNumber() : 0,
                        "chunkIndex", r.getChunkIndex() != null ? r.getChunkIndex() : 0,
                        "score", Math.round(r.getScore() * 10000.0) / 10000.0,
                        "category", r.getCategory() != null ? r.getCategory() : ""
                )).toList()
        ));
    }

    /**
     * 获取知识库分类列表
     */
    @GetMapping("/categories")
    public ResponseEntity<List<Map<String, Object>>> listCategories() {
        List<Map<String, Object>> categories = List.of(
                Map.of("name", "工作", "sortOrder", 1),
                Map.of("name", "学习", "sortOrder", 2),
                Map.of("name", "休闲", "sortOrder", 3),
                Map.of("name", "其他", "sortOrder", 4)
        );
        return ResponseEntity.ok(categories);
    }
}
