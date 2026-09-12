#!/bin/bash
# Knowledge Repository 熔断器管理脚本
# 用法: ./scripts/circuit.sh [status|state|reset|config|stats]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 熔断器管理"
echo "=========================================="
echo ""

ACTION=${1:-status}
CONFIG_FILE="./data/circuit-breaker.json"

case "$ACTION" in
    status)
        echo "=== 熔断器状态 ==="
        echo ""
        
        if [ ! -f "$CONFIG_FILE" ]; then
            echo "未配置熔断器"
            echo ""
            echo "使用 '$0 init' 创建默认配置"
            exit 0
        fi
        
        python3 << 'EOF'
import json

with open('./data/circuit-breaker.json', 'r') as f:
    config = json.load(f)

circuits = config.get('circuits', {})
if not circuits:
    print("无熔断器配置")
    exit(0)

print(f"{'名称':<25} {'状态':<15} {'失败次数':<12} {'最后失败':<20}")
print("-" * 72)

for name, info in circuits.items():
    state = info.get('state', 'CLOSED')
    failures = info.get('failureCount', 0)
    last_failure = info.get('lastFailureTime', '-')[:19]
    
    # 状态图标
    if state == 'CLOSED':
        state_display = '● 关闭(正常)'
    elif state == 'OPEN':
        state_display = '○ 打开(熔断)'
    else:
        state_display = '◐ 半开'
    
    print(f"{name:<25} {state_display:<15} {failures:<12} {last_failure:<20}")
EOF
        ;;

    state)
        if [ -z "$2" ]; then
            echo "用法: $0 state <熔断器名称>"
            echo ""
            echo "查看指定熔断器的详细状态"
            exit 1
        fi
        
        NAME=$2
        
        if [ ! -f "$CONFIG_FILE" ]; then
            echo "未配置熔断器"
            exit 1
        fi
        
        python3 << EOF
import json

with open('$CONFIG_FILE', 'r') as f:
    config = json.load(f)

circuit = config.get('circuits', {}).get('$NAME')
if not circuit:
    print("熔断器不存在: $NAME")
    exit(1)

print(f"熔断器: $NAME")
print(f"状态: {circuit.get('state', 'UNKNOWN')}")
print(f"失败次数: {circuit.get('failureCount', 0)}")
print(f"成功次数: {circuit.get('successCount', 0)}")
print(f"最后失败: {circuit.get('lastFailureTime', '-')}")
print(f"最后成功: {circuit.get('lastSuccessTime', '-')}")
EOF
        ;;

    reset)
        if [ -z "$2" ]; then
            echo "用法: $0 reset <熔断器名称|all>"
            echo ""
            echo "重置熔断器状态"
            exit 1
        fi
        
        NAME=$2
        
        if [ ! -f "$CONFIG_FILE" ]; then
            echo "未配置熔断器"
            exit 1
        fi
        
        if [ "$NAME" = "all" ]; then
            python3 << 'EOF'
import json
from datetime import datetime

with open('./data/circuit-breaker.json', 'r') as f:
    config = json.load(f)

for name in config.get('circuits', {}):
    config['circuits'][name]['state'] = 'CLOSED'
    config['circuits'][name]['failureCount'] = 0
    config['circuits'][name]['resetTime'] = datetime.now().isoformat()

with open('./data/circuit-breaker.json', 'w') as f:
    json.dump(config, f, indent=2)

print("所有熔断器已重置")
EOF
        else
            python3 << EOF
import json
from datetime import datetime

with open('$CONFIG_FILE', 'r') as f:
    config = json.load(f)

if '$NAME' not in config.get('circuits', {}):
    print("熔断器不存在: $NAME")
    exit(1)

config['circuits']['$NAME']['state'] = 'CLOSED'
config['circuits']['$NAME']['failureCount'] = 0
config['circuits']['$NAME']['resetTime'] = datetime.now().isoformat()

with open('$CONFIG_FILE', 'w') as f:
    json.dump(config, f, indent=2)

print("熔断器已重置: $NAME")
EOF
        fi
        ;;

    config)
        echo "=== 熔断器配置 ==="
        echo ""
        
        if [ ! -f "$CONFIG_FILE" ]; then
            echo "未配置熔断器"
            exit 1
        fi
        
        cat "$CONFIG_FILE" | python3 -m json.tool
        ;;

    stats)
        echo "=== 熔断器统计 ==="
        echo ""
        
        if [ ! -f "$CONFIG_FILE" ]; then
            echo "未配置熔断器"
            exit 1
        fi
        
        python3 << 'EOF'
import json

with open('./data/circuit-breaker.json', 'r') as f:
    config = json.load(f)

circuits = config.get('circuits', {})

total = len(circuits)
closed = sum(1 for c in circuits.values() if c.get('state') == 'CLOSED')
open_count = sum(1 for c in circuits.values() if c.get('state') == 'OPEN')
half_open = sum(1 for c in circuits.values() if c.get('state') == 'HALF_OPEN')

print(f"总数: {total}")
print(f"关闭(正常): {closed}")
print(f"打开(熔断): {open_count}")
print(f"半开: {half_open}")

if total > 0:
    print(f"\n健康率: {closed/total*100:.1f}%")
EOF
        ;;

    init)
        echo "=== 初始化熔断器配置 ==="
        echo ""
        
        mkdir -p ./data
        cat > "$CONFIG_FILE" <<EOF
{
  "circuits": {
    "milvus-client": {
      "state": "CLOSED",
      "failureCount": 0,
      "successCount": 0,
      "failureThreshold": 5,
      "successThreshold": 3,
      "timeout": 30
    },
    "ollama-client": {
      "state": "CLOSED",
      "failureCount": 0,
      "successCount": 0,
      "failureThreshold": 5,
      "successThreshold": 3,
      "timeout": 30
    },
    "storage-client": {
      "state": "CLOSED",
      "failureCount": 0,
      "successCount": 0,
      "failureThreshold": 5,
      "successThreshold": 3,
      "timeout": 30
    }
  },
  "defaults": {
    "failureThreshold": 5,
    "successThreshold": 3,
    "timeout": 30
  }
}
EOF
        
        echo "熔断器配置已创建"
        ;;

    *)
        echo "用法: $0 [status|state|reset|config|stats|init]"
        echo ""
        echo "  status              - 查看所有熔断器状态"
        echo "  state <名称>        - 查看详细状态"
        echo "  reset <名称|all>    - 重置熔断器"
        echo "  config              - 查看配置"
        echo "  stats               - 统计信息"
        echo "  init                - 初始化配置"
        exit 1
        ;;
esac
