#!/bin/bash
# Knowledge Repository 代理服务脚本
# 用法: ./scripts/proxy.sh [status|start|stop|config|logs]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 代理服务"
echo "=========================================="
echo ""

ACTION=${1:-status}
PROXY_PORT=8080
BACKEND_PORT=8091

case "$ACTION" in
    status)
        echo "=== 代理服务状态 ==="
        echo ""
        
        # 检查代理进程
        PID=$(lsof -i :$PROXY_PORT -t 2>/dev/null)
        if [ -n "$PID" ]; then
            echo "代理服务: 运行中 (PID: $PID, 端口: $PROXY_PORT)"
        else
            echo "代理服务: 未运行"
        fi
        
        echo ""
        echo "后端服务:"
        BACKEND_PID=$(lsof -i :$BACKEND_PORT -t 2>/dev/null)
        if [ -n "$BACKEND_PID" ]; then
            echo "  状态: 运行中 (端口: $BACKEND_PORT)"
        else
            echo "  状态: 未运行"
        fi
        
        echo ""
        if [ -f "./data/proxy-config.json" ]; then
            echo "代理配置:"
            cat ./data/proxy-config.json | python3 -m json.tool 2>/dev/null
        fi
        ;;

    start)
        echo "启动代理服务..."
        
        # 检查后端
        if ! lsof -i :$BACKEND_PORT -t >/dev/null 2>&1; then
            echo "后端服务未运行，请先启动: ./scripts/start.sh"
            exit 1
        fi
        
        # 检查端口
        if lsof -i :$PROXY_PORT -t >/dev/null 2>&1; then
            echo "端口 $PROXY_PORT 已被占用"
            exit 1
        fi
        
        # 创建代理配置
        if [ ! -f "./data/proxy-config.json" ]; then
            mkdir -p ./data
            cat > ./data/proxy-config.json <<EOF
{
  "listen": $PROXY_PORT,
  "backend": "http://localhost:$BACKEND_PORT",
  "timeout": 30,
  "ssl": false,
  "cors": {
    "enabled": true,
    "origins": ["*"]
  }
}
EOF
        fi
        
        echo "代理配置已就绪"
        echo "代理地址: http://localhost:$PROXY_PORT"
        echo ""
        echo "注意: 此脚本仅提供配置管理，实际代理需要使用 nginx/caddy 等"
        ;;

    stop)
        echo "停止代理服务..."
        
        PID=$(lsof -i :$PROXY_PORT -t 2>/dev/null)
        if [ -n "$PID" ]; then
            kill $PID 2>/dev/null
            echo "代理已停止"
        else
            echo "代理未运行"
        fi
        ;;

    config)
        echo "=== 代理配置 ==="
        echo ""
        
        if [ ! -f "./data/proxy-config.json" ]; then
            echo "未配置代理"
            echo ""
            echo "使用 '$0 start' 创建默认配置"
            exit 1
        fi
        
        cat ./data/proxy-config.json | python3 -m json.tool
        ;;

    logs)
        echo "=== 代理日志 ==="
        echo ""
        
        LOG_FILE="/tmp/kb-proxy.log"
        if [ -f "$LOG_FILE" ]; then
            tail -50 "$LOG_FILE"
        else
            echo "无代理日志"
        fi
        ;;

    nginx)
        echo "=== Nginx 配置示例 ==="
        echo ""
        cat << 'EOF'
server {
    listen 8080;
    server_name localhost;

    location / {
        proxy_pass http://localhost:8091;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # WebSocket 支持
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        
        # 超时设置
        proxy_connect_timeout 30s;
        proxy_send_timeout 30s;
        proxy_read_timeout 30s;
    }

    # CORS 配置
    add_header 'Access-Control-Allow-Origin' '*' always;
    add_header 'Access-Control-Allow-Methods' 'GET, POST, PUT, DELETE, OPTIONS' always;
    add_header 'Access-Control-Allow-Headers' 'DNT,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Range,Authorization' always;
}
EOF
        ;;

    test)
        echo "=== 代理测试 ==="
        echo ""
        
        # 测试直连后端
        echo -n "直连后端 (localhost:$BACKEND_PORT): "
        if curl -sf -o /dev/null --max-time 3 "http://localhost:$BACKEND_PORT/admin.html" 2>/dev/null; then
            echo "OK"
        else
            echo "FAIL"
        fi
        
        # 测试代理
        echo -n "通过代理 (localhost:$PROXY_PORT): "
        if curl -sf -o /dev/null --max-time 3 "http://localhost:$PROXY_PORT/admin.html" 2>/dev/null; then
            echo "OK"
        else
            echo "FAIL (代理可能未运行)"
        fi
        ;;

    *)
        echo "用法: $0 [status|start|stop|config|logs|nginx|test]"
        echo ""
        echo "  status    - 代理状态"
        echo "  start     - 启动代理"
        echo "  stop      - 停止代理"
        echo "  config    - 查看配置"
        echo "  logs      - 查看日志"
        echo "  nginx     - Nginx 配置示例"
        echo "  test      - 测试连接"
        exit 1
        ;;
esac
