#!/bin/bash
# Knowledge Repository 插件管理脚本
# 用法: ./plugin.sh [list|install|uninstall|enable|disable]

cd "$(dirname "$0")/.." || exit 1

PORT=8091
API_BASE="http://localhost:$PORT/api/admin/plugin"

# 检查服务
if ! lsof -i :$PORT -t >/dev/null 2>&1; then
    echo "服务未运行，请先启动服务"
    exit 1
fi

ACTION=${1:-list}

case "$ACTION" in
    list)
        echo "=== 已安装插件 ==="
        PLUGINS=$(curl -sf "$API_BASE/list" 2>/dev/null)
        if [ -n "$PLUGINS" ]; then
            echo "$PLUGINS" | python3 -m json.tool 2>/dev/null || echo "$PLUGINS"
        else
            echo "获取插件列表失败"
        fi
        ;;

    install)
        if [ -z "$2" ]; then
            echo "用法: $0 install <插件路径或URL>"
            echo ""
            echo "示例:"
            echo "  $0 install ./plugins/my-plugin.jar"
            echo "  $0 install https://example.com/plugin.jar"
            exit 1
        fi
        SOURCE=$2
        
        echo "安装插件: $SOURCE"
        
        if [[ "$SOURCE" == http* ]]; then
            RESULT=$(curl -sf -X POST "$API_BASE/install" \
                -H "Content-Type: application/json" \
                -d "{\"url\":\"$SOURCE\"}" 2>/dev/null)
        else
            if [ ! -f "$SOURCE" ]; then
                echo "文件不存在: $SOURCE"
                exit 1
            fi
            RESULT=$(curl -sf -X POST "$API_BASE/install" \
                -F "file=@$SOURCE" 2>/dev/null)
        fi
        
        if [ -n "$RESULT" ]; then
            echo "安装成功"
            echo "$RESULT" | python3 -m json.tool 2>/dev/null
            echo ""
            echo "注意: 需要重启服务以加载插件"
        else
            echo "安装失败"
        fi
        ;;

    uninstall)
        if [ -z "$2" ]; then
            echo "用法: $0 uninstall <插件ID>"
            exit 1
        fi
        PLUGIN_ID=$2
        
        read -p "确认卸载插件 $PLUGIN_ID? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        RESULT=$(curl -sf -X DELETE "$API_BASE/$PLUGIN_ID" 2>/dev/null)
        if [ -n "$RESULT" ]; then
            echo "卸载成功"
            echo "注意: 需要重启服务以生效"
        else
            echo "卸载失败"
        fi
        ;;

    enable)
        if [ -z "$2" ]; then
            echo "用法: $0 enable <插件ID>"
            exit 1
        fi
        PLUGIN_ID=$2
        
        RESULT=$(curl -sf -X POST "$API_BASE/$PLUGIN_ID/enable" 2>/dev/null)
        if [ -n "$RESULT" ]; then
            echo "插件已启用"
        else
            echo "启用失败"
        fi
        ;;

    disable)
        if [ -z "$2" ]; then
            echo "用法: $0 disable <插件ID>"
            exit 1
        fi
        PLUGIN_ID=$2
        
        RESULT=$(curl -sf -X POST "$API_BASE/$PLUGIN_ID/disable" 2>/dev/null)
        if [ -n "$RESULT" ]; then
            echo "插件已禁用"
        else
            echo "禁用失败"
        fi
        ;;

    info)
        if [ -z "$2" ]; then
            echo "用法: $0 info <插件ID>"
            exit 1
        fi
        PLUGIN_ID=$2
        
        INFO=$(curl -sf "$API_BASE/$PLUGIN_ID" 2>/dev/null)
        if [ -n "$INFO" ]; then
            echo "$INFO" | python3 -m json.tool 2>/dev/null || echo "$INFO"
        else
            echo "插件不存在"
        fi
        ;;

    *)
        echo "用法: $0 [list|install|uninstall|enable|disable|info]"
        echo ""
        echo "  list                  - 列出所有插件"
        echo "  install <路径/URL>     - 安装插件"
        echo "  uninstall <ID>        - 卸载插件"
        echo "  enable <ID>           - 启用插件"
        echo "  disable <ID>          - 禁用插件"
        echo "  info <ID>             - 查看详情"
        exit 1
        ;;
esac
