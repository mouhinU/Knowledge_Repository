#!/bin/bash
# Knowledge Repository 定时器管理脚本
# 用法: ./scripts/timer.sh [list|start|stop|status|config]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 定时器"
echo "=========================================="
echo ""

ACTION=${1:-list}
TIMER_DIR="./data/timers"

case "$ACTION" in
    list)
        echo "=== 定时器列表 ==="
        echo ""
        
        mkdir -p "$TIMER_DIR"
        
        TIMERS=$(ls "$TIMER_DIR"/*.json 2>/dev/null)
        
        if [ -z "$TIMERS" ]; then
            echo "无定时器"
            exit 0
        fi
        
        printf "%-20s %-12s %-15s %-20s %-10s\n" "名称" "间隔" "状态" "上次触发" "触发次数"
        echo "-------------------------------------------------------------------------"
        
        for T in $TIMERS; do
            python3 << EOF
import json
with open('$T', 'r') as f:
    t = json.load(f)
name = t.get('name', '-')
interval = t.get('interval', 0)
unit = t.get('unit', 's')
status = '● 运行' if t.get('running', False) else '○ 停止'
last = t.get('lastTrigger', '-')[:16]
count = t.get('triggerCount', 0)
print(f"{name:<20} {interval}{unit:<10} {status:<15} {last:<20} {count:<10}")
EOF
        done
        ;;

    start)
        if [ -z "$2" ]; then
            echo "用法: $0 start <定时器名称>"
            exit 1
        fi
        
        NAME=$2
        T_FILE="$TIMER_DIR/$NAME.json"
        
        if [ ! -f "$T_FILE" ]; then
            echo "定时器不存在: $NAME"
            exit 1
        fi
        
        python3 << EOF
import json

with open('$T_FILE', 'r') as f:
    t = json.load(f)

t['running'] = True

with open('$T_FILE', 'w') as f:
    json.dump(t, f, indent=2)

print("定时器已启动: $NAME")
EOF
        ;;

    stop)
        if [ -z "$2" ]; then
            echo "用法: $0 stop <定时器名称>"
            exit 1
        fi
        
        NAME=$2
        T_FILE="$TIMER_DIR/$NAME.json"
        
        if [ ! -f "$T_FILE" ]; then
            echo "定时器不存在: $NAME"
            exit 1
        fi
        
        python3 << EOF
import json

with open('$T_FILE', 'r') as f:
    t = json.load(f)

t['running'] = False

with open('$T_FILE', 'w') as f:
    json.dump(t, f, indent=2)

print("定时器已停止: $NAME")
EOF
        ;;

    status)
        if [ -z "$2" ]; then
            echo "用法: $0 status <定时器名称>"
            exit 1
        fi
        
        NAME=$2
        T_FILE="$TIMER_DIR/$NAME.json"
        
        if [ ! -f "$T_FILE" ]; then
            echo "定时器不存在: $NAME"
            exit 1
        fi
        
        echo "=== 定时器状态: $NAME ==="
        echo ""
        
        cat "$T_FILE" | python3 -m json.tool
        ;;

    config)
        if [ -z "$2" ]; then
            echo "用法: $0 config <定时器名称> <间隔> [单位]"
            echo ""
            echo "单位: s(秒), m(分), h(时)"
            echo ""
            echo "示例:"
            echo "  $0 config health-check 30 s"
            echo "  $0 config backup 1 h"
            exit 1
        fi
        
        NAME=$2
        INTERVAL=$3
        UNIT=${4:-s}
        
        mkdir -p "$TIMER_DIR"
        T_FILE="$TIMER_DIR/$NAME.json"
        
        if [ ! -f "$T_FILE" ]; then
            echo '{"triggerCount":0,"running":false}' > "$T_FILE"
        fi
        
        python3 << EOF
import json

with open('$T_FILE', 'r') as f:
    t = json.load(f)

t['name'] = '$NAME'
t['interval'] = $INTERVAL
t['unit'] = '$UNIT'

with open('$T_FILE', 'w') as f:
    json.dump(t, f, indent=2)

print("定时器已配置: $NAME (间隔: ${INTERVAL}${UNIT})")
EOF
        ;;

    create)
        if [ -z "$2" ] || [ -z "$3" ]; then
            echo "用法: $0 create <名称> <间隔> [单位] [命令]"
            echo ""
            echo "创建并配置定时器"
            exit 1
        fi
        
        NAME=$2
        INTERVAL=$3
        UNIT=${4:-s}
        CMD=${5:-"echo 'Timer triggered: $NAME'"}
        
        mkdir -p "$TIMER_DIR"
        T_FILE="$TIMER_DIR/$NAME.json"
        
        cat > "$T_FILE" <<EOF
{
  "name": "$NAME",
  "interval": $INTERVAL,
  "unit": "$UNIT",
  "command": "$CMD",
  "running": false,
  "triggerCount": 0,
  "lastTrigger": null,
  "createdAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF
        
        echo "定时器已创建: $NAME"
        ;;

    delete)
        if [ -z "$2" ]; then
            echo "用法: $0 delete <定时器名称>"
            exit 1
        fi
        
        NAME=$2
        T_FILE="$TIMER_DIR/$NAME.json"
        
        if [ ! -f "$T_FILE" ]; then
            echo "定时器不存在: $NAME"
            exit 1
        fi
        
        read -p "确认删除定时器 $NAME? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        rm -f "$T_FILE"
        echo "定时器已删除: $NAME"
        ;;

    *)
        echo "用法: $0 [list|start|stop|status|config|create|delete]"
        echo ""
        echo "  list                - 列出定时器"
        echo "  start <名称>        - 启动定时器"
        echo "  stop <名称>         - 停止定时器"
        echo "  status <名称>       - 查看详情"
        echo "  config <名称> <间隔> [单位] - 配置间隔"
        echo "  create <名称> <间隔> [单位] [命令] - 创建定时器"
        echo "  delete <名称>       - 删除定时器"
        exit 1
        ;;
esac
