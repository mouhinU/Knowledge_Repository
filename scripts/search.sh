#!/bin/bash
# Knowledge Repository 搜索管理脚本
# 用法: ./search.sh [query|reindex|stats|optimize]

cd "$(dirname "$0")/.." || exit 1

PORT=8091
API_BASE="http://localhost:$PORT/api"

# 检查服务
if ! lsof -i :$PORT -t >/dev/null 2>&1; then
    echo "服务未运行，请先启动服务"
    exit 1
fi

ACTION=${1:-query}

case "$ACTION" in
    query|q)
        if [ -z "$2" ]; then
            echo "用法: $0 query <关键词> [结果数量]"
            echo ""
            echo "示例:"
            echo "  $0 query '机器学习'"
            echo "  $0 query 'API文档' 10"
            exit 1
        fi
        QUERY=$2
        TOP_K=${3:-5}
        
        echo "=== 搜索: $QUERY ==="
        echo ""
        
        RESULT=$(curl -sf -X POST "$API_BASE/knowledge/search" \
            -H "Content-Type: application/json" \
            -d "{\"query\":\"$QUERY\",\"topK\":$TOP_K}" 2>/dev/null)
        
        if [ -n "$RESULT" ]; then
            echo "$RESULT" | python3 -m json.tool 2>/dev/null || echo "$RESULT"
        else
            echo "搜索失败"
        fi
        ;;

    reindex)
        if [ -z "$2" ]; then
            echo "用法: $0 reindex <documentKey|all>"
            echo ""
            echo "示例:"
            echo "  $0 reindex doc_abc123"
            echo "  $0 reindex all"
            exit 1
        fi
        TARGET=$2
        
        if [ "$TARGET" = "all" ]; then
            read -p "确认重新索引所有文档? (y/N): " CONFIRM
            if [ "$CONFIRM" != "y" ]; then
                echo "已取消"
                exit 0
            fi
            
            echo "获取文档列表..."
            DOCS=$(curl -sf "$API_BASE/admin/document/list" 2>/dev/null)
            
            if [ -z "$DOCS" ]; then
                echo "获取文档列表失败"
                exit 1
            fi
            
            # 提取所有 documentKey
            KEYS=$(echo "$DOCS" | grep -o '"documentKey":"[^"]*"' | cut -d'"' -f4)
            
            COUNT=0
            for KEY in $KEYS; do
                echo "重新索引: $KEY"
                curl -sf -X POST "$API_BASE/admin/document/$KEY/reindex" >/dev/null 2>&1
                ((COUNT++))
            done
            
            echo "已触发 $COUNT 个文档的重新索引"
        else
            echo "重新索引: $TARGET"
            RESULT=$(curl -sf -X POST "$API_BASE/admin/document/$TARGET/reindex" 2>/dev/null)
            
            if [ -n "$RESULT" ]; then
                echo "重新索引已启动"
            else
                echo "重新索引失败"
            fi
        fi
        ;;

    stats)
        echo "=== 搜索统计 ==="
        echo ""
        
        echo "--- 文档统计 ---"
        curl -sf "$API_BASE/admin/document/stats" 2>/dev/null | python3 -m json.tool
        echo ""
        
        echo "--- Milvus 状态 ---"
        if curl -sf "http://localhost:9091/healthz" >/dev/null 2>&1; then
            echo "Milvus: 健康"
        else
            echo "Milvus: 未就绪"
        fi
        ;;

    optimize)
        echo "=== 索引优化 ==="
        echo ""
        
        echo "优化 Milvus 集合..."
        # Milvus 优化操作
        curl -sf -X POST "http://localhost:19530/v1/vector/collections/knowledge_chunks/optimize" 2>/dev/null
        
        echo "完成"
        ;;

    clear)
        echo "=== 清空搜索索引 ==="
        echo ""
        read -p "确认要清空所有向量数据? 此操作不可恢复! (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        echo "清空 Milvus 集合..."
        curl -sf -X DELETE "http://localhost:19530/v1/vector/collections/knowledge_chunks" 2>/dev/null
        
        echo "完成，需要重新索引文档"
        ;;

    *)
        echo "用法: $0 [query|reindex|stats|optimize|clear]"
        echo ""
        echo "  query <关键词> [N]   - 搜索知识库"
        echo "  reindex <key|all>    - 重新索引文档"
        echo "  stats                - 查看搜索统计"
        echo "  optimize             - 优化索引"
        echo "  clear                - 清空索引"
        exit 1
        ;;
esac
