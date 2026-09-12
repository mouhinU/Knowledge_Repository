#!/bin/bash
# Knowledge Repository 用户管理脚本
# 用法: ./user.sh [list|add|delete|reset-password]

cd "$(dirname "$0")/.." || exit 1

PORT=8091
API_BASE="http://localhost:$PORT/api/admin/user"

# 检查服务
if ! lsof -i :$PORT -t >/dev/null 2>&1; then
    echo "服务未运行，请先启动服务"
    exit 1
fi

ACTION=${1:-list}

case "$ACTION" in
    list)
        echo "=== 用户列表 ==="
        USERS=$(curl -sf "$API_BASE/list" 2>/dev/null)
        if [ -n "$USERS" ]; then
            echo "$USERS" | python3 -m json.tool 2>/dev/null || echo "$USERS"
        else
            echo "获取用户列表失败"
        fi
        ;;

    add)
        if [ -z "$2" ] || [ -z "$3" ]; then
            echo "用法: $0 add <用户名> <密码>"
            exit 1
        fi
        USERNAME=$2
        PASSWORD=$3
        
        echo "创建用户: $USERNAME"
        RESULT=$(curl -sf -X POST "$API_BASE" \
            -H "Content-Type: application/json" \
            -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" 2>/dev/null)
        
        if [ -n "$RESULT" ]; then
            echo "创建成功"
            echo "$RESULT" | python3 -m json.tool 2>/dev/null
        else
            echo "创建失败"
        fi
        ;;

    delete)
        if [ -z "$2" ]; then
            echo "用法: $0 delete <用户名>"
            exit 1
        fi
        USERNAME=$2
        
        read -p "确认删除用户 $USERNAME? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        RESULT=$(curl -sf -X DELETE "$API_BASE/$USERNAME" 2>/dev/null)
        echo "删除完成"
        ;;

    reset-password)
        if [ -z "$2" ] || [ -z "$3" ]; then
            echo "用法: $0 reset-password <用户名> <新密码>"
            exit 1
        fi
        USERNAME=$2
        NEW_PASSWORD=$3
        
        RESULT=$(curl -sf -X PUT "$API_BASE/$USERNAME/password" \
            -H "Content-Type: application/json" \
            -d "{\"password\":\"$NEW_PASSWORD\"}" 2>/dev/null)
        
        if [ -n "$RESULT" ]; then
            echo "密码重置成功"
        else
            echo "密码重置失败"
        fi
        ;;

    *)
        echo "用法: $0 [list|add|delete|reset-password]"
        echo ""
        echo "  list              - 列出所有用户"
        echo "  add <用户> <密码>  - 创建用户"
        echo "  delete <用户>      - 删除用户"
        echo "  reset-password <用户> <密码> - 重置密码"
        exit 1
        ;;
esac
