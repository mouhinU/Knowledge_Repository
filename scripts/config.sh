#!/bin/bash
# Knowledge Repository 系统配置管理脚本
# 用法: ./config.sh [list|get|set|delete]

cd "$(dirname "$0")/.." || exit 1

PORT=8091
API_BASE="http://localhost:$PORT/api/admin/config"

# 检查服务
if ! lsof -i :$PORT -t >/dev/null 2>&1; then
    echo "服务未运行，请先启动服务"
    exit 1
fi

ACTION=${1:-list}

case "$ACTION" in
    list)
        echo "=== 系统配置列表 ==="
        CONFIGS=$(curl -sf "$API_BASE/list" 2>/dev/null)
        if [ -n "$CONFIGS" ]; then
            echo "$CONFIGS" | python3 -m json.tool 2>/dev/null || echo "$CONFIGS"
        else
            echo "获取配置列表失败"
        fi
        ;;

    get)
        if [ -z "$2" ]; then
            echo "用法: $0 get <配置键>"
            exit 1
        fi
        KEY=$2
        
        VALUE=$(curl -sf "$API_BASE/$KEY" 2>/dev/null)
        if [ -n "$VALUE" ]; then
            echo "$KEY = $VALUE"
        else
            echo "配置项不存在: $KEY"
        fi
        ;;

    set)
        if [ -z "$2" ] || [ -z "$3" ]; then
            echo "用法: $0 set <配置键> <配置值>"
            exit 1
        fi
        KEY=$2
        VALUE=$3
        
        RESULT=$(curl -sf -X PUT "$API_BASE/$KEY" \
            -H "Content-Type: application/json" \
            -d "{\"value\":\"$VALUE\"}" 2>/dev/null)
        
        if [ -n "$RESULT" ]; then
            echo "设置成功: $KEY = $VALUE"
        else
            echo "设置失败"
        fi
        ;;

    delete)
        if [ -z "$2" ]; then
            echo "用法: $0 delete <配置键>"
            exit 1
        fi
        KEY=$2
        
        read -p "确认删除配置 $KEY? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        RESULT=$(curl -sf -X DELETE "$API_BASE/$KEY" 2>/dev/null)
        echo "删除完成"
        ;;

    reload)
        echo "重新加载配置..."
        RESULT=$(curl -sf -X POST "$API_BASE/reload" 2>/dev/null)
        if [ -n "$RESULT" ]; then
            echo "配置已重新加载"
        else
            echo "重新加载失败"
        fi
        ;;

    *)
        echo "用法: $0 [list|get|set|delete|reload]"
        echo ""
        echo "  list              - 列出所有配置"
        echo "  get <键>           - 获取配置值"
        echo "  set <键> <值>      - 设置配置"
        echo "  delete <键>        - 删除配置"
        echo "  reload             - 重新加载配置"
        exit 1
        ;;
esac
