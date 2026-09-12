#!/bin/bash
# Knowledge Repository 集群管理脚本
# 用法: ./scripts/cluster.sh [status|nodes|add|remove|balance]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 集群管理"
echo "=========================================="
echo ""

ACTION=${1:-status}

case "$ACTION" in
    status)
        echo "=== 集群状态 ==="
        echo ""
        
        # 检查本地节点
        echo "本地节点:"
        PORT=8091
        PID=$(lsof -i :$PORT -t 2>/dev/null)
        if [ -n "$PID" ]; then
            echo "  状态: 运行中"
            echo "  PID:  $PID"
            echo "  端口: $PORT"
        else
            echo "  状态: 未运行"
        fi
        
        echo ""
        echo "集群节点:"
        if [ -f "./data/cluster-config.json" ]; then
            cat ./data/cluster-config.json | python3 -m json.tool 2>/dev/null
        else
            echo "  未配置集群"
        fi
        ;;

    nodes)
        echo "=== 集群节点列表 ==="
        echo ""
        
        if [ ! -f "./data/cluster-config.json" ]; then
            echo "未配置集群"
            exit 1
        fi
        
        NODES=$(cat ./data/cluster-config.json | python3 -c "
import sys, json
data = json.load(sys.stdin)
for node in data.get('nodes', []):
    status = '在线' if node.get('active') else '离线'
    print(f\"  {node['name']}: {node['host']}:{node['port']} [{status}]\")
" 2>/dev/null)
        
        if [ -n "$NODES" ]; then
            echo "$NODES"
        else
            echo "无节点"
        fi
        ;;

    add)
        if [ -z "$2" ] || [ -z "$3" ]; then
            echo "用法: $0 add <节点名称> <host:port>"
            echo ""
            echo "示例:"
            echo "  $0 add node2 192.168.1.102:8091"
            exit 1
        fi
        
        NAME=$2
        HOST_PORT=$3
        HOST=$(echo $HOST_PORT | cut -d: -f1)
        PORT=$(echo $HOST_PORT | cut -d: -f2)
        
        echo "添加节点: $NAME ($HOST:$PORT)"
        
        # 创建或更新配置
        mkdir -p ./data
        if [ ! -f "./data/cluster-config.json" ]; then
            echo '{"nodes":[]}' > ./data/cluster-config.json
        fi
        
        python3 << EOF
import json

with open('./data/cluster-config.json', 'r') as f:
    config = json.load(f)

# 检查是否已存在
for node in config['nodes']:
    if node['name'] == '$NAME':
        print("节点已存在")
        exit(1)

# 添加新节点
config['nodes'].append({
    'name': '$NAME',
    'host': '$HOST',
    'port': $PORT,
    'active': True
})

with open('./data/cluster-config.json', 'w') as f:
    json.dump(config, f, indent=2)

print("节点添加成功")
EOF
        ;;

    remove)
        if [ -z "$2" ]; then
            echo "用法: $0 remove <节点名称>"
            exit 1
        fi
        
        NAME=$2
        
        read -p "确认移除节点 $NAME? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        python3 << EOF
import json

with open('./data/cluster-config.json', 'r') as f:
    config = json.load(f)

config['nodes'] = [n for n in config['nodes'] if n['name'] != '$NAME']

with open('./data/cluster-config.json', 'w') as f:
    json.dump(config, f, indent=2)

print("节点已移除")
EOF
        ;;

    balance)
        echo "=== 负载均衡状态 ==="
        echo ""
        
        if [ ! -f "./data/cluster-config.json" ]; then
            echo "未配置集群"
            exit 1
        fi
        
        echo "检查各节点负载..."
        
        python3 << EOF
import json
import urllib.request

with open('./data/cluster-config.json', 'r') as f:
    config = json.load(f)

for node in config['nodes']:
    url = f"http://{node['host']}:{node['port']}/api/admin/document/stats"
    try:
        with urllib.request.urlopen(url, timeout=3) as response:
            stats = json.loads(response.read())
            print(f"  {node['name']}: 文档数 {stats.get('totalDocuments', 0)}")
    except:
        print(f"  {node['name']}: 无法连接")
EOF
        ;;

    init)
        echo "=== 初始化集群 ==="
        echo ""
        
        read -p "是否创建新的集群配置? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        mkdir -p ./data
        cat > ./data/cluster-config.json <<EOF
{
  "clusterName": "knowledge-cluster",
  "nodes": [
    {
      "name": "node1",
      "host": "localhost",
      "port": 8091,
      "active": true
    }
  ],
  "replicationFactor": 1,
  "syncInterval": 60
}
EOF
        
        echo "集群配置已创建"
        echo "使用 '$0 add' 添加更多节点"
        ;;

    *)
        echo "用法: $0 [status|nodes|add|remove|balance|init]"
        echo ""
        echo "  status              - 集群状态"
        echo "  nodes               - 节点列表"
        echo "  add <名称> <host:port> - 添加节点"
        echo "  remove <名称>       - 移除节点"
        echo "  balance             - 负载均衡"
        echo "  init                - 初始化集群"
        exit 1
        ;;
esac
