#!/bin/bash
# Knowledge Repository Webhook 管理脚本
# 用法: ./webhook.sh [list|add|delete|test]

cd "$(dirname "$0")/.." || exit 1

PORT=8091
API_BASE="http://localhost:$PORT/api/admin/webhook"

# 检查服务
if ! lsof -i :$PORT -t >/dev/null 2>&1; then
    echo "服务未运行，请先启动服务"
    exit 1
fi

ACTION=${1:-list}

case "$ACTION" in
    list)
        echo "=== Webhook 列表 ==="
        HOOKS=$(curl -sf "$API_BASE/list" 2>/dev/null)
        if [ -n "$HOOKS" ]; then
            echo "$HOOKS" | python3 -m json.tool 2>/dev/null || echo "$HOOKS"
        else
            echo "获取 Webhook 列表失败"
        fi
        ;;

    add)
        if [ -z "$2" ] || [ -z "$3" ]; then
            echo "用法: $0 add <事件类型> <回调URL>"
            echo ""
            echo "事件类型:"
            echo "  document.created   - 文档创建"
            echo "  document.indexed   - 文档索引完成"
            echo "  document.failed    - 文档处理失败"
            echo "  document.deleted   - 文档删除"
            exit 1
        fi
        EVENT=$2
        URL=$3
        
        echo "创建 Webhook: $EVENT -> $URL"
        RESULT=$(curl -sf -X POST "$API_BASE" \
            -H "Content-Type: application/json" \
            -d "{\"event\":\"$EVENT\",\"url\":\"$URL\"}" 2>/dev/null)
        
        if [ -n "$RESULT" ]; then
            echo "创建成功"
            echo "$RESULT" | python3 -m json.tool 2>/dev/null
        else
            echo "创建失败"
        fi
        ;;

    delete)
        if [ -z "$2" ]; then
            echo "用法: $0 delete <webhook ID>"
            exit 1
        fi
        HOOK_ID=$2
        
        read -p "确认删除 Webhook $HOOK_ID? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        RESULT=$(curl -sf -X DELETE "$API_BASE/$HOOK_ID" 2>/dev/null)
        echo "删除完成"
        ;;

    test)
        if [ -z "$2" ]; then
            echo "用法: $0 test <webhook ID>"
            exit 1
        fi
        HOOK_ID=$2
        
        echo "测试 Webhook: $HOOK_ID"
        RESULT=$(curl -sf -X POST "$API_BASE/$HOOK_ID/test" 2>/dev/null)
        
        if [ -n "$RESULT" ]; then
            echo "测试成功"
            echo "$RESULT" | python3 -m json.tool 2>/dev/null
        else
            echo "测试失败"
        fi
        ;;

    logs)
        if [ -z "$2" ]; then
            echo "用法: $0 logs <webhook ID>"
            exit 1
        fi
        HOOK_ID=$2
        
        echo "=== Webhook $HOOK_ID 投递日志 ==="
        LOGS=$(curl -sf "$API_BASE/$HOOK_ID/logs" 2>/dev/null)
        if [ -n "$LOGS" ]; then
            echo "$LOGS" | python3 -m json.tool 2>/dev/null || echo "$LOGS"
        else
            echo "获取日志失败"
        fi
        ;;

    *)
        echo "用法: $0 [list|add|delete|test|logs]"
        echo ""
        echo "  list              - 列出所有 Webhook"
        echo "  add <事件> <URL>   - 创建 Webhook"
        echo "  delete <ID>        - 删除 Webhook"
        echo "  test <ID>          - 测试 Webhook"
        echo "  logs <ID>          - 查看投递日志"
        exit 1
        ;;
esac
