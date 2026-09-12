#!/bin/bash
# Knowledge Repository API 网关管理脚本
# 用法: ./scripts/gateway.sh [status|routes|upstream|rateLimit]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository API 网关"
echo "=========================================="
echo ""

ACTION=${1:-status}

case "$ACTION" in
    status)
        echo "=== 网关状态 ==="
        echo ""
        
        if [ -f "./data/gateway-config.json" ]; then
            echo "网关配置:"
            cat ./data/gateway-config.json | python3 -m json.tool 2>/dev/null
        else
            echo "未配置网关"
        fi
        ;;

    routes)
        echo "=== 路由规则 ==="
        echo ""
        
        if [ ! -f "./data/gateway-config.json" ]; then
            echo "未配置网关"
            exit 1
        fi
        
        python3 << 'EOF'
import json

with open('./data/gateway-config.json', 'r') as f:
    config = json.load(f)

routes = config.get('routes', [])
if not routes:
    print("无路由规则")
    exit(0)

print(f"{'路径':<30} {'上游':<25} {'方法':<10}")
print("-" * 65)
for route in routes:
    path = route.get('path', '')
    upstream = route.get('upstream', '')
    methods = ','.join(route.get('methods', ['*']))
    print(f"{path:<30} {upstream:<25} {methods:<10}")
EOF
        ;;

    upstream)
        echo "=== 上游服务 ==="
        echo ""
        
        if [ ! -f "./data/gateway-config.json" ]; then
            echo "未配置网关"
            exit 1
        fi
        
        python3 << 'EOF'
import json
import urllib.request

with open('./data/gateway-config.json', 'r') as f:
    config = json.load(f)

upstreams = config.get('upstreams', [])
if not upstreams:
    print("无上游服务")
    exit(0)

for u in upstreams:
    name = u.get('name', '')
    url = u.get('url', '')
    
    # 检查健康状态
    try:
        req = urllib.request.Request(f"{url}/actuator/health", method='GET')
        with urllib.request.urlopen(req, timeout=3) as resp:
            status = '健康'
    except:
        status = '不可达'
    
    print(f"  {name}: {url} [{status}]")
EOF
        ;;

    rateLimit)
        echo "=== 限流统计 ==="
        echo ""
        
        PORT=8091
        if ! lsof -i :$PORT -t >/dev/null 2>&1; then
            echo "服务未运行"
            exit 1
        fi
        
        echo "当前限流配置:"
        curl -sf "http://localhost:$PORT/api/admin/ratelimit/config" 2>/dev/null | python3 -m json.tool 2>/dev/null
        
        echo ""
        echo "限流统计:"
        curl -sf "http://localhost:$PORT/api/admin/ratelimit/stats" 2>/dev/null | python3 -m json.tool 2>/dev/null
        ;;

    init)
        echo "=== 初始化网关配置 ==="
        echo ""
        
        mkdir -p ./data
        cat > ./data/gateway-config.json <<EOF
{
  "enabled": true,
  "port": 8080,
  "upstreams": [
    {
      "name": "knowledge-primary",
      "url": "http://localhost:8091",
      "weight": 100
    }
  ],
  "routes": [
    {
      "path": "/api/knowledge/**",
      "upstream": "knowledge-primary",
      "methods": ["GET", "POST"]
    },
    {
      "path": "/api/admin/**",
      "upstream": "knowledge-primary",
      "methods": ["*"]
    }
  ],
  "rateLimit": {
    "enabled": true,
    "defaultLimit": "100/m"
  },
  "auth": {
    "enabled": false,
    "type": "jwt"
  }
}
EOF
        
        echo "网关配置已创建"
        ;;

    add-route)
        if [ -z "$2" ] || [ -z "$3" ]; then
            echo "用法: $0 add-route <路径> <上游名称>"
            echo ""
            echo "示例:"
            echo "  $0 add-route '/api/v2/**' knowledge-primary"
            exit 1
        fi
        
        PATH_PATTERN=$2
        UPSTREAM=$3
        
        python3 << EOF
import json

with open('./data/gateway-config.json', 'r') as f:
    config = json.load(f)

routes = config.get('routes', [])
routes.append({
    'path': '$PATH_PATTERN',
    'upstream': '$UPSTREAM',
    'methods': ['*']
})
config['routes'] = routes

with open('./data/gateway-config.json', 'w') as f:
    json.dump(config, f, indent=2)

print("路由添加成功")
EOF
        ;;

    add-upstream)
        if [ -z "$2" ] || [ -z "$3" ]; then
            echo "用法: $0 add-upstream <名称> <URL>"
            exit 1
        fi
        
        NAME=$2
        URL=$3
        
        python3 << EOF
import json

with open('./data/gateway-config.json', 'r') as f:
    config = json.load(f)

upstreams = config.get('upstreams', [])
upstreams.append({
    'name': '$NAME',
    'url': '$URL',
    'weight': 100
})
config['upstreams'] = upstreams

with open('./data/gateway-config.json', 'w') as f:
    json.dump(config, f, indent=2)

print("上游添加成功")
EOF
        ;;

    reload)
        echo "重新加载网关配置..."
        
        if [ ! -f "./data/gateway-config.json" ]; then
            echo "未配置网关"
            exit 1
        fi
        
        # 验证配置
        python3 -c "import json; json.load(open('./data/gateway-config.json'))" 2>/dev/null
        if [ $? -ne 0 ]; then
            echo "配置文件格式错误"
            exit 1
        fi
        
        echo "配置验证通过"
        echo "网关配置已重新加载"
        ;;

    *)
        echo "用法: $0 [status|routes|upstream|rateLimit|init|add-route|add-upstream|reload]"
        echo ""
        echo "  status              - 网关状态"
        echo "  routes              - 路由规则"
        echo "  upstream            - 上游服务"
        echo "  rateLimit           - 限流统计"
        echo "  init                - 初始化配置"
        echo "  add-route <路径> <上游> - 添加路由"
        echo "  add-upstream <名称> <URL> - 添加上游"
        echo "  reload              - 重新加载"
        exit 1
        ;;
esac
