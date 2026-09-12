#!/bin/bash
# Knowledge Repository 主题管理脚本
# 用法: ./theme.sh [list|current|set|install|preview]

cd "$(dirname "$0")/.." || exit 1

PORT=8091
API_BASE="http://localhost:$PORT/api/admin/theme"

# 检查服务
if ! lsof -i :$PORT -t >/dev/null 2>&1; then
    echo "服务未运行，请先启动服务"
    exit 1
fi

ACTION=${1:-list}

case "$ACTION" in
    list)
        echo "=== 可用主题 ==="
        THEMES=$(curl -sf "$API_BASE/list" 2>/dev/null)
        if [ -n "$THEMES" ]; then
            echo "$THEMES" | python3 -m json.tool 2>/dev/null || echo "$THEMES"
        else
            echo "获取主题列表失败"
        fi
        ;;

    current)
        echo "=== 当前主题 ==="
        CURRENT=$(curl -sf "$API_BASE/current" 2>/dev/null)
        if [ -n "$CURRENT" ]; then
            echo "$CURRENT" | python3 -m json.tool 2>/dev/null || echo "$CURRENT"
        else
            echo "获取当前主题失败"
        fi
        ;;

    set)
        if [ -z "$2" ]; then
            echo "用法: $0 set <主题ID>"
            echo ""
            echo "使用 'list' 查看可用主题"
            exit 1
        fi
        THEME_ID=$2
        
        echo "切换主题: $THEME_ID"
        RESULT=$(curl -sf -X PUT "$API_BASE/current" \
            -H "Content-Type: application/json" \
            -d "{\"themeId\":\"$THEME_ID\"}" 2>/dev/null)
        
        if [ -n "$RESULT" ]; then
            echo "主题已切换"
        else
            echo "切换失败"
        fi
        ;;

    install)
        if [ -z "$2" ]; then
            echo "用法: $0 install <主题包路径>"
            echo ""
            echo "主题包格式: .zip 或 .jar"
            exit 1
        fi
        THEME_PATH=$2
        
        if [ ! -f "$THEME_PATH" ]; then
            echo "文件不存在: $THEME_PATH"
            exit 1
        fi
        
        echo "安装主题: $THEME_PATH"
        RESULT=$(curl -sf -X POST "$API_BASE/install" \
            -F "file=@$THEME_PATH" 2>/dev/null)
        
        if [ -n "$RESULT" ]; then
            echo "安装成功"
            echo "$RESULT" | python3 -m json.tool 2>/dev/null
        else
            echo "安装失败"
        fi
        ;;

    preview)
        if [ -z "$2" ]; then
            echo "用法: $0 preview <主题ID>"
            exit 1
        fi
        THEME_ID=$2
        
        echo "预览主题: $THEME_ID"
        echo "打开浏览器访问: http://localhost:$PORT/admin.html?theme=$THEME_ID"
        ;;

    customize)
        echo "=== 自定义配置 ==="
        CUSTOM=$(curl -sf "$API_BASE/customize" 2>/dev/null)
        if [ -n "$CUSTOM" ]; then
            echo "$CUSTOM" | python3 -m json.tool 2>/dev/null || echo "$CUSTOM"
        else
            echo "获取自定义配置失败"
        fi
        ;;

    *)
        echo "用法: $0 [list|current|set|install|preview|customize]"
        echo ""
        echo "  list              - 列出所有主题"
        echo "  current           - 查看当前主题"
        echo "  set <主题ID>       - 切换主题"
        echo "  install <路径>     - 安装主题"
        echo "  preview <主题ID>   - 预览主题"
        echo "  customize         - 查看自定义配置"
        exit 1
        ;;
esac
