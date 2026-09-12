#!/bin/bash
# Knowledge Repository 工作流管理脚本
# 用法: ./scripts/workflow.sh [list|run|status|define|history]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 工作流管理"
echo "=========================================="
echo ""

ACTION=${1:-list}
WORKFLOW_DIR="./data/workflows"

case "$ACTION" in
    list)
        echo "=== 工作流列表 ==="
        echo ""
        
        mkdir -p "$WORKFLOW_DIR"
        
        WORKFLOWS=$(ls "$WORKFLOW_DIR"/*.json 2>/dev/null)
        
        if [ -z "$WORKFLOWS" ]; then
            echo "无工作流定义"
            echo ""
            echo "使用 '$0 define' 创建工作流"
            exit 0
        fi
        
        printf "%-25s %-15s %-20s %-10s\n" "名称" "状态" "创建时间" "步骤数"
        echo "---------------------------------------------------------------------"
        
        for W in $WORKFLOWS; do
            python3 << EOF
import json
with open('$W', 'r') as f:
    wf = json.load(f)
name = wf.get('name', '-')
status = wf.get('status', 'draft')
created = wf.get('createdAt', '-')[:10]
steps = len(wf.get('steps', []))
print(f"{name:<25} {status:<15} {created:<20} {steps:<10}")
EOF
        done
        ;;

    run)
        if [ -z "$2" ]; then
            echo "用法: $0 run <工作流名称>"
            exit 1
        fi
        
        WF_NAME=$2
        WF_FILE="$WORKFLOW_DIR/$WF_NAME.json"
        
        if [ ! -f "$WF_FILE" ]; then
            echo "工作流不存在: $WF_NAME"
            exit 1
        fi
        
        echo "=== 运行工作流: $WF_NAME ==="
        echo ""
        
        # 读取工作流定义
        python3 << EOF
import json

with open('$WF_FILE', 'r') as f:
    wf = json.load(f)

print(f"工作流: {wf.get('name')}")
print(f"描述: {wf.get('description', '-')}")
print()

steps = wf.get('steps', [])
print(f"执行 {len(steps)} 个步骤:")
print()

for i, step in enumerate(steps):
    name = step.get('name', f'Step {i+1}')
    action = step.get('action', '-')
    print(f"  [{i+1}] {name}")
    print(f"      动作: {action}")
    print()

print("工作流执行完成 (模拟)")
EOF
        ;;

    status)
        if [ -z "$2" ]; then
            echo "用法: $0 status <工作流名称>"
            exit 1
        fi
        
        WF_NAME=$2
        WF_FILE="$WORKFLOW_DIR/$WF_NAME.json"
        
        if [ ! -f "$WF_FILE" ]; then
            echo "工作流不存在: $WF_NAME"
            exit 1
        fi
        
        echo "=== 工作流状态: $WF_NAME ==="
        echo ""
        
        cat "$WF_FILE" | python3 -m json.tool
        ;;

    define)
        if [ -z "$2" ]; then
            echo "用法: $0 define <工作流名称>"
            echo ""
            echo "创建示例工作流定义"
            exit 1
        fi
        
        WF_NAME=$2
        WF_FILE="$WORKFLOW_DIR/$WF_NAME.json"
        
        mkdir -p "$WORKFLOW_DIR"
        
        if [ -f "$WF_FILE" ]; then
            echo "工作流已存在: $WF_NAME"
            read -p "是否覆盖? (y/N): " CONFIRM
            if [ "$CONFIRM" != "y" ]; then
                exit 0
            fi
        fi
        
        cat > "$WF_FILE" <<EOF
{
  "name": "$WF_NAME",
  "description": "示例工作流",
  "status": "draft",
  "createdAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "steps": [
    {
      "name": "上传文档",
      "action": "document.upload",
      "params": {}
    },
    {
      "name": "解析文档",
      "action": "document.parse",
      "params": {
        "strategy": "FIXED_SIZE"
      }
    },
    {
      "name": "向量化",
      "action": "document.index",
      "params": {}
    },
    {
      "name": "发送通知",
      "action": "notify.send",
      "params": {
        "channel": "email",
        "message": "文档处理完成"
      }
    }
  ]
}
EOF
        
        echo "工作流已创建: $WF_FILE"
        ;;

    history)
        echo "=== 工作流执行历史 ==="
        echo ""
        
        HISTORY_FILE="$WORKFLOW_DIR/history.log"
        
        if [ ! -f "$HISTORY_FILE" ]; then
            echo "无执行历史"
            exit 0
        fi
        
        LIMIT=${2:-20}
        tail -n "$LIMIT" "$HISTORY_FILE" | while read line; do
            echo "$line" | python3 -c "
import sys, json
try:
    h = json.loads(sys.stdin.read())
    print(f\"[{h.get('timestamp', '-')[:19]}] {h.get('workflow', '-')} - {h.get('status', '-')}\")
except:
    pass
"
        done
        ;;

    delete)
        if [ -z "$2" ]; then
            echo "用法: $0 delete <工作流名称>"
            exit 1
        fi
        
        WF_NAME=$2
        WF_FILE="$WORKFLOW_DIR/$WF_NAME.json"
        
        if [ ! -f "$WF_FILE" ]; then
            echo "工作流不存在: $WF_NAME"
            exit 1
        fi
        
        read -p "确认删除工作流 $WF_NAME? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        rm -f "$WF_FILE"
        echo "工作流已删除: $WF_NAME"
        ;;

    template)
        echo "=== 工作流模板 ==="
        echo ""
        echo "1. 文档处理流程"
        echo "   上传 -> 解析 -> 向量化 -> 通知"
        echo ""
        echo "2. 批量导入流程"
        echo "   扫描目录 -> 批量上传 -> 批量索引 -> 生成报告"
        echo ""
        echo "3. 定期备份流程"
        echo "   检查时间 -> 导出数据 -> 压缩备份 -> 清理旧备份"
        echo ""
        echo "使用 '$0 define <名称>' 基于模板创建工作流"
        ;;

    *)
        echo "用法: $0 [list|run|status|define|history|delete|template]"
        echo ""
        echo "  list                - 列出工作流"
        echo "  run <名称>          - 运行工作流"
        echo "  status <名称>       - 查看状态"
        echo "  define <名称>       - 定义工作流"
        echo "  history [数量]      - 执行历史"
        echo "  delete <名称>       - 删除工作流"
        echo "  template            - 查看模板"
        exit 1
        ;;
esac
