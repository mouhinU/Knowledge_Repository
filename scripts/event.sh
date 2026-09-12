#!/bin/bash
# Knowledge Repository 事件管理脚本
# 用法: ./scripts/event.sh [list|publish|subscribe|history|replay]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 事件管理"
echo "=========================================="
echo ""

ACTION=${1:-list}
EVENT_DIR="./data/events"

case "$ACTION" in
    list)
        echo "=== 事件类型 ==="
        echo ""
        
        mkdir -p "$EVENT_DIR"
        
        # 从事件日志中提取事件类型
        if [ -f "$EVENT_DIR/events.log" ]; then
            echo "已发生的事件类型:"
            grep -o '"type":"[^"]*"' "$EVENT_DIR/events.log" 2>/dev/null | sort | uniq -c | sort -rn
        else
            echo "无事件记录"
        fi
        ;;

    publish)
        if [ -z "$2" ] || [ -z "$3" ]; then
            echo "用法: $0 publish <事件类型> <数据>"
            echo ""
            echo "事件类型:"
            echo "  document.created   - 文档创建"
            echo "  document.indexed   - 文档索引完成"
            echo "  document.deleted   - 文档删除"
            echo "  user.login         - 用户登录"
            exit 1
        fi
        
        EVENT_TYPE=$2
        EVENT_DATA=$3
        
        mkdir -p "$EVENT_DIR"
        
        # 追加到事件日志
        TIMESTAMP=$(date -u +%Y-%m-%dT%H:%M:%SZ)
        EVENT_ID=$(uuidgen 2>/dev/null | cut -d'-' -f1 || echo $RANDOM)
        
        echo "{\"id\":\"$EVENT_ID\",\"type\":\"$EVENT_TYPE\",\"data\":\"$EVENT_DATA\",\"timestamp\":\"$TIMESTAMP\"}" >> "$EVENT_DIR/events.log"
        
        echo "事件已发布: $EVENT_TYPE (ID: $EVENT_ID)"
        ;;

    subscribe)
        if [ -z "$2" ]; then
            echo "用法: $0 subscribe <事件类型>"
            echo ""
            echo "订阅并实时显示指定类型的事件"
            exit 1
        fi
        
        EVENT_TYPE=$2
        EVENT_LOG="$EVENT_DIR/events.log"
        
        if [ ! -f "$EVENT_LOG" ]; then
            echo "无事件日志"
            exit 1
        fi
        
        echo "=== 订阅事件: $EVENT_TYPE (Ctrl+C 退出) ==="
        echo ""
        
        tail -f "$EVENT_LOG" | grep --line-buffered "\"type\":\"$EVENT_TYPE\""
        ;;

    history)
        echo "=== 事件历史 ==="
        echo ""
        
        EVENT_LOG="$EVENT_DIR/events.log"
        LIMIT=${2:-20}
        
        if [ ! -f "$EVENT_LOG" ]; then
            echo "无事件历史"
            exit 0
        fi
        
        echo "最近 $LIMIT 条事件:"
        echo ""
        
        tail -n "$LIMIT" "$EVENT_LOG" | while read line; do
            echo "$line" | python3 -c "
import sys, json
try:
    e = json.loads(sys.stdin.read())
    print(f\"[{e.get('timestamp', '-')[:19]}] {e.get('type', '-')} - {e.get('data', '')[:50]}\")
except:
    pass
"
        done
        ;;

    replay)
        if [ -z "$2" ]; then
            echo "用法: $0 replay <事件类型|all>"
            echo ""
            echo "重放事件，触发相关处理"
            exit 1
        fi
        
        EVENT_TYPE=$2
        EVENT_LOG="$EVENT_DIR/events.log"
        
        if [ ! -f "$EVENT_LOG" ]; then
            echo "无事件历史"
            exit 1
        fi
        
        echo "=== 重放事件 ==="
        echo ""
        
        if [ "$EVENT_TYPE" = "all" ]; then
            COUNT=$(wc -l < "$EVENT_LOG" | tr -d ' ')
            echo "将重放所有 $COUNT 条事件"
        else
            COUNT=$(grep -c "\"type\":\"$EVENT_TYPE\"" "$EVENT_LOG" 2>/dev/null || echo 0)
            echo "将重放 $COUNT 条 $EVENT_TYPE 事件"
        fi
        
        read -p "确认重放? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        echo "事件重放完成 (模拟)"
        ;;

    stats)
        echo "=== 事件统计 ==="
        echo ""
        
        EVENT_LOG="$EVENT_DIR/events.log"
        
        if [ ! -f "$EVENT_LOG" ]; then
            echo "无事件记录"
            exit 0
        fi
        
        TOTAL=$(wc -l < "$EVENT_LOG" | tr -d ' ')
        echo "总事件数: $TOTAL"
        echo ""
        echo "按类型统计:"
        grep -o '"type":"[^"]*"' "$EVENT_LOG" 2>/dev/null | sort | uniq -c | sort -rn
        
        echo ""
        echo "按小时统计 (最近24小时):"
        # 这里可以添加更详细的时间统计
        ;;

    clean)
        DAYS=${2:-30}
        
        echo "清理 $DAYS 天前的事件..."
        
        EVENT_LOG="$EVENT_DIR/events.log"
        if [ -f "$EVENT_LOG" ]; then
            # 备份旧日志
            cp "$EVENT_LOG" "$EVENT_LOG.backup.$(date +%Y%m%d)"
            # 清空日志
            > "$EVENT_LOG"
            echo "事件日志已清理"
        else
            echo "无事件日志"
        fi
        ;;

    *)
        echo "用法: $0 [list|publish|subscribe|history|replay|stats|clean]"
        echo ""
        echo "  list                - 列出事件类型"
        echo "  publish <类型> <数据> - 发布事件"
        echo "  subscribe <类型>    - 订阅事件"
        echo "  history [数量]      - 查看历史"
        echo "  replay <类型|all>   - 重放事件"
        echo "  stats               - 统计信息"
        echo "  clean [天数]        - 清理旧事件"
        exit 1
        ;;
esac
