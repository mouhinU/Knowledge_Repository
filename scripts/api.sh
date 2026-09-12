#!/bin/bash
# Knowledge Repository API 客户端脚本
# 用法: ./scripts/api.sh [get|post|put|delete] <端点> [数据]

cd "$(dirname "$0")/.." || exit 1

PORT=8091
BASE_URL="http://localhost:$PORT"

# 检查服务
if ! lsof -i :$PORT -t >/dev/null 2>&1; then
    echo "服务未运行"
    exit 1
fi

METHOD=${1:-get}
ENDPOINT=$2
DATA=$3

case "$METHOD" in
    get)
        if [ -z "$ENDPOINT" ]; then
            echo "用法: $0 get <端点>"
            echo ""
            echo "示例:"
            echo "  $0 get /api/admin/document/list"
            echo "  $0 get /api/admin/document/stats"
            exit 1
        fi
        
        echo "GET $ENDPOINT"
        echo ""
        curl -sf "$BASE_URL$ENDPOINT" | python3 -m json.tool 2>/dev/null || curl -sf "$BASE_URL$ENDPOINT"
        ;;

    post)
        if [ -z "$ENDPOINT" ]; then
            echo "用法: $0 post <端点> [数据]"
            echo ""
            echo "示例:"
            echo "  $0 post /api/knowledge/search '{\"query\":\"test\",\"topK\":5}'"
            exit 1
        fi
        
        echo "POST $ENDPOINT"
        if [ -n "$DATA" ]; then
            echo "数据: $DATA"
        fi
        echo ""
        
        if [ -n "$DATA" ]; then
            curl -sf -X POST "$BASE_URL$ENDPOINT" \
                -H "Content-Type: application/json" \
                -d "$DATA" | python3 -m json.tool 2>/dev/null
        else
            curl -sf -X POST "$BASE_URL$ENDPOINT" | python3 -m json.tool 2>/dev/null
        fi
        ;;

    put)
        if [ -z "$ENDPOINT" ]; then
            echo "用法: $0 put <端点> [数据]"
            exit 1
        fi
        
        echo "PUT $ENDPOINT"
        if [ -n "$DATA" ]; then
            echo "数据: $DATA"
        fi
        echo ""
        
        if [ -n "$DATA" ]; then
            curl -sf -X PUT "$BASE_URL$ENDPOINT" \
                -H "Content-Type: application/json" \
                -d "$DATA" | python3 -m json.tool 2>/dev/null
        else
            curl -sf -X PUT "$BASE_URL$ENDPOINT" | python3 -m json.tool 2>/dev/null
        fi
        ;;

    delete)
        if [ -z "$ENDPOINT" ]; then
            echo "用法: $0 delete <端点>"
            exit 1
        fi
        
        echo "DELETE $ENDPOINT"
        echo ""
        
        read -p "确认执行删除操作? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        curl -sf -X DELETE "$BASE_URL$ENDPOINT" | python3 -m json.tool 2>/dev/null
        ;;

    endpoints)
        echo "=== 常用 API 端点 ==="
        echo ""
        echo "文档管理:"
        echo "  GET  /api/admin/document/list          - 文档列表"
        echo "  GET  /api/admin/document/stats         - 统计信息"
        echo "  GET  /api/admin/document/{key}         - 文档详情"
        echo "  POST /api/admin/document/{key}/reindex - 重新索引"
        echo "  DELETE /api/admin/document/{key}       - 删除文档"
        echo ""
        echo "知识检索:"
        echo "  POST /api/knowledge/search             - 搜索"
        echo ""
        echo "用户管理:"
        echo "  GET  /api/admin/user/list              - 用户列表"
        echo ""
        echo "系统:"
        echo "  GET  /actuator/health                  - 健康检查"
        echo "  GET  /actuator/metrics                 - 指标"
        ;;

    test)
        echo "=== API 测试 ==="
        echo ""
        ./scripts/api-test.sh
        ;;

    *)
        echo "用法: $0 [get|post|put|delete|endpoints|test] [端点] [数据]"
        echo ""
        echo "  get <端点>           - GET 请求"
        echo "  post <端点> [数据]   - POST 请求"
        echo "  put <端点> [数据]    - PUT 请求"
        echo "  delete <端点>        - DELETE 请求"
        echo "  endpoints            - 查看常用端点"
        echo "  test                 - 测试所有 API"
        exit 1
        ;;
esac
