#!/bin/bash
# Knowledge Repository 重试策略管理脚本
# 用法: ./scripts/retry.sh [status|config|stats|reset]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 重试策略"
echo "=========================================="
echo ""

ACTION=${1:-status}
CONFIG_FILE="./data/retry-config.json"

case "$ACTION" in
    status)
        echo "=== 重试策略状态 ==="
        echo ""
        
        if [ ! -f "$CONFIG_FILE" ]; then
            echo "未配置重试策略"
            echo ""
            echo "使用 '$0 init' 创建默认配置"
            exit 0
        fi
        
        python3 << 'EOF'
import json

with open('./data/retry-config.json', 'r') as f:
    config = json.load(f)

policies = config.get('policies', {})
if not policies:
    print("无重试策略")
    exit(0)

print(f"{'操作':<25} {'最大重试':<10} {'延迟':<10} {'当前重试次数':<15}")
print("-" * 60)

for name, policy in policies.items():
    max_retries = policy.get('maxRetries', 3)
    delay = policy.get('delay', 1)
    current = policy.get('currentRetries', 0)
    print(f"{name:<25} {max_retries:<10} {delay:<10}s {current:<15}")
EOF
        ;;

    config)
        echo "=== 重试配置 ==="
        echo ""
        
        if [ ! -f "$CONFIG_FILE" ]; then
            echo "未配置重试策略"
            exit 1
        fi
        
        cat "$CONFIG_FILE" | python3 -m json.tool
        ;;

    stats)
        echo "=== 重试统计 ==="
        echo ""
        
        if [ ! -f "$CONFIG_FILE" ]; then
            echo "未配置重试策略"
            exit 1
        fi
        
        python3 << 'EOF'
import json

with open('./data/retry-config.json', 'r') as f:
    config = json.load(f)

policies = config.get('policies', {})

total_retries = sum(p.get('totalRetries', 0) for p in policies.values())
total_success = sum(p.get('retrySuccess', 0) for p in policies.values())
total_failed = sum(p.get('retryFailed', 0) for p in policies.values())

print(f"总重试次数: {total_retries}")
print(f"重试成功: {total_success}")
print(f"重试失败: {total_failed}")

if total_retries > 0:
    print(f"\n重试成功率: {total_success/total_retries*100:.1f}%")
EOF
        ;;

    reset)
        echo "=== 重置重试统计 ==="
        echo ""
        
        if [ ! -f "$CONFIG_FILE" ]; then
            echo "未配置重试策略"
            exit 1
        fi
        
        python3 << 'EOF'
import json

with open('./data/retry-config.json', 'r') as f:
    config = json.load(f)

for policy in config.get('policies', {}).values():
    policy['currentRetries'] = 0
    policy['totalRetries'] = 0
    policy['retrySuccess'] = 0
    policy['retryFailed'] = 0

with open('./data/retry-config.json', 'w') as f:
    json.dump(config, f, indent=2)

print("统计已重置")
EOF
        ;;

    set)
        if [ -z "$2" ] || [ -z "$3" ]; then
            echo "用法: $0 set <操作名> <最大重试次数> [延迟秒数]"
            echo ""
            echo "示例:"
            echo "  $0 set milvus-query 5 2"
            exit 1
        fi
        
        OP=$2
        MAX=$3
        DELAY=${4:-1}
        
        if [ ! -f "$CONFIG_FILE" ]; then
            mkdir -p ./data
            echo '{"policies":{}}' > "$CONFIG_FILE"
        fi
        
        python3 << EOF
import json

with open('$CONFIG_FILE', 'r') as f:
    config = json.load(f)

policies = config.get('policies', {})
policies['$OP'] = {
    'maxRetries': $MAX,
    'delay': $DELAY,
    'currentRetries': 0,
    'totalRetries': 0,
    'retrySuccess': 0,
    'retryFailed': 0
}
config['policies'] = policies

with open('$CONFIG_FILE', 'w') as f:
    json.dump(config, f, indent=2)

print("策略已设置: $OP (最大重试: $MAX, 延迟: ${DELAY}s)")
EOF
        ;;

    init)
        echo "=== 初始化重试策略 ==="
        echo ""
        
        mkdir -p ./data
        cat > "$CONFIG_FILE" <<EOF
{
  "policies": {
    "milvus-query": {
      "maxRetries": 3,
      "delay": 1,
      "backoff": "exponential",
      "currentRetries": 0,
      "totalRetries": 0,
      "retrySuccess": 0,
      "retryFailed": 0
    },
    "milvus-insert": {
      "maxRetries": 5,
      "delay": 2,
      "backoff": "exponential",
      "currentRetries": 0,
      "totalRetries": 0,
      "retrySuccess": 0,
      "retryFailed": 0
    },
    "ollama-embedding": {
      "maxRetries": 3,
      "delay": 2,
      "backoff": "linear",
      "currentRetries": 0,
      "totalRetries": 0,
      "retrySuccess": 0,
      "retryFailed": 0
    },
    "file-extract": {
      "maxRetries": 2,
      "delay": 5,
      "backoff": "fixed",
      "currentRetries": 0,
      "totalRetries": 0,
      "retrySuccess": 0,
      "retryFailed": 0
    }
  },
  "defaults": {
    "maxRetries": 3,
    "delay": 1,
    "backoff": "exponential"
  }
}
EOF
        
        echo "重试策略已创建"
        ;;

    *)
        echo "用法: $0 [status|config|stats|reset|set|init]"
        echo ""
        echo "  status              - 查看策略状态"
        echo "  config              - 查看完整配置"
        echo "  stats               - 查看统计"
        echo "  reset               - 重置统计"
        echo "  set <操作> <次数> [延迟] - 设置策略"
        echo "  init                - 初始化配置"
        exit 1
        ;;
esac
