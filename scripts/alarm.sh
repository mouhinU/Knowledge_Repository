#!/bin/bash
# Knowledge Repository 告警管理脚本
# 用法: ./scripts/alarm.sh [list|add|remove|trigger|history|config]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 告警管理"
echo "=========================================="
echo ""

ACTION=${1:-list}
ALARM_FILE="./data/alarms.json"

case "$ACTION" in
    list)
        echo "=== 告警规则列表 ==="
        echo ""
        
        if [ ! -f "$ALARM_FILE" ]; then
            echo "无告警规则"
            exit 0
        fi
        
        python3 << 'EOF'
import json

with open('./data/alarms.json', 'r') as f:
    config = json.load(f)

alarms = config.get('alarms', [])
if not alarms:
    print("无告警规则")
    exit(0)

print(f"{'名称':<20} {'指标':<20} {'阈值':<15} {'状态':<10}")
print("-" * 65)

for a in alarms:
    name = a.get('name', '-')
    metric = a.get('metric', '-')
    threshold = a.get('threshold', '-')
    enabled = '● 启用' if a.get('enabled', True) else '○ 禁用'
    print(f"{name:<20} {metric:<20} {threshold:<15} {enabled:<10}")
EOF
        ;;

    add)
        if [ -z "$2" ] || [ -z "$3" ] || [ -z "$4" ]; then
            echo "用法: $0 add <名称> <指标> <阈值>"
            echo ""
            echo "指标:"
            echo "  cpu_usage       - CPU 使用率 (%)"
            echo "  memory_usage    - 内存使用率 (%)"
            echo "  disk_usage      - 磁盘使用率 (%)"
            echo "  error_rate      - 错误率"
            echo "  response_time   - 响应时间 (ms)"
            exit 1
        fi
        
        NAME=$2
        METRIC=$3
        THRESHOLD=$4
        
        if [ ! -f "$ALARM_FILE" ]; then
            mkdir -p ./data
            echo '{"alarms":[],"history":[]}' > "$ALARM_FILE"
        fi
        
        python3 << EOF
import json

with open('$ALARM_FILE', 'r') as f:
    config = json.load(f)

alarms = config.get('alarms', [])
alarms.append({
    'name': '$NAME',
    'metric': '$METRIC',
    'threshold': $THRESHOLD,
    'enabled': True,
    'createdAt': '$(date -u +%Y-%m-%dT%H:%M:%SZ)'
})
config['alarms'] = alarms

with open('$ALARM_FILE', 'w') as f:
    json.dump(config, f, indent=2)

print("告警规则已添加: $NAME")
EOF
        ;;

    remove)
        if [ -z "$2" ]; then
            echo "用法: $0 remove <告警名称>"
            exit 1
        fi
        
        NAME=$2
        
        python3 << EOF
import json

with open('$ALARM_FILE', 'r') as f:
    config = json.load(f)

config['alarms'] = [a for a in config.get('alarms', []) if a['name'] != '$NAME']

with open('$ALARM_FILE', 'w') as f:
    json.dump(config, f, indent=2)

print("告警规则已移除: $NAME")
EOF
        ;;

    trigger)
        if [ -z "$2" ]; then
            echo "用法: $0 trigger <告警名称> [消息]"
            echo ""
            echo "手动触发告警"
            exit 1
        fi
        
        NAME=$2
        MESSAGE=${3:-"手动触发告警"}
        
        python3 << EOF
import json
from datetime import datetime

with open('$ALARM_FILE', 'r') as f:
    config = json.load(f)

# 添加到历史
history = config.get('history', [])
history.append({
    'alarm': '$NAME',
    'message': '$MESSAGE',
    'timestamp': datetime.now().isoformat(),
    'level': 'WARNING'
})
config['history'] = history[-100:]  # 保留最近100条

with open('$ALARM_FILE', 'w') as f:
    json.dump(config, f, indent=2)

print("告警已触发: $NAME")
print("消息: $MESSAGE")
EOF
        ;;

    history)
        echo "=== 告警历史 ==="
        echo ""
        
        if [ ! -f "$ALARM_FILE" ]; then
            echo "无告警历史"
            exit 0
        fi
        
        LIMIT=${2:-20}
        
        python3 << EOF
import json

with open('$ALARM_FILE', 'r') as f:
    config = json.load(f)

history = config.get('history', [])
if not history:
    print("无告警历史")
    exit(0)

print(f"最近 {min($LIMIT, len(history))} 条告警:")
print()

for h in history[-$LIMIT:]:
    ts = h.get('timestamp', '-')[:19]
    alarm = h.get('alarm', '-')
    level = h.get('level', '-')
    msg = h.get('message', '-')
    print(f"[{ts}] [{level}] {alarm}: {msg}")
EOF
        ;;

    config)
        echo "=== 告警配置 ==="
        echo ""
        
        if [ ! -f "$ALARM_FILE" ]; then
            echo "未配置告警"
            exit 1
        fi
        
        cat "$ALARM_FILE" | python3 -m json.tool
        ;;

    check)
        echo "=== 告警检查 ==="
        echo ""
        
        if [ ! -f "$ALARM_FILE" ]; then
            echo "无告警规则"
            exit 0
        fi
        
        echo "检查告警条件..."
        echo ""
        
        python3 << 'EOF'
import json
import subprocess

with open('./data/alarms.json', 'r') as f:
    config = json.load(f)

alarms = config.get('alarms', [])

for a in alarms:
    if not a.get('enabled', True):
        continue
    
    name = a.get('name', '-')
    metric = a.get('metric', '-')
    threshold = a.get('threshold', 0)
    
    # 模拟获取指标值
    if metric == 'cpu_usage':
        # 获取 CPU 使用率
        value = 25  # 模拟值
    elif metric == 'memory_usage':
        value = 45  # 模拟值
    elif metric == 'disk_usage':
        value = 60  # 模拟值
    else:
        value = 0
    
    status = '正常' if value < threshold else '告警'
    print(f"  {name}: {metric} = {value} (阈值: {threshold}) [{status}]")
EOF
        ;;

    init)
        echo "=== 初始化告警配置 ==="
        echo ""
        
        mkdir -p ./data
        cat > "$ALARM_FILE" <<EOF
{
  "alarms": [
    {
      "name": "CPU告警",
      "metric": "cpu_usage",
      "threshold": 80,
      "enabled": true
    },
    {
      "name": "内存告警",
      "metric": "memory_usage",
      "threshold": 85,
      "enabled": true
    },
    {
      "name": "磁盘告警",
      "metric": "disk_usage",
      "threshold": 90,
      "enabled": true
    }
  ],
  "history": [],
  "notification": {
    "enabled": false,
    "channels": []
  }
}
EOF
        
        echo "告警配置已初始化"
        ;;

    *)
        echo "用法: $0 [list|add|remove|trigger|history|config|check|init]"
        echo ""
        echo "  list                - 列出告警规则"
        echo "  add <名称> <指标> <阈值> - 添加规则"
        echo "  remove <名称>       - 移除规则"
        echo "  trigger <名称> [消息] - 触发告警"
        echo "  history [数量]      - 查看历史"
        echo "  config              - 查看配置"
        echo "  check               - 检查告警条件"
        echo "  init                - 初始化配置"
        exit 1
        ;;
esac
