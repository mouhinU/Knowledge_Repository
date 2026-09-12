#!/bin/bash
# Knowledge Repository API Key 管理脚本
# 用法: ./apikey.sh [list|create|revoke|info]

cd "$(dirname "$0")/.." || exit 1

PORT=8091
API_BASE="http://localhost:$PORT/api/admin/apikey"

# 检查服务
if ! lsof -i :$PORT -t >/dev/null 2>&1; then
    echo "服务未运行，请先启动服务"
    exit 1
fi

ACTION=${1:-list}

case "$ACTION" in
    list)
        echo "=== API Key 列表 ==="
        KEYS=$(curl -sf "$API_BASE/list" 2>/dev/null)
        if [ -n "$KEYS" ]; then
            echo "$KEYS" | python3 -m json.tool 2>/dev/null || echo "$KEYS"
        else
            echo "获取 API Key 列表失败"
        fi
        ;;

    create)
        if [ -z "$2" ]; then
            echo "用法: $0 create <名称> [过期天数]"
            echo ""
            echo "示例:"
            echo "  $0 create '测试应用'"
            echo "  $0 create '生产应用' 365"
            exit 1
        fi
        NAME=$2
        EXPIRE_DAYS=${3:-}
        
        echo "创建 API Key: $NAME"
        
        if [ -n "$EXPIRE_DAYS" ]; then
            RESULT=$(curl -sf -X POST "$API_BASE" \
                -H "Content-Type: application/json" \
                -d "{\"name\":\"$NAME\",\"expireDays\":$EXPIRE_DAYS}" 2>/dev/null)
        else
            RESULT=$(curl -sf -X POST "$API_BASE" \
                -H "Content-Type: application/json" \
                -d "{\"name\":\"$NAME\"}" 2>/dev/null)
        fi
        
        if [ -n "$RESULT" ]; then
            echo "创建成功"
            echo ""
            echo "$RESULT" | python3 -m json.tool 2>/dev/null
            echo ""
            echo "请妥善保存 API Key，后续无法再次查看完整密钥"
        else
            echo "创建失败"
        fi
        ;;

    revoke)
        if [ -z "$2" ]; then
            echo "用法: $0 revoke <API Key ID>"
            exit 1
        fi
        KEY_ID=$2
        
        read -p "确认撤销 API Key $KEY_ID? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        RESULT=$(curl -sf -X POST "$API_BASE/$KEY_ID/revoke" 2>/dev/null)
        if [ -n "$RESULT" ]; then
            echo "API Key 已撤销"
        else
            echo "撤销失败"
        fi
        ;;

    info)
        if [ -z "$2" ]; then
            echo "用法: $0 info <API Key ID>"
            exit 1
        fi
        KEY_ID=$2
        
        INFO=$(curl -sf "$API_BASE/$KEY_ID" 2>/dev/null)
        if [ -n "$INFO" ]; then
            echo "$INFO" | python3 -m json.tool 2>/dev/null || echo "$INFO"
        else
            echo "API Key 不存在"
        fi
        ;;

    usage)
        if [ -z "$2" ]; then
            echo "用法: $0 usage <API Key ID>"
            exit 1
        fi
        KEY_ID=$2
        
        echo "=== API Key $KEY_ID 使用统计 ==="
        USAGE=$(curl -sf "$API_BASE/$KEY_ID/usage" 2>/dev/null)
        if [ -n "$USAGE" ]; then
            echo "$USAGE" | python3 -m json.tool 2>/dev/null || echo "$USAGE"
        else
            echo "获取使用统计失败"
        fi
        ;;

    *)
        echo "用法: $0 [list|create|revoke|info|usage]"
        echo ""
        echo "  list              - 列出所有 API Key"
        echo "  create <名称> [天数] - 创建 API Key"
        echo "  revoke <ID>       - 撤销 API Key"
        echo "  info <ID>         - 查看详情"
        echo "  usage <ID>        - 查看使用统计"
        exit 1
        ;;
esac
