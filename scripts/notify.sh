#!/bin/bash
# Knowledge Repository 通知管理脚本
# 用法: ./notify.sh [list|send|config|history|test]

cd "$(dirname "$0")/.." || exit 1

PORT=8091
API_BASE="http://localhost:$PORT/api/admin/notify"

# 检查服务
if ! lsof -i :$PORT -t >/dev/null 2>&1; then
    echo "服务未运行，请先启动服务"
    exit 1
fi

ACTION=${1:-list}

case "$ACTION" in
    list)
        echo "=== 通知渠道配置 ==="
        CHANNELS=$(curl -sf "$API_BASE/channels" 2>/dev/null)
        if [ -n "$CHANNELS" ]; then
            echo "$CHANNELS" | python3 -m json.tool 2>/dev/null || echo "$CHANNELS"
        else
            echo "获取通知渠道失败"
        fi
        ;;

    send)
        if [ -z "$2" ] || [ -z "$3" ]; then
            echo "用法: $0 send <渠道> <消息内容>"
            echo ""
            echo "渠道: email, webhook, dingtalk, feishu"
            echo ""
            echo "示例:"
            echo "  $0 send email '系统维护通知'"
            echo "  $0 send webhook '文档处理完成'"
            exit 1
        fi
        CHANNEL=$2
        MESSAGE=$3
        
        echo "发送通知: [$CHANNEL] $MESSAGE"
        RESULT=$(curl -sf -X POST "$API_BASE/send" \
            -H "Content-Type: application/json" \
            -d "{\"channel\":\"$CHANNEL\",\"message\":\"$MESSAGE\"}" 2>/dev/null)
        
        if [ -n "$RESULT" ]; then
            echo "发送成功"
        else
            echo "发送失败"
        fi
        ;;

    config)
        if [ -z "$2" ]; then
            echo "用法: $0 config <渠道>"
            echo ""
            echo "查看指定渠道的配置"
            exit 1
        fi
        CHANNEL=$2
        
        CONFIG=$(curl -sf "$API_BASE/config/$CHANNEL" 2>/dev/null)
        if [ -n "$CONFIG" ]; then
            echo "$CONFIG" | python3 -m json.tool 2>/dev/null || echo "$CONFIG"
        else
            echo "获取配置失败"
        fi
        ;;

    history)
        echo "=== 通知历史 (最近 20 条) ==="
        LIMIT=${2:-20}
        
        HISTORY=$(curl -sf "$API_BASE/history?limit=$LIMIT" 2>/dev/null)
        if [ -n "$HISTORY" ]; then
            echo "$HISTORY" | python3 -m json.tool 2>/dev/null || echo "$HISTORY"
        else
            echo "获取历史记录失败"
        fi
        ;;

    test)
        if [ -z "$2" ]; then
            echo "用法: $0 test <渠道>"
            echo ""
            echo "发送测试通知"
            exit 1
        fi
        CHANNEL=$2
        
        echo "发送测试通知到: $CHANNEL"
        RESULT=$(curl -sf -X POST "$API_BASE/test/$CHANNEL" 2>/dev/null)
        
        if [ -n "$RESULT" ]; then
            echo "测试通知已发送"
        else
            echo "发送失败"
        fi
        ;;

    template)
        echo "=== 通知模板 ==="
        TEMPLATES=$(curl -sf "$API_BASE/templates" 2>/dev/null)
        if [ -n "$TEMPLATES" ]; then
            echo "$TEMPLATES" | python3 -m json.tool 2>/dev/null || echo "$TEMPLATES"
        else
            echo "获取模板失败"
        fi
        ;;

    *)
        echo "用法: $0 [list|send|config|history|test|template]"
        echo ""
        echo "  list              - 列出通知渠道"
        echo "  send <渠道> <消息> - 发送通知"
        echo "  config <渠道>      - 查看渠道配置"
        echo "  history [数量]     - 查看发送历史"
        echo "  test <渠道>        - 发送测试通知"
        echo "  template          - 查看通知模板"
        exit 1
        ;;
esac
