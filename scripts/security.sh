#!/bin/bash
# Knowledge Repository 安全管理脚本
# 用法: ./security.sh [status|scan|firewall|cert|audit]

cd "$(dirname "$0")/.." || exit 1

PORT=8091

echo "=== Knowledge Repository 安全管理 ==="
echo ""

ACTION=${1:-status}

case "$ACTION" in
    status)
        echo "--- 服务状态 ---"
        PID=$(lsof -i :$PORT -t 2>/dev/null)
        if [ -n "$PID" ]; then
            echo "应用服务: 运行中 (PID: $PID)"
        else
            echo "应用服务: 未运行"
        fi
        
        echo ""
        echo "--- 监听端口 ---"
        lsof -i :$PORT 2>/dev/null | grep LISTEN
        
        echo ""
        echo "--- 活跃连接 ---"
        if [ -n "$PID" ]; then
            lsof -i :$PORT 2>/dev/null | grep ESTABLISHED | wc -l | tr -d ' '
            echo "个活跃连接"
        fi
        ;;

    scan)
        echo "=== 安全扫描 ==="
        echo ""
        
        # 检查默认密码
        echo "--- 默认密码检查 ---"
        if grep -q "password: admin" ./knowledge-web/src/main/resources/application.yml 2>/dev/null; then
            echo "[WARN] 发现默认管理员密码 'admin'，建议修改"
        else
            echo "[OK] 未使用默认密码"
        fi
        
        echo ""
        
        # 检查敏感文件权限
        echo "--- 文件权限检查 ---"
        if [ -f "./data/knowledge-repository.mv.db" ]; then
            PERMS=$(stat -f "%Lp" ./data/knowledge-repository.mv.db 2>/dev/null)
            if [ "$PERMS" = "600" ] || [ "$PERMS" = "640" ]; then
                echo "[OK] 数据库文件权限: $PERMS"
            else
                echo "[WARN] 数据库文件权限过于宽松: $PERMS (建议 600)"
            fi
        fi
        
        echo ""
        
        # 检查日志中的敏感信息
        echo "--- 日志敏感信息检查 ---"
        if [ -f "/tmp/kb-server.log" ]; then
            if grep -qi "password\|secret\|api-key\|token" /tmp/kb-server.log 2>/dev/null; then
                echo "[WARN] 日志中可能包含敏感信息"
            else
                echo "[OK] 日志中未发现明显敏感信息"
            fi
        fi
        ;;

    firewall)
        echo "=== 防火墙规则 ==="
        echo ""
        
        if command -v ufw >/dev/null 2>&1; then
            echo "UFW 状态:"
            sudo ufw status 2>/dev/null || echo "需要 sudo 权限"
        elif command -v firewall-cmd >/dev/null 2>&1; then
            echo "Firewalld 状态:"
            sudo firewall-cmd --state 2>/dev/null || echo "需要 sudo 权限"
        else
            echo "未检测到防火墙管理工具"
        fi
        
        echo ""
        echo "建议规则:"
        echo "  sudo ufw allow $PORT/tcp  # 允许应用端口"
        echo "  sudo ufw allow 19530/tcp  # Milvus (仅内网)"
        ;;

    cert)
        echo "=== SSL/TLS 证书 ==="
        echo ""
        
        # 检查是否配置了 HTTPS
        if grep -q "server.ssl" ./knowledge-web/src/main/resources/application.yml 2>/dev/null; then
            echo "[OK] 已配置 SSL"
            grep -A5 "server.ssl" ./knowledge-web/src/main/resources/application.yml 2>/dev/null
        else
            echo "[INFO] 未配置 SSL，使用 HTTP"
            echo ""
            echo "配置 HTTPS 示例:"
            echo "  server:"
            echo "    ssl:"
            echo "      key-store: classpath:keystore.p12"
            echo "      key-store-password: your-password"
            echo "      key-store-type: PKCS12"
        fi
        ;;

    audit)
        echo "=== 安全审计 ==="
        echo ""
        
        # 最近登录记录
        echo "--- 最近登录 ---"
        if lsof -i :$PORT -t >/dev/null 2>&1; then
            curl -sf "http://localhost:$PORT/api/admin/audit/user/admin" 2>/dev/null | head -20
        else
            echo "服务未运行"
        fi
        
        echo ""
        
        # 失败登录尝试
        echo "--- 失败登录尝试 ---"
        if [ -f "/tmp/kb-server.log" ]; then
            grep -i "login.*fail\|authentication.*fail" /tmp/kb-server.log 2>/dev/null | tail -10
        fi
        ;;

    password)
        if [ -z "$2" ]; then
            echo "用法: $0 password <新密码>"
            echo ""
            echo "修改管理员密码"
            exit 1
        fi
        NEW_PWD=$2
        
        if ! lsof -i :$PORT -t >/dev/null 2>&1; then
            echo "服务未运行"
            exit 1
        fi
        
        echo "修改管理员密码..."
        RESULT=$(curl -sf -X PUT "http://localhost:$PORT/api/admin/user/admin/password" \
            -H "Content-Type: application/json" \
            -d "{\"password\":\"$NEW_PWD\"}" 2>/dev/null)
        
        if [ -n "$RESULT" ]; then
            echo "密码修改成功"
        else
            echo "密码修改失败"
        fi
        ;;

    *)
        echo "用法: $0 [status|scan|firewall|cert|audit|password]"
        echo ""
        echo "  status          - 安全状态概览"
        echo "  scan            - 安全扫描"
        echo "  firewall        - 防火墙配置"
        echo "  cert            - SSL 证书状态"
        echo "  audit           - 安全审计日志"
        echo "  password <新密码> - 修改管理员密码"
        exit 1
        ;;
esac
