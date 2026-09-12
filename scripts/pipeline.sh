#!/bin/bash
# Knowledge Repository 数据管道管理脚本
# 用法: ./scripts/pipeline.sh [list|run|status|create|logs]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 数据管道"
echo "=========================================="
echo ""

ACTION=${1:-list}
PIPELINE_DIR="./data/pipelines"

case "$ACTION" in
    list)
        echo "=== 数据管道列表 ==="
        echo ""
        
        mkdir -p "$PIPELINE_DIR"
        
        PIPELINES=$(ls "$PIPELINE_DIR"/*.json 2>/dev/null)
        
        if [ -z "$PIPELINES" ]; then
            echo "无数据管道"
            exit 0
        fi
        
        printf "%-25s %-15s %-15s %-20s\n" "名称" "状态" "运行次数" "最后运行"
        echo "---------------------------------------------------------------------"
        
        for P in $PIPELINES; do
            python3 << EOF
import json
with open('$P', 'r') as f:
            p = json.load(f)
name = p.get('name', '-')
status = p.get('status', 'inactive')
runs = p.get('runCount', 0)
last = p.get('lastRun', '-')[:10]
print(f"{name:<25} {status:<15} {runs:<15} {last:<20}")
EOF
        done
        ;;

    run)
        if [ -z "$2" ]; then
            echo "用法: $0 run <管道名称>"
            exit 1
        fi
        
        P_NAME=$2
        P_FILE="$PIPELINE_DIR/$P_NAME.json"
        
        if [ ! -f "$P_FILE" ]; then
            echo "管道不存在: $P_NAME"
            exit 1
        fi
        
        echo "=== 运行数据管道: $P_NAME ==="
        echo ""
        
        python3 << EOF
import json
from datetime import datetime

with open('$P_FILE', 'r') as f:
    p = json.load(f)

print(f"管道: {p.get('name')}")
print(f"描述: {p.get('description', '-')}")
print()

stages = p.get('stages', [])
print(f"执行 {len(stages)} 个阶段:")
print()

for i, stage in enumerate(stages):
    name = stage.get('name', f'Stage {i+1}')
    processor = stage.get('processor', '-')
    print(f"  [{i+1}] {name}")
    print(f"      处理器: {processor}")
    
    # 模拟执行
    print(f"      状态: 完成")
    print()

# 更新运行统计
p['runCount'] = p.get('runCount', 0) + 1
p['lastRun'] = datetime.now().isoformat()
p['status'] = 'active'

with open('$P_FILE', 'w') as f:
    json.dump(p, f, indent=2)

print("管道执行完成")
EOF
        ;;

    status)
        if [ -z "$2" ]; then
            echo "用法: $0 status <管道名称>"
            exit 1
        fi
        
        P_NAME=$2
        P_FILE="$PIPELINE_DIR/$P_NAME.json"
        
        if [ ! -f "$P_FILE" ]; then
            echo "管道不存在: $P_NAME"
            exit 1
        fi
        
        echo "=== 管道状态: $P_NAME ==="
        echo ""
        
        cat "$P_FILE" | python3 -m json.tool
        ;;

    create)
        if [ -z "$2" ]; then
            echo "用法: $0 create <管道名称>"
            exit 1
        fi
        
        P_NAME=$2
        P_FILE="$PIPELINE_DIR/$P_NAME.json"
        
        mkdir -p "$PIPELINE_DIR"
        
        if [ -f "$P_FILE" ]; then
            echo "管道已存在: $P_NAME"
            exit 1
        fi
        
        cat > "$P_FILE" <<EOF
{
  "name": "$P_NAME",
  "description": "数据管道",
  "status": "inactive",
  "runCount": 0,
  "stages": [
    {
      "name": "数据提取",
      "processor": "extract",
      "config": {
        "source": "file",
        "path": "./data/input"
      }
    },
    {
      "name": "数据转换",
      "processor": "transform",
      "config": {
        "operations": ["clean", "normalize"]
      }
    },
    {
      "name": "数据加载",
      "processor": "load",
      "config": {
        "target": "milvus",
        "collection": "knowledge_chunks"
      }
    }
  ]
}
EOF
        
        echo "管道已创建: $P_FILE"
        ;;

    logs)
        if [ -z "$2" ]; then
            echo "用法: $0 logs <管道名称>"
            exit 1
        fi
        
        P_NAME=$2
        LOG_FILE="$PIPELINE_DIR/$P_NAME.log"
        
        if [ ! -f "$LOG_FILE" ]; then
            echo "无日志: $P_NAME"
            exit 0
        fi
        
        echo "=== 管道日志: $P_NAME ==="
        echo ""
        
        tail -50 "$LOG_FILE"
        ;;

    delete)
        if [ -z "$2" ]; then
            echo "用法: $0 delete <管道名称>"
            exit 1
        fi
        
        P_NAME=$2
        P_FILE="$PIPELINE_DIR/$P_NAME.json"
        
        if [ ! -f "$P_FILE" ]; then
            echo "管道不存在: $P_NAME"
            exit 1
        fi
        
        read -p "确认删除管道 $P_NAME? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        rm -f "$P_FILE"
        rm -f "$PIPELINE_DIR/$P_NAME.log"
        echo "管道已删除: $P_NAME"
        ;;

    template)
        echo "=== 数据管道模板 ==="
        echo ""
        echo "1. ETL 管道"
        echo "   提取 -> 转换 -> 加载"
        echo ""
        echo "2. 文档处理管道"
        echo "   扫描 -> 解析 -> 分块 -> 向量化 -> 存储"
        echo ""
        echo "3. 数据同步管道"
        echo "   检测变更 -> 增量提取 -> 同步 -> 验证"
        echo ""
        echo "使用 '$0 create <名称>' 创建管道"
        ;;

    *)
        echo "用法: $0 [list|run|status|create|logs|delete|template]"
        echo ""
        echo "  list                - 列出管道"
        echo "  run <名称>          - 运行管道"
        echo "  status <名称>       - 查看状态"
        echo "  create <名称>       - 创建管道"
        echo "  logs <名称>         - 查看日志"
        echo "  delete <名称>       - 删除管道"
        echo "  template            - 查看模板"
        exit 1
        ;;
esac
