#!/bin/bash
# Knowledge Repository 定时任务管理脚本
# 用法: ./schedule.sh [list|add|remove|run]

cd "$(dirname "$0")/.." || exit 1

PORT=8091
API_BASE="http://localhost:$PORT/api/admin/schedule"

# 检查服务
if ! lsof -i :$PORT -t >/dev/null 2>&1; then
    echo "服务未运行，请先启动服务"
    exit 1
fi

ACTION=${1:-list}

case "$ACTION" in
    list)
        echo "=== 定时任务列表 ==="
        TASKS=$(curl -sf "$API_BASE/list" 2>/dev/null)
        if [ -n "$TASKS" ]; then
            echo "$TASKS" | python3 -m json.tool 2>/dev/null || echo "$TASKS"
        else
            echo "获取任务列表失败"
        fi
        ;;

    add)
        if [ -z "$2" ] || [ -z "$3" ]; then
            echo "用法: $0 add <任务名称> <cron表达式>"
            echo ""
            echo "示例:"
            echo "  $0 add '每日备份' '0 2 * * *'"
            echo "  $0 add '每小时清理' '0 * * * *'"
            exit 1
        fi
        NAME=$2
        CRON=$3
        
        echo "创建定时任务: $NAME ($CRON)"
        RESULT=$(curl -sf -X POST "$API_BASE" \
            -H "Content-Type: application/json" \
            -d "{\"name\":\"$NAME\",\"cron\":\"$CRON\"}" 2>/dev/null)
        
        if [ -n "$RESULT" ]; then
            echo "创建成功"
            echo "$RESULT" | python3 -m json.tool 2>/dev/null
        else
            echo "创建失败"
        fi
        ;;

    remove)
        if [ -z "$2" ]; then
            echo "用法: $0 remove <任务ID>"
            exit 1
        fi
        TASK_ID=$2
        
        read -p "确认删除任务 $TASK_ID? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        RESULT=$(curl -sf -X DELETE "$API_BASE/$TASK_ID" 2>/dev/null)
        echo "删除完成"
        ;;

    run)
        if [ -z "$2" ]; then
            echo "用法: $0 run <任务ID>"
            exit 1
        fi
        TASK_ID=$2
        
        echo "立即执行任务: $TASK_ID"
        RESULT=$(curl -sf -X POST "$API_BASE/$TASK_ID/run" 2>/dev/null)
        
        if [ -n "$RESULT" ]; then
            echo "任务已触发"
        else
            echo "触发失败"
        fi
        ;;

    logs)
        if [ -z "$2" ]; then
            echo "用法: $0 logs <任务ID>"
            exit 1
        fi
        TASK_ID=$2
        
        echo "=== 任务 $TASK_ID 执行日志 ==="
        LOGS=$(curl -sf "$API_BASE/$TASK_ID/logs" 2>/dev/null)
        if [ -n "$LOGS" ]; then
            echo "$LOGS" | python3 -m json.tool 2>/dev/null || echo "$LOGS"
        else
            echo "获取日志失败"
        fi
        ;;

    *)
        echo "用法: $0 [list|add|remove|run|logs]"
        echo ""
        echo "  list                    - 列出所有任务"
        echo "  add <名称> <cron>        - 创建任务"
        echo "  remove <任务ID>          - 删除任务"
        echo "  run <任务ID>             - 立即执行"
        echo "  logs <任务ID>            - 查看执行日志"
        echo ""
        echo "Cron 表达式示例:"
        echo "  0 2 * * *      - 每天凌晨2点"
        echo "  0 * * * *      - 每小时"
        echo "  */5 * * * *    - 每5分钟"
        echo "  0 9 * * 1-5    - 工作日9点"
        exit 1
        ;;
esac
