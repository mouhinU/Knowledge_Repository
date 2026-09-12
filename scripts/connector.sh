#!/bin/bash
# Knowledge Repository 连接器管理脚本
# 用法: ./scripts/connector.sh [list|test|add|remove|config]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 连接器管理"
echo "=========================================="
echo ""

ACTION=${1:-list}
CONNECTOR_FILE="./data/connectors.json"

case "$ACTION" in
    list)
        echo "=== 连接器列表 ==="
        echo ""
        
        if [ ! -f "$CONNECTOR_FILE" ]; then
            echo "无自定义连接器"
            echo ""
            echo "内置连接器:"
            echo "  ● milvus      - Milvus 向量数据库"
            echo "  ● ollama      - Ollama Embedding"
            echo "  ● local       - 本地文件存储"
            exit 0
        fi
        
        python3 << 'EOF'
import json

with open('./data/connectors.json', 'r') as f:
    config = json.load(f)

connectors = config.get('connectors', [])
if not connectors:
    print("无自定义连接器")
    exit(0)

print(f"{'名称':<20} {'类型':<15} {'状态':<10} {'地址':<30}")
print("-" * 75)

for c in connectors:
    name = c.get('name', '-')
    ctype = c.get('type', '-')
    status = '● 启用' if c.get('enabled', True) else '○ 禁用'
    url = c.get('url', '-')
    print(f"{name:<20} {ctype:<15} {status:<10} {url:<30}")
EOF
        ;;

    test)
        if [ -z "$2" ]; then
            echo "用法: $0 test <连接器名称>"
            exit 1
        fi
        
        NAME=$2
        
        echo "测试连接器: $NAME"
        echo ""
        
        case "$NAME" in
            milvus)
                echo -n "Milvus: "
                if curl -sf "http://localhost:9091/healthz" >/dev/null 2>&1; then
                    echo "● 连接成功"
                else
                    echo "○ 连接失败"
                fi
                ;;
            ollama)
                echo -n "Ollama: "
                if curl -sf "http://localhost:11434/api/tags" >/dev/null 2>&1; then
                    echo "● 连接成功"
                else
                    echo "○ 连接失败"
                fi
                ;;
            *)
                if [ -f "$CONNECTOR_FILE" ]; then
                    python3 << EOF
import json
import urllib.request

with open('$CONNECTOR_FILE', 'r') as f:
    config = json.load(f)

for c in config.get('connectors', []):
    if c['name'] == '$NAME':
        url = c.get('url', '')
        try:
            req = urllib.request.Request(url, method='GET')
            with urllib.request.urlopen(req, timeout=5) as resp:
                print("● 连接成功")
        except Exception as e:
            print(f"○ 连接失败: {e}")
        break
else:
    print("连接器不存在: $NAME")
EOF
                else
                    echo "连接器不存在: $NAME"
                fi
                ;;
        esac
        ;;

    add)
        if [ -z "$2" ] || [ -z "$3" ] || [ -z "$4" ]; then
            echo "用法: $0 add <名称> <类型> <URL>"
            echo ""
            echo "类型: http, database, storage"
            exit 1
        fi
        
        NAME=$2
        TYPE=$3
        URL=$4
        
        mkdir -p ./data
        if [ ! -f "$CONNECTOR_FILE" ]; then
            echo '{"connectors":[]}' > "$CONNECTOR_FILE"
        fi
        
        python3 << EOF
import json

with open('$CONNECTOR_FILE', 'r') as f:
    config = json.load(f)

connectors = config.get('connectors', [])
connectors.append({
    'name': '$NAME',
    'type': '$TYPE',
    'url': '$URL',
    'enabled': True
})
config['connectors'] = connectors

with open('$CONNECTOR_FILE', 'w') as f:
    json.dump(config, f, indent=2)

print("连接器添加成功: $NAME")
EOF
        ;;

    remove)
        if [ -z "$2" ]; then
            echo "用法: $0 remove <连接器名称>"
            exit 1
        fi
        
        NAME=$2
        
        if [ ! -f "$CONNECTOR_FILE" ]; then
            echo "无自定义连接器"
            exit 1
        fi
        
        python3 << EOF
import json

with open('$CONNECTOR_FILE', 'r') as f:
    config = json.load(f)

config['connectors'] = [c for c in config.get('connectors', []) if c['name'] != '$NAME']

with open('$CONNECTOR_FILE', 'w') as f:
    json.dump(config, f, indent=2)

print("连接器已移除: $NAME")
EOF
        ;;

    config)
        if [ -z "$2" ]; then
            echo "用法: $0 config <连接器名称>"
            exit 1
        fi
        
        NAME=$2
        
        case "$NAME" in
            milvus)
                echo "=== Milvus 配置 ==="
                grep -A5 "milvus:" ./knowledge-web/src/main/resources/application.yml 2>/dev/null
                ;;
            ollama)
                echo "=== Ollama 配置 ==="
                grep -A5 "ollama:" ./knowledge-web/src/main/resources/application.yml 2>/dev/null
                ;;
            *)
                if [ -f "$CONNECTOR_FILE" ]; then
                    python3 << EOF
import json

with open('$CONNECTOR_FILE', 'r') as f:
    config = json.load(f)

for c in config.get('connectors', []):
    if c['name'] == '$NAME':
        print(json.dumps(c, indent=2))
        break
else:
    print("连接器不存在: $NAME")
EOF
                else
                    echo "连接器不存在: $NAME"
                fi
                ;;
        esac
        ;;

    *)
        echo "用法: $0 [list|test|add|remove|config]"
        echo ""
        echo "  list              - 列出连接器"
        echo "  test <名称>       - 测试连接"
        echo "  add <名称> <类型> <URL> - 添加连接器"
        echo "  remove <名称>     - 移除连接器"
        echo "  config <名称>     - 查看配置"
        exit 1
        ;;
esac
