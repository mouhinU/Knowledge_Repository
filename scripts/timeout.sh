#!/bin/bash
# Knowledge Repository 超时配置管理脚本
# 用法: ./scripts/timeout.sh [status|config|set|stats]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 超时配置"
echo "=========================================="
echo ""

ACTION=${1:-status}
CONFIG_FILE="./data/timeout-config.json"

case "$ACTION" in
    status)
        echo "=== 超时配置状态 ==="
        echo ""
        
        if [ ! -f "$CONFIG_FILE" ]; then
            echo "未配置超时策略"
            echo ""
            echo "使用 '$0 init' 创建默认配置"
            exit 0
        fi
        
        python3 << 'EOF'
import json

with open('./data/timeout-config.json', 'r') as f:
    config = json.load(f)

timeouts = config.get('timeouts', {})
if not timeouts:
    print("无超时配置")
    exit(0)

print(f"{'操作':<25} {'连接超时':<12} {'读取超时':<12} {'写入超时':<12}")
print("-" * 61)

for name, t in timeouts.items():
    connect = t.get('connect', 5)
    read = t.get('read', 30)
    write = t.get('write', 30)
    print(f"{name:<25} {connect:<12}s {read:<12}s {write:<12}s")
EOF
        ;;

    config)
        echo "=== 超时完整配置 ==="
        echo ""
        
        if [ ! -f "$CONFIG_FILE" ]; then
            echo "未配置超时策略"
            exit 1
        fi
        
        cat "$CONFIG_FILE" | python3 -m json.tool
        ;;

    set)
        if [ -z "$2" ]; then
            echo "用法: $0 set <操作名> <连接超时> <读取超时> [写入超时]"
            echo ""
            echo "示例:"
            echo "  $0 set milvus-query 5 30 30"
            echo "  $0 set file-upload 10 300 300"
            exit 1
        fi
        
        OP=$2
        CONNECT=$3
        READ=$4
        WRITE=${5:-$READ}
        
        if [ ! -f "$CONFIG_FILE" ]; then
            mkdir -p ./data
            echo '{"timeouts":{}}' > "$CONFIG_FILE"
        fi
        
        python3 << EOF
import json

with open('$CONFIG_FILE', 'r') as f:
    config = json.load(f)

timeouts = config.get('timeouts', {})
timeouts['$OP'] = {
    'connect': $CONNECT,
    'read': $READ,
    'write': $WRITE
}
config['timeouts'] = timeouts

with open('$CONFIG_FILE', 'w') as f:
    json.dump(config, f, indent=2)

print("超时已设置: $OP (连接: ${CONNECT}s, 读取: ${READ}s, 写入: ${WRITE}s)")
EOF
        ;;

    stats)
        echo "=== 超时统计 ==="
        echo ""
        
        if [ ! -f "$CONFIG_FILE" ]; then
            echo "未配置超时策略"
            exit 1
        fi
        
        python3 << 'EOF'
import json

with open('./data/timeout-config.json', 'r') as f:
    config = json.load(f)

timeouts = config.get('timeouts', {})

total_timeouts = sum(t.get('timeoutCount', 0) for t in timeouts.values())
total_requests = sum(t.get('requestCount', 0) for t in timeouts.values())

print(f"总请求数: {total_requests}")
print(f"超时次数: {total_timeouts}")

if total_requests > 0:
    print(f"超时率: {total_timeouts/total_requests*100:.2f}%")

print()
print("各操作超时统计:")
for name, t in timeouts.items():
    count = t.get('timeoutCount', 0)
    total = t.get('requestCount', 0)
    if total > 0:
        rate = count / total * 100
        print(f"  {name}: {count}/{total} ({rate:.2f}%)")
EOF
        ;;

    reset)
        echo "=== 重置超时统计 ==="
        echo ""
        
        if [ ! -f "$CONFIG_FILE" ]; then
            echo "未配置超时策略"
            exit 1
        fi
        
        python3 << 'EOF'
import json

with open('./data/timeout-config.json', 'r') as f:
    config = json.load(f)

for t in config.get('timeouts', {}).values():
    t['timeoutCount'] = 0
    t['requestCount'] = 0

with open('./data/timeout-config.json', 'w') as f:
    json.dump(config, f, indent=2)

print("统计已重置")
EOF
        ;;

    init)
        echo "=== 初始化超时配置 ==="
        echo ""
        
        mkdir -p ./data
        cat > "$CONFIG_FILE" <<EOF
{
  "timeouts": {
    "milvus-query": {
      "connect": 5,
      "read": 30,
      "write": 30,
      "timeoutCount": 0,
      "requestCount": 0
    },
    "milvus-insert": {
      "connect": 5,
      "read": 60,
      "write": 60,
      "timeoutCount": 0,
      "requestCount": 0
    },
    "ollama-embedding": {
      "connect": 5,
      "read": 120,
      "write": 120,
      "timeoutCount": 0,
      "requestCount": 0
    },
    "file-upload": {
      "connect": 10,
      "read": 300,
      "write": 300,
      "timeoutCount": 0,
      "requestCount": 0
    },
    "file-extract": {
      "connect": 5,
      "read": 600,
      "write": 600,
      "timeoutCount": 0,
      "requestCount": 0
    },
    "http-default": {
      "connect": 5,
      "read": 30,
      "write": 30,
      "timeoutCount": 0,
      "requestCount": 0
    }
  },
  "defaults": {
    "connect": 5,
    "read": 30,
    "write": 30
  }
}
EOF
        
        echo "超时配置已创建"
        ;;

    *)
        echo "用法: $0 [status|config|set|stats|reset|init]"
        echo ""
        echo "  status              - 查看超时状态"
        echo "  config              - 查看完整配置"
        echo "  set <操作> <连接> <读取> [写入] - 设置超时"
        echo "  stats               - 查看统计"
        echo "  reset               - 重置统计"
        echo "  init                - 初始化配置"
        exit 1
        ;;
esac
