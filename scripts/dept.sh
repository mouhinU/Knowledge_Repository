#!/bin/bash
# Knowledge Repository 部门管理脚本
# 用法: ./dept.sh [list|add|delete|tree]

cd "$(dirname "$0")/.." || exit 1

PORT=8091
API_BASE="http://localhost:$PORT/api/admin/department"

# 检查服务
if ! lsof -i :$PORT -t >/dev/null 2>&1; then
    echo "服务未运行，请先启动服务"
    exit 1
fi

ACTION=${1:-list}

case "$ACTION" in
    list)
        echo "=== 部门列表 ==="
        DEPTS=$(curl -sf "$API_BASE/list" 2>/dev/null)
        if [ -n "$DEPTS" ]; then
            echo "$DEPTS" | python3 -m json.tool 2>/dev/null || echo "$DEPTS"
        else
            echo "获取部门列表失败"
        fi
        ;;

    tree)
        echo "=== 部门树 ==="
        TREE=$(curl -sf "$API_BASE/tree" 2>/dev/null)
        if [ -n "$TREE" ]; then
            echo "$TREE" | python3 -m json.tool 2>/dev/null || echo "$TREE"
        else
            echo "获取部门树失败"
        fi
        ;;

    add)
        if [ -z "$2" ]; then
            echo "用法: $0 add <部门名称> [父部门ID]"
            exit 1
        fi
        NAME=$2
        PARENT_ID=${3:-0}
        
        echo "创建部门: $NAME (父级: $PARENT_ID)"
        RESULT=$(curl -sf -X POST "$API_BASE" \
            -H "Content-Type: application/json" \
            -d "{\"name\":\"$NAME\",\"parentId\":\"$PARENT_ID\"}" 2>/dev/null)
        
        if [ -n "$RESULT" ]; then
            echo "创建成功"
            echo "$RESULT" | python3 -m json.tool 2>/dev/null
        else
            echo "创建失败"
        fi
        ;;

    delete)
        if [ -z "$2" ]; then
            echo "用法: $0 delete <部门ID>"
            exit 1
        fi
        DEPT_ID=$2
        
        read -p "确认删除部门 $DEPT_ID? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        RESULT=$(curl -sf -X DELETE "$API_BASE/$DEPT_ID" 2>/dev/null)
        echo "删除完成"
        ;;

    *)
        echo "用法: $0 [list|tree|add|delete]"
        echo ""
        echo "  list              - 列出所有部门"
        echo "  tree              - 显示部门树结构"
        echo "  add <名称> [父ID]  - 创建部门"
        echo "  delete <部门ID>    - 删除部门"
        exit 1
        ;;
esac
