#!/bin/bash
# Knowledge Repository 异步任务管理脚本
# 用法: ./task.sh [list|status|cancel|retry|clean]

cd "$(dirname "$0")/.." || exit 1

PORT=8091
API_BASE="http://localhost:$PORT/api/admin/task"

# 检查服务
if ! lsof -i :$PORT -t >/dev/null 2>&1; then
    echo "服务未运行，请先启动服务"
    exit 1
fi

ACTION=${1:-list}

case "$ACTION" in
    list)
        echo "=== 任务列表 ==="
        STATUS=${2:-}
        
        if [ -n "$STATUS" ]; then
            TASKS=$(curl -sf "$API_BASE/list?status=$STATUS" 2>/dev/null)
        else
            TASKS=$(curl -sf "$API_BASE/list" 2>/dev/null)
        fi
        
        if [ -n "$TASKS" ]; then
            echo "$TASKS" | python3 -m json.tool 2>/dev/null || echo "$TASKS"
        else
            echo "获取任务列表失败"
        fi
        ;;

    status)
        if [ -z "$2" ]; then
            echo "用法: $0 status <任务ID>"
            exit 1
        fi
        TASK_ID=$2
        
        INFO=$(curl -sf "$API_BASE/$TASK_ID" 2>/dev/null)
        if [ -n "$INFO" ]; then
            echo "$INFO" | python3 -m json.tool 2>/dev/null || echo "$INFO"
        else
            echo "任务不存在"
        fi
        ;;

    cancel)
        if [ -z "$2" ]; then
            echo "用法: $0 cancel <任务ID>"
            exit 1
        fi
        TASK_ID=$2
        
        read -p "确认取消任务 $TASK_ID? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        RESULT=$(curl -sf -X POST "$API_BASE/$TASK_ID/cancel" 2>/dev/null)
        if [ -n "$RESULT" ]; then
            echo "任务已取消"
        else
            echo "取消失败"
        fi
        ;;

    retry)
        if [ -z "$2" ]; then
            echo "用法: $0 retry <任务ID>"
            exit 1
        fi
        TASK_ID=$2
        
        echo "重试任务: $TASK_ID"
        RESULT=$(curl -sf -X POST "$API_BASE/$TASK_ID/retry" 2>/dev/null)
        
        if [ -n "$RESULT" ]; then
            echo "任务已重新提交"
        else
            echo "重试失败"
        fi
        ;;

    clean)
        DAYS=${2:-7}
        
        echo "清理 $DAYS 天前的已完成任务..."
        read -p "确认清理? (y/N): " CONFIRM
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
        echo "=== 任务统计 ==="
        STATS=$(curl -sf "$API_BASE/stats" 2>/dev/null)
        if [ -n "$STATS" ]; then
            echo "$STATS" | python3 -m json.tool 2>/dev/null || echo "$STATS"
        else
            echo "获取统计失败"
        fi
        ;;

    queue)
        echo "=== 任务队列 ==="
        QUEUE=$(curl -sf "$API_BASE/queue" 2>/dev/null)
        if [ -n "$QUEUE" ]; then
            echo "$QUEUE" | python3 -m json.tool 2>/dev/null || echo "$QUEUE"
        else
            echo "获取队列失败"
        fi
        ;;

    *)
        echo "用法: $0 [list|status|cancel|retry|clean|stats|queue]"
        echo ""
        echo "  list [状态]        - 列出任务 (PENDING/RUNNING/DONE/FAILED)"
        echo "  status <ID>        - 查看任务状态"
        echo "  cancel <ID>        - 取消任务"
        echo "  retry <ID>         - 重试失败任务"
        echo "  clean [天数]       - 清理旧任务 (默认7天)"
        echo "  stats              - 查看统计"
        echo "  queue              - 查看任务队列"
        exit 1
        ;;
esac
