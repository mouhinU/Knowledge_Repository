#!/bin/bash
# Knowledge Repository 服务发现脚本
# 用法: ./scripts/discovery.sh [register|deregister|list|watch|health]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 服务发现"
echo "=========================================="
echo ""

ACTION=${1:-list}
REGISTRY_FILE="./data/service-registry.json"

case "$ACTION" in
    register)
        if [ -z "$2" ] || [ -z "$3" ]; then
            echo "用法: $0 register <服务名> <地址:端口>"
            echo ""
            echo "示例:"
            echo "  $0 register knowledge-service localhost:8091"
            exit 1
        fi
        
        SERVICE=$2
        ADDRESS=$3
        
        mkdir -p ./data
        if [ ! -f "$REGISTRY_FILE" ]; then
            echo '{"services":{}}' > "$REGISTRY_FILE"
        fi
        
        python3 << EOF
import json
from datetime import datetime

with open('$REGISTRY_FILE', 'r') as f:
    registry = json.load(f)

services = registry.get('services', {})
services['$SERVICE'] = {
    'address': '$ADDRESS',
    'registeredAt': datetime.now().isoformat(),
    'lastHeartbeat': datetime.now().isoformat(),
    'status': 'healthy',
    'metadata': {}
}
registry['services'] = services

with open('$REGISTRY_FILE', 'w') as f:
    json.dump(registry, f, indent=2)

print("服务注册成功: $SERVICE -> $ADDRESS")
EOF
        ;;

    deregister)
        if [ -z "$2" ]; then
            echo "用法: $0 deregister <服务名>"
            exit 1
        fi
        
        SERVICE=$2
        
        python3 << EOF
import json

with open('$REGISTRY_FILE', 'r') as f:
    registry = json.load(f)

if '$SERVICE' in registry.get('services', {}):
    del registry['services']['$SERVICE']
    with open('$REGISTRY_FILE', 'w') as f:
        json.dump(registry, f, indent=2)
    print("服务已注销: $SERVICE")
else:
    print("服务不存在: $SERVICE")
EOF
        ;;

    list)
        echo "=== 已注册服务 ==="
        echo ""
        
        if [ ! -f "$REGISTRY_FILE" ]; then
            echo "无注册服务"
            exit 0
        fi
        
        python3 << 'EOF'
import json
from datetime import datetime

with open('./data/service-registry.json', 'r') as f:
    registry = json.load(f)

services = registry.get('services', {})
if not services:
    print("无注册服务")
    exit(0)

print(f"{'服务名':<25} {'地址':<30} {'状态':<10} {'最后心跳':<20}")
print("-" * 85)

for name, info in services.items():
    address = info.get('address', '')
    status = info.get('status', 'unknown')
    heartbeat = info.get('lastHeartbeat', '')[:19]
    print(f"{name:<25} {address:<30} {status:<10} {heartbeat:<20}")
EOF
        ;;

    watch)
        echo "=== 服务监控 (Ctrl+C 退出) ==="
        echo ""
        
        if [ ! -f "$REGISTRY_FILE" ]; then
            echo "无注册服务"
            exit 1
        fi
        
        while true; do
            clear
            echo "=== 服务监控 $(date '+%H:%M:%S') ==="
            echo ""
            
            python3 << 'EOF'
import json
import urllib.request

with open('./data/service-registry.json', 'r') as f:
    registry = json.load(f)

for name, info in registry.get('services', {}).items():
    address = info.get('address', '')
    host, port = address.split(':') if ':' in address else (address, '8091')
    
    # 检查健康状态
    try:
        req = urllib.request.Request(f"http://{address}/actuator/health", method='GET')
        with urllib.request.urlopen(req, timeout=3) as resp:
            status = '● 健康'
    except:
        status = '○ 不可达'
    
    print(f"  {name:<25} {address:<30} {status}")
EOF
            
            sleep 5
        done
        ;;

    health)
        echo "=== 服务健康检查 ==="
        echo ""
        
        if [ ! -f "$REGISTRY_FILE" ]; then
            echo "无注册服务"
            exit 1
        fi
        
        python3 << 'EOF'
import json
import urllib.request
from datetime import datetime

with open('./data/service-registry.json', 'r') as f:
    registry = json.load(f)

print(f"检查时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
print()

healthy = 0
unhealthy = 0

for name, info in registry.get('services', {}).items():
    address = info.get('address', '')
    
    start = datetime.now()
    try:
        req = urllib.request.Request(f"http://{address}/actuator/health", method='GET')
        with urllib.request.urlopen(req, timeout=5) as resp:
            elapsed = (datetime.now() - start).total_seconds() * 1000
            print(f"  ● {name:<20} {address:<30} 健康 ({elapsed:.0f}ms)")
            healthy += 1
    except Exception as e:
        print(f"  ○ {name:<20} {address:<30} 不可达")
        unhealthy += 1

print()
print(f"健康: {healthy}  不可达: {unhealthy}")
EOF
        ;;

    resolve)
        if [ -z "$2" ]; then
            echo "用法: $0 resolve <服务名>"
            echo ""
            echo "解析服务地址"
            exit 1
        fi
        
        SERVICE=$2
        
        if [ ! -f "$REGISTRY_FILE" ]; then
            echo "无注册服务"
            exit 1
        fi
        
        python3 << EOF
import json

with open('$REGISTRY_FILE', 'r') as f:
    registry = json.load(f)

service = registry.get('services', {}).get('$SERVICE')
if service:
    print(service.get('address', ''))
else:
    print("服务不存在: $SERVICE")
    exit(1)
EOF
        ;;

    init)
        echo "=== 初始化服务注册表 ==="
        echo ""
        
        mkdir -p ./data
        echo '{"services":{}}' > "$REGISTRY_FILE"
        
        echo "服务注册表已创建"
        echo ""
        echo "使用 '$0 register' 注册服务"
        ;;

    *)
        echo "用法: $0 [register|deregister|list|watch|health|resolve|init]"
        echo ""
        echo "  register <名称> <地址> - 注册服务"
        echo "  deregister <名称>      - 注销服务"
        echo "  list                   - 列出所有服务"
        echo "  watch                  - 实时监控"
        echo "  health                 - 健康检查"
        echo "  resolve <名称>         - 解析服务地址"
        echo "  init                   - 初始化注册表"
        exit 1
        ;;
esac
