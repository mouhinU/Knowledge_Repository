#!/bin/bash
# Knowledge Repository 限流配置管理脚本
# 用法: ./ratelimit.sh [status|set|get|reset]

cd "$(dirname "$0")/.." || exit 1

PORT=8091
API_BASE="http://localhost:$PORT/api/admin/ratelimit"

# 检查服务
if ! lsof -i :$PORT -t >/dev/null 2>&1; then
    echo "服务未运行，请先启动服务"
    exit 1
fi

ACTION=${1:-status}

case "$ACTION" in
    status)
        echo "=== 限流配置 ==="
        CONFIG=$(curl -sf "$API_BASE/config" 2>/dev/null)
        if [ -n "$CONFIG" ]; then
            echo "$CONFIG" | python3 -m json.tool 2>/dev/null || echo "$CONFIG"
        else
            echo "获取限流配置失败"
        fi
        echo ""
        echo "=== 当前统计 ==="
        STATS=$(curl -sf "$API_BASE/stats" 2>/dev/null)
        if [ -n "$STATS" ]; then
            echo "$STATS" | python3 -m json.tool 2>/dev/null || echo "$STATS"
        fi
        ;;

    set)
        if [ -z "$2" ] || [ -z "$3" ]; then
            echo "用法: $0 set <接口> <限流值>"
            echo ""
            echo "示例:"
            echo "  $0 set '/api/knowledge/search' '100/h'"
            echo "  $0 set '/api/document/upload' '10/m'"
            echo ""
            echo "限流格式: <数量>/<时间单位>"
            echo "  s = 秒, m = 分, h = 时, d = 天"
            exit 1
        fi
        ENDPOINT=$2
        LIMIT=$3
        
        echo "设置限流: $ENDPOINT = $LIMIT"
        RESULT=$(curl -sf -X PUT "$API_BASE/config" \
            -H "Content-Type: application/json" \
            -d "{\"endpoint\":\"$ENDPOINT\",\"limit\":\"$LIMIT\"}" 2>/dev/null)
        
        if [ -n "$RESULT" ]; then
            echo "设置成功"
        else
            echo "设置失败"
        fi
        ;;

    get)
        if [ -z "$2" ]; then
            echo "用法: $0 get <接口>"
            exit 1
        fi
        ENDPOINT=$2
        
        LIMIT=$(curl -sf "$API_BASE/config/$ENDPOINT" 2>/dev/null)
        if [ -n "$LIMIT" ]; then
            echo "$ENDPOINT = $LIMIT"
        else
            echo "未配置限流: $ENDPOINT"
        fi
        ;;

    reset)
        if [ -z "$2" ]; then
            echo "用法: $0 reset <接口>"
            echo "  或: $0 reset all  (重置所有)"
            exit 1
        fi
        ENDPOINT=$2
        
        if [ "$ENDPOINT" = "all" ]; then
            read -p "确认重置所有限流配置? (y/N): " CONFIRM
            if [ "$CONFIRM" != "y" ]; then
                echo "已取消"
                exit 0
            fi
            RESULT=$(curl -sf -X DELETE "$API_BASE/config/all" 2>/dev/null)
        else
            RESULT=$(curl -sf -X DELETE "$API_BASE/config/$ENDPOINT" 2>/dev/null)
        fi
        
        if [ -n "$RESULT" ]; then
            echo "重置完成"
        else
            echo "重置失败"
        fi
        ;;

    blocked)
        echo "=== 被限流的请求 ==="
        LOGS=$(curl -sf "$API_BASE/blocked" 2>/dev/null)
        if [ -n "$LOGS" ]; then
            echo "$LOGS" | python3 -m json.tool 2>/dev/null || echo "$LOGS"
        else
            echo "无被限流记录"
        fi
        ;;

    *)
        echo "用法: $0 [status|set|get|reset|blocked]"
        echo ""
        echo "  status            - 查看限流状态"
        echo "  set <接口> <限流>  - 设置限流规则"
        echo "  get <接口>        - 查看接口限流"
        echo "  reset <接口|all>  - 重置限流配置"
        echo "  blocked           - 查看被限流的请求"
        exit 1
        ;;
esac
