#!/bin/bash
# Knowledge Repository API 测试脚本
# 测试所有 API 端点是否可用

cd "$(dirname "$0")/.." || exit 1

PORT=8091
BASE="http://localhost:$PORT"

echo "=== Knowledge Repository API 测试 ==="
echo ""

PASS=0
FAIL=0

test_api() {
    local METHOD=$1
    local URL=$2
    local DESC=$3
    local DATA=$4
    
    if [ "$METHOD" = "GET" ]; then
        HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" "$BASE$URL" 2>/dev/null)
    else
        HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" -X "$METHOD" \
            -H "Content-Type: application/json" \
            -d "$DATA" "$BASE$URL" 2>/dev/null)
    fi
    
    if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "201" ]; then
        echo "[OK]   $METHOD $URL  ($DESC)"
        ((PASS++))
    elif [ "$HTTP_CODE" = "401" ]; then
        echo "[WARN] $METHOD $URL  (需要认证)"
        ((PASS++))
    elif [ "$HTTP_CODE" = "404" ]; then
        echo "[FAIL] $METHOD $URL  (接口不存在)"
        ((FAIL++))
    elif [ "$HTTP_CODE" = "405" ]; then
        echo "[OK]   $METHOD $URL  (方法不允许，接口存在)"
        ((PASS++))
    else
        echo "[FAIL] $METHOD $URL  (HTTP $HTTP_CODE)"
        ((FAIL++))
    fi
}

# 文档管理 API
echo "--- 文档管理 ---"
test_api "GET" "/api/admin/document/list" "文档列表"
test_api "GET" "/api/admin/document/stats" "统计信息"
test_api "GET" "/api/admin/document/status/INDEXED" "按状态查询"

# 用户管理 API
echo ""
echo "--- 用户管理 ---"
test_api "GET" "/api/admin/user/list" "用户列表"

# 部门管理 API
echo ""
echo "--- 部门管理 ---"
test_api "GET" "/api/admin/department/list" "部门列表"
test_api "GET" "/api/admin/department/tree" "部门树"

# 角色管理 API
echo ""
echo "--- 角色管理 ---"
test_api "GET" "/api/admin/role/list" "角色列表"

# 配置管理 API
echo ""
echo "--- 系统配置 ---"
test_api "GET" "/api/admin/config/list" "配置列表"

# 知识检索 API
echo ""
echo "--- 知识检索 ---"
test_api "POST" "/api/knowledge/search" "搜索接口" '{"query":"test","topK":5}'

# 静态资源
echo ""
echo "--- 静态资源 ---"
test_api "GET" "/admin.html" "管理控制台"

# 汇总
echo ""
echo "=========================================="
echo "  通过: $PASS   失败: $FAIL"
echo "=========================================="

if [ $FAIL -gt 0 ]; then
    exit 1
fi
