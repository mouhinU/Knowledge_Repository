#!/bin/bash
# Knowledge Repository 任务调度器管理脚本
# 用法: ./scripts/scheduler.sh [status|jobs|add|remove|pause|resume|trigger]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 任务调度器"
echo "=========================================="
echo ""

ACTION=${1:-status}
SCHEDULER_FILE="./data/scheduler.json"

case "$ACTION" in
    status)
        echo "=== 调度器状态 ==="
        echo ""
        
        if [ ! -f "$SCHEDULER_FILE" ]; then
            echo "调度器未初始化"
            echo ""
            echo "使用 '$0 init' 创建调度器配置"
            exit 0
        fi
        
        python3 << 'EOF'
import json

with open('./data/scheduler.json', 'r') as f:
    config = json.load(f)

print(f"调度器状态: {config.get('enabled', False) and '运行中' or '已暂停'}")
print(f"线程池大小: {config.get('threadPoolSize', 5)}")
print(f"任务总数: {len(config.get('jobs', []))}")

active = sum(1 for j in config.get('jobs', []) if j.get('enabled', True))
paused = len(config.get('jobs', [])) - active

print(f"活跃任务: {active}")
print(f"暂停任务: {paused}")
EOF
        ;;

    jobs)
        echo "=== 调度任务列表 ==="
        echo ""
        
        if [ ! -f "$SCHEDULER_FILE" ]; then
            echo "调度器未初始化"
            exit 1
        fi
        
        python3 << 'EOF'
import json

with open('./data/scheduler.json', 'r') as f:
    config = json.load(f)

jobs = config.get('jobs', [])
if not jobs:
    print("无调度任务")
    exit(0)

print(f"{'名称':<20} {'Cron':<18} {'状态':<10} {'上次执行':<20} {'下次执行':<20}")
print("-" * 88)

for job in jobs:
    name = job.get('name', '-')
    cron = job.get('cron', '-')
    enabled = '● 活跃' if job.get('enabled', True) else '○ 暂停'
    last = job.get('lastRun', '-')[:10]
    next_run = job.get('nextRun', '-')[:10]
    print(f"{name:<20} {cron:<18} {enabled:<10} {last:<20} {next_run:<20}")
EOF
        ;;

    add)
        if [ -z "$2" ] || [ -z "$3" ] || [ -z "$4" ]; then
            echo "用法: $0 add <名称> <cron> <命令>"
            echo ""
            echo "示例:"
            echo "  $0 add '每日备份' '0 2 * * *' './scripts/backup.sh'"
            echo "  $0 add '健康检查' '*/5 * * * *' './scripts/health-check.sh'"
            exit 1
        fi
        
        NAME=$2
        CRON=$3
        CMD=$4
        
        if [ ! -f "$SCHEDULER_FILE" ]; then
            mkdir -p ./data
            echo '{"enabled":true,"threadPoolSize":5,"jobs":[]}' > "$SCHEDULER_FILE"
        fi
        
        python3 << EOF
import json

with open('$SCHEDULER_FILE', 'r') as f:
    config = json.load(f)

jobs = config.get('jobs', [])

# 检查是否已存在
for j in jobs:
    if j['name'] == '$NAME':
        print("任务已存在: $NAME")
        exit(1)

jobs.append({
    'name': '$NAME',
    'cron': '$CRON',
    'command': '$CMD',
    'enabled': True,
    'lastRun': None,
    'nextRun': None,
    'runCount': 0
})
config['jobs'] = jobs

with open('$SCHEDULER_FILE', 'w') as f:
    json.dump(config, f, indent=2)

print("任务添加成功: $NAME")
EOF
        ;;

    remove)
        if [ -z "$2" ]; then
            echo "用法: $0 remove <任务名称>"
            exit 1
        fi
        
        NAME=$2
        
        python3 << EOF
import json

with open('$SCHEDULER_FILE', 'r') as f:
    config = json.load(f)

config['jobs'] = [j for j in config.get('jobs', []) if j['name'] != '$NAME']

with open('$SCHEDULER_FILE', 'w') as f:
    json.dump(config, f, indent=2)

print("任务已移除: $NAME")
EOF
        ;;

    pause)
        if [ -z "$2" ]; then
            echo "用法: $0 pause <任务名称>"
            exit 1
        fi
        
        NAME=$2
        
        python3 << EOF
import json

with open('$SCHEDULER_FILE', 'r') as f:
    config = json.load(f)

for j in config.get('jobs', []):
    if j['name'] == '$NAME':
        j['enabled'] = False
        break

with open('$SCHEDULER_FILE', 'w') as f:
    json.dump(config, f, indent=2)

print("任务已暂停: $NAME")
EOF
        ;;

    resume)
        if [ -z "$2" ]; then
            echo "用法: $0 resume <任务名称>"
            exit 1
        fi
        
        NAME=$2
        
        python3 << EOF
import json

with open('$SCHEDULER_FILE', 'r') as f:
    config = json.load(f)

for j in config.get('jobs', []):
    if j['name'] == '$NAME':
        j['enabled'] = True
        break

with open('$SCHEDULER_FILE', 'w') as f:
    json.dump(config, f, indent=2)

print("任务已恢复: $NAME")
EOF
        ;;

    trigger)
        if [ -z "$2" ]; then
            echo "用法: $0 trigger <任务名称>"
            echo ""
            echo "立即触发指定任务"
            exit 1
        fi
        
        NAME=$2
        
        echo "立即触发任务: $NAME"
        
        python3 << EOF
import json
import subprocess
from datetime import datetime

with open('$SCHEDULER_FILE', 'r') as f:
    config = json.load(f)

for j in config.get('jobs', []):
    if j['name'] == '$NAME':
        cmd = j.get('command', '')
        print(f"执行命令: {cmd}")
        
        # 执行命令
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
        
        if result.returncode == 0:
            print("执行成功")
        else:
            print(f"执行失败: {result.stderr}")
        
        # 更新统计
        j['lastRun'] = datetime.now().isoformat()
        j['runCount'] = j.get('runCount', 0) + 1
        break

with open('$SCHEDULER_FILE', 'w') as f:
    json.dump(config, f, indent=2)
EOF
        ;;

    init)
        echo "=== 初始化调度器 ==="
        echo ""
        
        mkdir -p ./data
        cat > "$SCHEDULER_FILE" <<EOF
{
  "enabled": true,
  "threadPoolSize": 5,
  "jobs": [
    {
      "name": "健康检查",
      "cron": "*/5 * * * *",
      "command": "./scripts/health-check.sh",
      "enabled": true,
      "runCount": 0
    },
    {
      "name": "日志清理",
      "cron": "0 3 * * *",
      "command": "./scripts/log.sh clean 7",
      "enabled": true,
      "runCount": 0
    }
  ]
}
EOF
        
        echo "调度器已初始化"
        ;;

    *)
        echo "用法: $0 [status|jobs|add|remove|pause|resume|trigger|init]"
        echo ""
        echo "  status              - 调度器状态"
        echo "  jobs                - 任务列表"
        echo "  add <名称> <cron> <命令> - 添加任务"
        echo "  remove <名称>       - 移除任务"
        echo "  pause <名称>        - 暂停任务"
        echo "  resume <名称>       - 恢复任务"
        echo "  trigger <名称>      - 立即触发"
        echo "  init                - 初始化调度器"
        exit 1
        ;;
esac
