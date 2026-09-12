#!/bin/bash
# Knowledge Repository 消息队列管理脚本
# 用法: ./scripts/queue.sh [status|list|publish|consume|purge|stats]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 消息队列"
echo "=========================================="
echo ""

ACTION=${1:-status}
QUEUE_DIR="./data/queues"

case "$ACTION" in
    status)
        echo "=== 队列状态 ==="
        echo ""
        
        mkdir -p "$QUEUE_DIR"
        
        # 列出所有队列
        QUEUES=$(ls "$QUEUE_DIR"/*.json 2>/dev/null)
        
        if [ -z "$QUEUES" ]; then
            echo "无队列"
            exit 0
        fi
        
        printf "%-25s %-10s %-10s %-20s\n" "队列名" "消息数" "消费者" "最后消费"
        echo "---------------------------------------------------------------------"
        
        for Q in $QUEUES; do
            NAME=$(basename "$Q" .json)
            python3 << EOF
import json
with open('$Q', 'r') as f:
    data = json.load(f)
messages = len(data.get('messages', []))
consumers = data.get('consumerCount', 0)
last = data.get('lastConsumeTime', '-')[:19]
print(f"{$NAME:<25} {messages:<10} {consumers:<10} {last:<20}")
EOF
        done
        ;;

    list)
        if [ -z "$2" ]; then
            echo "用法: $0 list <队列名>"
            exit 1
        fi
        
        QUEUE_NAME=$2
        QUEUE_FILE="$QUEUE_DIR/$QUEUE_NAME.json"
        
        if [ ! -f "$QUEUE_FILE" ]; then
            echo "队列不存在: $QUEUE_NAME"
            exit 1
        fi
        
        echo "=== 队列 $QUEUE_NAME 消息列表 ==="
        echo ""
        
        python3 << EOF
import json
with open('$QUEUE_FILE', 'r') as f:
    data = json.load(f)
messages = data.get('messages', [])
if not messages:
    print("队列为空")
else:
    for i, msg in enumerate(messages[:20]):
        print(f"[{i+1}] {msg.get('id', '-')} - {msg.get('timestamp', '-')[:19]}")
        print(f"    {str(msg.get('payload', ''))[:80]}...")
        print()
    if len(messages) > 20:
        print(f"... 还有 {len(messages) - 20} 条消息")
EOF
        ;;

    publish)
        if [ -z "$2" ] || [ -z "$3" ]; then
            echo "用法: $0 publish <队列名> <消息内容>"
            exit 1
        fi
        
        QUEUE_NAME=$2
        MESSAGE=$3
        QUEUE_FILE="$QUEUE_DIR/$QUEUE_NAME.json"
        
        mkdir -p "$QUEUE_DIR"
        
        if [ ! -f "$QUEUE_FILE" ]; then
            echo '{"messages":[],"consumerCount":0}' > "$QUEUE_FILE"
        fi
        
        python3 << EOF
import json
from datetime import datetime
import uuid

with open('$QUEUE_FILE', 'r') as f:
    data = json.load(f)

msg = {
    'id': str(uuid.uuid4())[:8],
    'payload': '$MESSAGE',
    'timestamp': datetime.now().isoformat()
}
data['messages'].append(msg)

with open('$QUEUE_FILE', 'w') as f:
    json.dump(data, f, indent=2)

print(f"消息已发布到队列 $QUEUE_NAME: {msg['id']}")
EOF
        ;;

    consume)
        if [ -z "$2" ]; then
            echo "用法: $0 consume <队列名>"
            exit 1
        fi
        
        QUEUE_NAME=$2
        QUEUE_FILE="$QUEUE_DIR/$QUEUE_NAME.json"
        
        if [ ! -f "$QUEUE_FILE" ]; then
            echo "队列不存在: $QUEUE_NAME"
            exit 1
        fi
        
        python3 << EOF
import json
from datetime import datetime

with open('$QUEUE_FILE', 'r') as f:
    data = json.load(f)

messages = data.get('messages', [])
if not messages:
    print("队列为空")
    exit(0)

# 取第一条消息
msg = messages.pop(0)
data['lastConsumeTime'] = datetime.now().isoformat()

with open('$QUEUE_FILE', 'w') as f:
    json.dump(data, f, indent=2)

print(f"消费消息: {msg['id']}")
print(f"内容: {msg['payload']}")
EOF
        ;;

    purge)
        if [ -z "$2" ]; then
            echo "用法: $0 purge <队列名|all>"
            exit 1
        fi
        
        QUEUE_NAME=$2
        
        if [ "$QUEUE_NAME" = "all" ]; then
            read -p "确认清空所有队列? (y/N): " CONFIRM
            if [ "$CONFIRM" != "y" ]; then
                echo "已取消"
                exit 0
            fi
            rm -f "$QUEUE_DIR"/*.json
            echo "所有队列已清空"
        else
            QUEUE_FILE="$QUEUE_DIR/$QUEUE_NAME.json"
            if [ -f "$QUEUE_FILE" ]; then
                rm -f "$QUEUE_FILE"
                echo "队列已清空: $QUEUE_NAME"
            else
                echo "队列不存在: $QUEUE_NAME"
            fi
        fi
        ;;

    stats)
        echo "=== 队列统计 ==="
        echo ""
        
        mkdir -p "$QUEUE_DIR"
        
        python3 << 'EOF'
import json
import os

queue_dir = './data/queues'
total_queues = 0
total_messages = 0

for f in os.listdir(queue_dir):
    if f.endswith('.json'):
        total_queues += 1
        with open(os.path.join(queue_dir, f), 'r') as fp:
            data = json.load(fp)
            total_messages += len(data.get('messages', []))

print(f"队列总数: {total_queues}")
print(f"消息总数: {total_messages}")
EOF
        ;;

    create)
        if [ -z "$2" ]; then
            echo "用法: $0 create <队列名>"
            exit 1
        fi
        
        QUEUE_NAME=$2
        QUEUE_FILE="$QUEUE_DIR/$QUEUE_NAME.json"
        
        mkdir -p "$QUEUE_DIR"
        
        if [ -f "$QUEUE_FILE" ]; then
            echo "队列已存在: $QUEUE_NAME"
            exit 1
        fi
        
        echo '{"messages":[],"consumerCount":0}' > "$QUEUE_FILE"
        echo "队列已创建: $QUEUE_NAME"
        ;;

    *)
        echo "用法: $0 [status|list|publish|consume|purge|stats|create]"
        echo ""
        echo "  status              - 查看所有队列状态"
        echo "  list <队列名>       - 列出队列消息"
        echo "  publish <队列> <消息> - 发布消息"
        echo "  consume <队列名>    - 消费消息"
        echo "  purge <队列|all>    - 清空队列"
        echo "  stats               - 统计信息"
        echo "  create <队列名>     - 创建队列"
        exit 1
        ;;
esac
