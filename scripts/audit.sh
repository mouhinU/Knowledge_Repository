#!/bin/bash
# Knowledge Repository 审计日志查看脚本
# 用法: ./audit.sh [list|search|export|clean]

cd "$(dirname "$0")/.." || exit 1

PORT=8091
API_BASE="http://localhost:$PORT/api/admin/audit"

# 检查服务
if ! lsof -i :$PORT -t >/dev/null 2>&1; then
    echo "服务未运行，请先启动服务"
    exit 1
fi

ACTION=${1:-list}

case "$ACTION" in
    list)
        echo "=== 审计日志 (最近 50 条) ==="
        LIMIT=${2:-50}
        
        LOGS=$(curl -sf "$API_BASE/list?limit=$LIMIT" 2>/dev/null)
        if [ -n "$LOGS" ]; then
            echo "$LOGS" | python3 -m json.tool 2>/dev/null || echo "$LOGS"
        else
            echo "获取审计日志失败"
        fi
        ;;

    search)
        if [ -z "$2" ]; then
            echo "用法: $0 search <关键词>"
            echo ""
            echo "搜索范围: 用户、操作类型、资源"
            exit 1
        fi
        KEYWORD=$2
        
        echo "=== 搜索: $KEYWORD ==="
        LOGS=$(curl -sf "$API_BASE/search?keyword=$KEYWORD" 2>/dev/null)
        if [ -n "$LOGS" ]; then
            echo "$LOGS" | python3 -m json.tool 2>/dev/null || echo "$LOGS"
        else
            echo "未找到匹配记录"
        fi
        ;;

    user)
        if [ -z "$2" ]; then
            echo "用法: $0 user <用户名>"
            exit 1
        fi
        USERNAME=$2
        
        echo "=== 用户 $USERNAME 的操作记录 ==="
        LOGS=$(curl -sf "$API_BASE/user/$USERNAME" 2>/dev/null)
        if [ -n "$LOGS" ]; then
            echo "$LOGS" | python3 -m json.tool 2>/dev/null || echo "$LOGS"
        else
            echo "获取用户日志失败"
        fi
        ;;

    export)
        OUTPUT_DIR="./data/exports"
        mkdir -p "$OUTPUT_DIR"
        
        TIMESTAMP=$(date +%Y%m%d_%H%M%S)
        OUTPUT_FILE="$OUTPUT_DIR/audit_$TIMESTAMP.json"
        
        echo "导出审计日志..."
        LOGS=$(curl -sf "$API_BASE/list?limit=10000" 2>/dev/null)
        
        if [ -n "$LOGS" ]; then
            echo "$LOGS" > "$OUTPUT_FILE"
            COUNT=$(echo "$LOGS" | grep -o '"id"' | wc -l | tr -d ' ')
            echo "导出完成: $OUTPUT_FILE ($COUNT 条记录)"
        else
            echo "导出失败"
        fi
        ;;

    clean)
        DAYS=${2:-90}
        
        echo "清理 $DAYS 天前的审计日志..."
        read -p "确认要清理吗? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        RESULT=$(curl -sf -X DELETE "$API_BASE/clean?days=$DAYS" 2>/dev/null)
        if [ -n "$RESULT" ]; then
            echo "清理完成"
        else
            echo "清理失败"
        fi
        ;;

    stats)
        echo "=== 审计日志统计 ==="
        STATS=$(curl -sf "$API_BASE/stats" 2>/dev/null)
        if [ -n "$STATS" ]; then
            echo "$STATS" | python3 -m json.tool 2>/dev/null || echo "$STATS"
        else
            echo "获取统计失败"
        fi
        ;;

    *)
        echo "用法: $0 [list|search|user|export|clean|stats]"
        echo ""
        echo "  list [数量]        - 查看最近日志 (默认50条)"
        echo "  search <关键词>    - 搜索日志"
        echo "  user <用户名>      - 查看用户操作"
        echo "  export             - 导出全部日志"
        echo "  clean [天数]       - 清理旧日志 (默认90天)"
        echo "  stats              - 统计信息"
        exit 1
        ;;
esac
