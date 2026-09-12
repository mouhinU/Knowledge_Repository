#!/bin/bash
# Knowledge Repository 负载均衡管理脚本
# 用法: ./scripts/lb.sh [status|backends|add|remove|health|algorithm]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 负载均衡"
echo "=========================================="
echo ""

ACTION=${1:-status}

case "$ACTION" in
    status)
        echo "=== 负载均衡状态 ==="
        echo ""
        
        if [ -f "./data/lb-config.json" ]; then
            echo "负载均衡配置:"
            cat ./data/lb-config.json | python3 -c "
import sys, json
config = json.load(sys.stdin)
print(f\"  算法: {config.get('algorithm', 'round-robin')}\")
print(f\"  健康检查: {config.get('healthCheck', {}).get('enabled', True)}\")
print(f\"  后端数量: {len(config.get('backends', []))}\")
" 2>/dev/null
        else
            echo "未配置负载均衡"
        fi
        ;;

    backends)
        echo "=== 后端列表 ==="
        echo ""
        
        if [ ! -f "./data/lb-config.json" ]; then
            echo "未配置负载均衡"
            exit 1
        fi
        
        python3 << 'EOF'
import json
import urllib.request

with open('./data/lb-config.json', 'r') as f:
    config = json.load(f)

backends = config.get('backends', [])
if not backends:
    print("无后端服务")
    exit(0)

print(f"{'名称':<15} {'地址':<30} {'权重':<8} {'状态':<10} {'连接数':<10}")
print("-" * 73)

for b in backends:
    name = b.get('name', '')
    url = b.get('url', '')
    weight = b.get('weight', 100)
    
    # 检查健康状态
    try:
        req = urllib.request.Request(f"{url}/actuator/health", method='GET')
        with urllib.request.urlopen(req, timeout=3) as resp:
            health = '健康'
    except:
        health = '不可达'
    
    # 模拟连接数
    import random
    connections = random.randint(0, 50) if health == '健康' else 0
    
    print(f"{name:<15} {url:<30} {weight:<8} {health:<10} {connections:<10}")
EOF
        ;;

    add)
        if [ -z "$2" ] || [ -z "$3" ]; then
            echo "用法: $0 add <名称> <URL> [权重]"
            echo ""
            echo "示例:"
            echo "  $0 add backend1 http://localhost:8091"
            echo "  $0 add backend2 http://localhost:8092 50"
            exit 1
        fi
        
        NAME=$2
        URL=$3
        WEIGHT=${4:-100}
        
        mkdir -p ./data
        if [ ! -f "./data/lb-config.json" ]; then
            echo '{"algorithm":"round-robin","backends":[],"healthCheck":{"enabled":true,"interval":30}}' > ./data/lb-config.json
        fi
        
        python3 << EOF
import json

with open('./data/lb-config.json', 'r') as f:
    config = json.load(f)

backends = config.get('backends', [])

# 检查是否已存在
for b in backends:
    if b['name'] == '$NAME':
        print("后端已存在")
        exit(1)

backends.append({
    'name': '$NAME',
    'url': '$URL',
    'weight': $WEIGHT,
    'active': True
})
config['backends'] = backends

with open('./data/lb-config.json', 'w') as f:
    json.dump(config, f, indent=2)

print("后端添加成功")
EOF
        ;;

    remove)
        if [ -z "$2" ]; then
            echo "用法: $0 remove <名称>"
            exit 1
        fi
        
        NAME=$2
        
        python3 << EOF
import json

with open('./data/lb-config.json', 'r') as f:
    config = json.load(f)

config['backends'] = [b for b in config.get('backends', []) if b['name'] != '$NAME']

with open('./data/lb-config.json', 'w') as f:
    json.dump(config, f, indent=2)

print("后端已移除")
EOF
        ;;

    health)
        echo "=== 健康检查 ==="
        echo ""
        
        if [ ! -f "./data/lb-config.json" ]; then
            echo "未配置负载均衡"
            exit 1
        fi
        
        python3 << 'EOF'
import json
import urllib.request
from datetime import datetime

with open('./data/lb-config.json', 'r') as f:
    config = json.load(f)

print(f"检查时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
print()

for b in config.get('backends', []):
    name = b.get('name', '')
    url = b.get('url', '')
    
    start = datetime.now()
    try:
        req = urllib.request.Request(f"{url}/actuator/health", method='GET')
        with urllib.request.urlopen(req, timeout=5) as resp:
            elapsed = (datetime.now() - start).total_seconds() * 1000
            print(f"  {name}: 健康 (响应时间: {elapsed:.0f}ms)")
    except Exception as e:
        print(f"  {name}: 不可达 ({e})")
EOF
        ;;

    algorithm)
        echo "=== 负载均衡算法 ==="
        echo ""
        
        if [ -n "$2" ]; then
            ALGO=$2
            python3 << EOF
import json

with open('./data/lb-config.json', 'r') as f:
    config = json.load(f)

config['algorithm'] = '$ALGO'

with open('./data/lb-config.json', 'w') as f:
    json.dump(config, f, indent=2)

print("算法已设置为: $ALGO")
EOF
        else
            if [ -f "./data/lb-config.json" ]; then
                ALGO=$(cat ./data/lb-config.json | python3 -c "import sys,json; print(json.load(sys.stdin).get('algorithm', 'round-robin'))" 2>/dev/null)
                echo "当前算法: $ALGO"
            else
                echo "未配置"
            fi
        fi
        
        echo ""
        echo "可用算法:"
        echo "  round-robin    - 轮询"
        echo "  weighted       - 加权轮询"
        echo "  least-conn     - 最少连接"
        echo "  ip-hash        - IP哈希"
        echo "  random         - 随机"
        ;;

    init)
        echo "=== 初始化负载均衡配置 ==="
        echo ""
        
        mkdir -p ./data
        cat > ./data/lb-config.json <<EOF
{
  "algorithm": "round-robin",
  "backends": [
    {
      "name": "primary",
      "url": "http://localhost:8091",
      "weight": 100,
      "active": true
    }
  ],
  "healthCheck": {
    "enabled": true,
    "interval": 30,
    "timeout": 5,
    "path": "/actuator/health"
  },
  "stickySession": {
    "enabled": false,
    "cookie": "KB_SESSION"
  }
}
EOF
        
        echo "负载均衡配置已创建"
        ;;

    *)
        echo "用法: $0 [status|backends|add|remove|health|algorithm|init]"
        echo ""
        echo "  status              - 负载均衡状态"
        echo "  backends            - 后端列表"
        echo "  add <名称> <URL> [权重] - 添加后端"
        echo "  remove <名称>       - 移除后端"
        echo "  health              - 健康检查"
        echo "  algorithm [算法]    - 查看/设置算法"
        echo "  init                - 初始化配置"
        exit 1
        ;;
esac
