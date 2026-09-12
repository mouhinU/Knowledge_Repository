#!/bin/bash
# Knowledge Repository 文档管理脚本
# 用法: ./doc.sh [list|info|delete|reindex|search]

cd "$(dirname "$0")/.." || exit 1

PORT=8091
API_BASE="http://localhost:$PORT/api/admin/document"

# 检查服务
if ! lsof -i :$PORT -t >/dev/null 2>&1; then
    echo "服务未运行，请先启动服务"
    exit 1
fi

ACTION=${1:-list}

case "$ACTION" in
    list)
        echo "=== 文档列表 ==="
        STATUS_FILTER=${2:-}
        
        if [ -n "$STATUS_FILTER" ]; then
            DOCS=$(curl -sf "$API_BASE/status/$STATUS_FILTER" 2>/dev/null)
        else
            DOCS=$(curl -sf "$API_BASE/list" 2>/dev/null)
        fi
        
        if [ -n "$DOCS" ]; then
            echo "$DOCS" | python3 -m json.tool 2>/dev/null || echo "$DOCS"
        else
            echo "获取文档列表失败"
        fi
        ;;

    info)
        if [ -z "$2" ]; then
            echo "用法: $0 info <documentKey>"
            exit 1
        fi
        KEY=$2
        
        DOC=$(curl -sf "$API_BASE/$KEY" 2>/dev/null)
        if [ -n "$DOC" ]; then
            echo "$DOC" | python3 -m json.tool 2>/dev/null || echo "$DOC"
        else
            echo "文档不存在: $KEY"
        fi
        ;;

    delete)
        if [ -z "$2" ]; then
            echo "用法: $0 delete <documentKey>"
            exit 1
        fi
        KEY=$2
        
        read -p "确认删除文档 $KEY? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        RESULT=$(curl -sf -X DELETE "$API_BASE/$KEY" 2>/dev/null)
        echo "删除完成"
        ;;

    reindex)
        if [ -z "$2" ]; then
            echo "用法: $0 reindex <documentKey>"
            exit 1
        fi
        KEY=$2
        
        echo "重新索引文档: $KEY"
        RESULT=$(curl -sf -X POST "$API_BASE/$KEY/reindex" 2>/dev/null)
        
        if [ -n "$RESULT" ]; then
            echo "重新索引已启动"
            echo "$RESULT" | python3 -m json.tool 2>/dev/null
        else
            echo "重新索引失败"
        fi
        ;;

    search)
        if [ -z "$2" ]; then
            echo "用法: $0 search <关键词> [数量]"
            exit 1
        fi
        QUERY=$2
        TOP_K=${3:-5}
        
        echo "搜索: $QUERY (返回 $TOP_K 条)"
        echo ""
        
        RESULT=$(curl -sf -X POST "http://localhost:$PORT/api/knowledge/search" \
            -H "Content-Type: application/json" \
            -d "{\"query\":\"$QUERY\",\"topK\":$TOP_K}" 2>/dev/null)
        
        if [ -n "$RESULT" ]; then
            echo "$RESULT" | python3 -m json.tool 2>/dev/null || echo "$RESULT"
        else
            echo "搜索失败"
        fi
        ;;

    stats)
        echo "=== 文档统计 ==="
        STATS=$(curl -sf "$API_BASE/stats" 2>/dev/null)
        if [ -n "$STATS" ]; then
            echo "$STATS" | python3 -m json.tool 2>/dev/null || echo "$STATS"
        else
            echo "获取统计失败"
        fi
        ;;

    *)
        echo "用法: $0 [list|info|delete|reindex|search|stats]"
        echo ""
        echo "  list [状态]        - 列出文档 (可选: INDEXED/FAILED/UPLOADED)"
        echo "  info <key>         - 查看文档详情"
        echo "  delete <key>       - 删除文档"
        echo "  reindex <key>      - 重新索引文档"
        echo "  search <关键词> [N] - 搜索知识库"
        echo "  stats              - 查看统计信息"
        exit 1
        ;;
esac
