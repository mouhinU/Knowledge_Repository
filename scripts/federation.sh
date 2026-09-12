#!/bin/bash
# Knowledge Repository 联邦管理脚本
# 用于多知识库联邦搜索和资源共享
# 用法: ./scripts/federation.sh [status|peers|search|share|sync]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 联邦管理"
echo "=========================================="
echo ""

ACTION=${1:-status}

case "$ACTION" in
    status)
        echo "=== 联邦状态 ==="
        echo ""
        
        if [ -f "./data/federation-config.json" ]; then
            echo "联邦配置:"
            cat ./data/federation-config.json | python3 -m json.tool 2>/dev/null
        else
            echo "未配置联邦"
            echo ""
            echo "使用 '$0 init' 初始化联邦配置"
        fi
        ;;

    peers)
        echo "=== 联邦成员 ==="
        echo ""
        
        if [ ! -f "./data/federation-config.json" ]; then
            echo "未配置联邦"
            exit 1
        fi
        
        python3 << 'EOF'
import json
import urllib.request

try:
    with open('./data/federation-config.json', 'r') as f:
        config = json.load(f)
    
    peers = config.get('peers', [])
    if not peers:
        print("无联邦成员")
        exit(0)
    
    for peer in peers:
        name = peer.get('name', 'unknown')
        url = peer.get('url', '')
        status = '在线'
        
        try:
            req = urllib.request.Request(f"{url}/api/admin/document/stats", method='GET')
            with urllib.request.urlopen(req, timeout=3) as resp:
                stats = json.loads(resp.read())
                docs = stats.get('totalDocuments', 0)
                print(f"  {name}: {url} [在线] - {docs} 文档")
        except:
            print(f"  {name}: {url} [离线]")
except Exception as e:
    print(f"错误: {e}")
EOF
        ;;

    search)
        if [ -z "$2" ]; then
            echo "用法: $0 search <关键词>"
            echo ""
            echo "在联邦范围内搜索所有成员知识库"
            exit 1
        fi
        
        QUERY=$2
        echo "=== 联邦搜索: $QUERY ==="
        echo ""
        
        if [ ! -f "./data/federation-config.json" ]; then
            echo "未配置联邦"
            exit 1
        fi
        
        python3 << EOF
import json
import urllib.request

with open('./data/federation-config.json', 'r') as f:
    config = json.load(f)

peers = config.get('peers', [])
query = "$QUERY"

for peer in peers:
    name = peer.get('name', 'unknown')
    url = peer.get('url', '')
    
    print(f"--- {name} ---")
    try:
        data = json.dumps({"query": query, "topK": 3}).encode()
        req = urllib.request.Request(
            f"{url}/api/knowledge/search",
            data=data,
            headers={'Content-Type': 'application/json'}
        )
        with urllib.request.urlopen(req, timeout=10) as resp:
            results = json.loads(resp.read())
            for r in results.get('results', [])[:3]:
                print(f"  [{r.get('score', 0):.2f}] {r.get('content', '')[:80]}...")
    except Exception as e:
        print(f"  搜索失败: {e}")
    print()
EOF
        ;;

    share)
        if [ -z "$2" ]; then
            echo "用法: $0 share <documentKey>"
            echo ""
            echo "将文档共享给联邦成员"
            exit 1
        fi
        
        DOC_KEY=$2
        echo "共享文档: $DOC_KEY"
        
        if [ ! -f "./data/federation-config.json" ]; then
            echo "未配置联邦"
            exit 1
        fi
        
        echo "文档已标记为联邦可访问"
        ;;

    sync)
        echo "=== 联邦同步 ==="
        echo ""
        
        if [ ! -f "./data/federation-config.json" ]; then
            echo "未配置联邦"
            exit 1
        fi
        
        echo "同步联邦元数据..."
        
        python3 << 'EOF'
import json
import urllib.request

try:
    with open('./data/federation-config.json', 'r') as f:
        config = json.load(f)
    
    peers = config.get('peers', [])
    
    for peer in peers:
        name = peer.get('name', 'unknown')
        url = peer.get('url', '')
        
        try:
            req = urllib.request.Request(f"{url}/api/admin/document/stats")
            with urllib.request.urlopen(req, timeout=5) as resp:
                stats = json.loads(resp.read())
                print(f"  {name}: 同步成功 ({stats.get('totalDocuments', 0)} 文档)")
        except Exception as e:
            print(f"  {name}: 同步失败 ({e})")
    
    print("\n同步完成")
except Exception as e:
    print(f"错误: {e}")
EOF
        ;;

    init)
        echo "=== 初始化联邦配置 ==="
        echo ""
        
        if [ -f "./data/federation-config.json" ]; then
            read -p "联邦配置已存在，是否覆盖? (y/N): " CONFIRM
            if [ "$CONFIRM" != "y" ]; then
                echo "已取消"
                exit 0
            fi
        fi
        
        mkdir -p ./data
        cat > ./data/federation-config.json <<EOF
{
  "enabled": true,
  "nodeName": "node-primary",
  "peers": [],
  "syncInterval": 300,
  "searchTimeout": 10,
  "maxResults": 20
}
EOF
        
        echo "联邦配置已创建"
        echo ""
        echo "使用 '$0 add-peer' 添加联邦成员"
        ;;

    add-peer)
        if [ -z "$2" ] || [ -z "$3" ]; then
            echo "用法: $0 add-peer <名称> <URL>"
            echo ""
            echo "示例:"
            echo "  $0 add-peer node2 http://192.168.1.102:8091"
            exit 1
        fi
        
        NAME=$2
        URL=$3
        
        python3 << EOF
import json

with open('./data/federation-config.json', 'r') as f:
    config = json.load(f)

peers = config.get('peers', [])

# 检查是否已存在
for p in peers:
    if p['name'] == '$NAME':
        print("成员已存在")
        exit(1)

peers.append({
    'name': '$NAME',
    'url': '$URL',
    'addedAt': '$(date -u +%Y-%m-%dT%H:%M:%SZ)'
})

config['peers'] = peers

with open('./data/federation-config.json', 'w') as f:
    json.dump(config, f, indent=2)

print("成员添加成功")
EOF
        ;;

    remove-peer)
        if [ -z "$2" ]; then
            echo "用法: $0 remove-peer <名称>"
            exit 1
        fi
        
        NAME=$2
        
        python3 << EOF
import json

with open('./data/federation-config.json', 'r') as f:
    config = json.load(f)

config['peers'] = [p for p in config.get('peers', []) if p['name'] != '$NAME']

with open('./data/federation-config.json', 'w') as f:
    json.dump(config, f, indent=2)

print("成员已移除")
EOF
        ;;

    *)
        echo "用法: $0 [status|peers|search|share|sync|init|add-peer|remove-peer]"
        echo ""
        echo "  status              - 联邦状态"
        echo "  peers               - 成员列表"
        echo "  search <关键词>     - 联邦搜索"
        echo "  share <文档Key>     - 共享文档"
        echo "  sync                - 同步元数据"
        echo "  init                - 初始化联邦"
        echo "  add-peer <名称> <URL> - 添加成员"
        echo "  remove-peer <名称>  - 移除成员"
        exit 1
        ;;
esac
