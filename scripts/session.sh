#!/bin/bash
# Knowledge Repository 会话管理脚本
# 用法: ./session.sh [list|clear|info|kick]

cd "$(dirname "$0")/.." || exit 1

PORT=8091
API_BASE="http://localhost:$PORT/api/admin/session"

# 检查服务
if ! lsof -i :$PORT -t >/dev/null 2>&1; then
    echo "服务未运行，请先启动服务"
    exit 1
fi

ACTION=${1:-list}

case "$ACTION" in
    list)
        echo "=== 活跃会话 ==="
        SESSIONS=$(curl -sf "$API_BASE/list" 2>/dev/null)
        if [ -n "$SESSIONS" ]; then
            echo "$SESSIONS" | python3 -m json.tool 2>/dev/null || echo "$SESSIONS"
        else
            echo "获取会话列表失败"
        fi
        ;;

    clear)
        echo "=== 清理过期会话 ==="
        read -p "确认清理所有过期会话? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        RESULT=$(curl -sf -X DELETE "$API_BASE/expired" 2>/dev/null)
        if [ -n "$RESULT" ]; then
            echo "清理完成"
        else
            echo "清理失败"
        fi
        ;;

    info)
        if [ -z "$2" ]; then
            echo "用法: $0 info <会话ID>"
            exit 1
        fi
        SESSION_ID=$2
        
        INFO=$(curl -sf "$API_BASE/$SESSION_ID" 2>/dev/null)
        if [ -n "$INFO" ]; then
            echo "$INFO" | python3 -m json.tool 2>/dev/null || echo "$INFO"
        else
            echo "会话不存在"
        fi
        ;;

    kick)
        if [ -z "$2" ]; then
            echo "用法: $0 kick <会话ID|用户名>"
            echo ""
            echo "踢出指定会话或用户的所有会话"
            exit 1
        fi
        TARGET=$2
        
        read -p "确认踢出 $TARGET? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        RESULT=$(curl -sf -X DELETE "$API_BASE/$TARGET" 2>/dev/null)
        if [ -n "$RESULT" ]; then
            echo "已踢出"
        else
            echo "踢出失败"
        fi
        ;;

    stats)
        echo "=== 会话统计 ==="
        STATS=$(curl -sf "$API_BASE/stats" 2>/dev/null)
        if [ -n "$STATS" ]; then
            echo "$STATS" | python3 -m json.tool 2>/dev/null || echo "$STATS"
        else
            echo "获取统计失败"
        fi
        ;;

    config)
        echo "=== 会话配置 ==="
        CONFIG=$(curl -sf "$API_BASE/config" 2>/dev/null)
        if [ -n "$CONFIG" ]; then
            echo "$CONFIG" | python3 -m json.tool 2>/dev/null || echo "$CONFIG"
        else
            echo "获取配置失败"
        fi
        ;;

    *)
        echo "用法: $0 [list|clear|info|kick|stats|config]"
        echo ""
        echo "  list              - 列出活跃会话"
        echo "  clear             - 清理过期会话"
        echo "  info <会话ID>      - 查看会话详情"
        echo "  kick <ID/用户>     - 踢出会话"
        echo "  stats             - 查看统计"
        echo "  config            - 查看配置"
        exit 1
        ;;
esac
