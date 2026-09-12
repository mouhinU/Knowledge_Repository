#!/bin/bash
# Knowledge Repository 缓存管理脚本
# 用法: ./cache.sh [status|clear|keys|stats]

cd "$(dirname "$0")/.." || exit 1

PORT=8091
API_BASE="http://localhost:$PORT/api/admin/cache"

# 检查服务
if ! lsof -i :$PORT -t >/dev/null 2>&1; then
    echo "服务未运行，请先启动服务"
    exit 1
fi

ACTION=${1:-status}

case "$ACTION" in
    status)
        echo "=== 缓存状态 ==="
        STATUS=$(curl -sf "$API_BASE/status" 2>/dev/null)
        if [ -n "$STATUS" ]; then
            echo "$STATUS" | python3 -m json.tool 2>/dev/null || echo "$STATUS"
        else
            echo "获取缓存状态失败"
        fi
        ;;

    clear)
        CACHE_NAME=${2:-all}
        
        if [ "$CACHE_NAME" = "all" ]; then
            read -p "确认清除所有缓存? (y/N): " CONFIRM
            if [ "$CONFIRM" != "y" ]; then
                echo "已取消"
                exit 0
            fi
            echo "清除所有缓存..."
            RESULT=$(curl -sf -X DELETE "$API_BASE/all" 2>/dev/null)
        else
            echo "清除缓存: $CACHE_NAME"
            RESULT=$(curl -sf -X DELETE "$API_BASE/$CACHE_NAME" 2>/dev/null)
        fi
        
        if [ -n "$RESULT" ]; then
            echo "清除完成"
        else
            echo "清除失败"
        fi
        ;;

    keys)
        CACHE_NAME=${2:-}
        
        if [ -z "$CACHE_NAME" ]; then
            echo "用法: $0 keys <缓存名称>"
            echo ""
            echo "示例: $0 keys documents"
            exit 1
        fi
        
        echo "=== 缓存 $CACHE_NAME 的键 ==="
        KEYS=$(curl -sf "$API_BASE/$CACHE_NAME/keys" 2>/dev/null)
        if [ -n "$KEYS" ]; then
            echo "$KEYS" | python3 -m json.tool 2>/dev/null || echo "$KEYS"
        else
            echo "获取缓存键失败"
        fi
        ;;

    stats)
        echo "=== 缓存统计 ==="
        STATS=$(curl -sf "$API_BASE/stats" 2>/dev/null)
        if [ -n "$STATS" ]; then
            echo "$STATS" | python3 -m json.tool 2>/dev/null || echo "$STATS"
        else
            echo "获取统计失败"
        fi
        ;;

    warm)
        CACHE_NAME=${2:-}
        
        if [ -z "$CACHE_NAME" ]; then
            echo "用法: $0 warm <缓存名称>"
            echo ""
            echo "预热缓存，提前加载数据到内存"
            exit 1
        fi
        
        echo "预热缓存: $CACHE_NAME"
        RESULT=$(curl -sf -X POST "$API_BASE/$CACHE_NAME/warm" 2>/dev/null)
        
        if [ -n "$RESULT" ]; then
            echo "预热完成"
        else
            echo "预热失败"
        fi
        ;;

    *)
        echo "用法: $0 [status|clear|keys|stats|warm]"
        echo ""
        echo "  status            - 查看缓存状态"
        echo "  clear [名称|all]  - 清除缓存 (默认全部)"
        echo "  keys <名称>       - 查看缓存键"
        echo "  stats             - 查看统计信息"
        echo "  warm <名称>       - 预热缓存"
        exit 1
        ;;
esac
