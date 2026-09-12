#!/bin/bash
# Knowledge Repository 角色权限管理脚本
# 用法: ./role.sh [list|add|delete|assign|permissions]

cd "$(dirname "$0")/.." || exit 1

PORT=8091
API_BASE="http://localhost:$PORT/api/admin/role"

# 检查服务
if ! lsof -i :$PORT -t >/dev/null 2>&1; then
    echo "服务未运行，请先启动服务"
    exit 1
fi

ACTION=${1:-list}

case "$ACTION" in
    list)
        echo "=== 角色列表 ==="
        ROLES=$(curl -sf "$API_BASE/list" 2>/dev/null)
        if [ -n "$ROLES" ]; then
            echo "$ROLES" | python3 -m json.tool 2>/dev/null || echo "$ROLES"
        else
            echo "获取角色列表失败"
        fi
        ;;

    add)
        if [ -z "$2" ]; then
            echo "用法: $0 add <角色名称> [描述]"
            exit 1
        fi
        NAME=$2
        DESC=${3:-""}
        
        echo "创建角色: $NAME"
        RESULT=$(curl -sf -X POST "$API_BASE" \
            -H "Content-Type: application/json" \
            -d "{\"name\":\"$NAME\",\"description\":\"$DESC\"}" 2>/dev/null)
        
        if [ -n "$RESULT" ]; then
            echo "创建成功"
            echo "$RESULT" | python3 -m json.tool 2>/dev/null
        else
            echo "创建失败"
        fi
        ;;

    delete)
        if [ -z "$2" ]; then
            echo "用法: $0 delete <角色ID>"
            exit 1
        fi
        ROLE_ID=$2
        
        read -p "确认删除角色 $ROLE_ID? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        RESULT=$(curl -sf -X DELETE "$API_BASE/$ROLE_ID" 2>/dev/null)
        echo "删除完成"
        ;;

    assign)
        if [ -z "$2" ] || [ -z "$3" ]; then
            echo "用法: $0 assign <用户ID> <角色ID>"
            exit 1
        fi
        USER_ID=$2
        ROLE_ID=$3
        
        echo "分配角色: 用户=$USER_ID 角色=$ROLE_ID"
        RESULT=$(curl -sf -X POST "$API_BASE/assign" \
            -H "Content-Type: application/json" \
            -d "{\"userId\":\"$USER_ID\",\"roleId\":\"$ROLE_ID\"}" 2>/dev/null)
        
        if [ -n "$RESULT" ]; then
            echo "分配成功"
        else
            echo "分配失败"
        fi
        ;;

    permissions)
        if [ -z "$2" ]; then
            echo "用法: $0 permissions <角色ID>"
            exit 1
        fi
        ROLE_ID=$2
        
        echo "=== 角色 $ROLE_ID 的权限 ==="
        PERMS=$(curl -sf "$API_BASE/$ROLE_ID/permissions" 2>/dev/null)
        if [ -n "$PERMS" ]; then
            echo "$PERMS" | python3 -m json.tool 2>/dev/null || echo "$PERMS"
        else
            echo "获取权限失败"
        fi
        ;;

    *)
        echo "用法: $0 [list|add|delete|assign|permissions]"
        echo ""
        echo "  list                    - 列出所有角色"
        echo "  add <名称> [描述]        - 创建角色"
        echo "  delete <角色ID>          - 删除角色"
        echo "  assign <用户ID> <角色ID> - 分配角色给用户"
        echo "  permissions <角色ID>     - 查看角色权限"
        exit 1
        ;;
esac
