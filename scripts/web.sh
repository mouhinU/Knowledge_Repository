#!/bin/bash
# Knowledge Repository Web 管理界面启动脚本
# 用法: ./scripts/web.sh [start|stop|status]

cd "$(dirname "$0")/.." || exit 1

PORT=8091
WEB_PORT=8092
WEB_PID_FILE="/tmp/kb-web.pid"

echo "=========================================="
echo "  Knowledge Repository Web 管理"
echo "=========================================="
echo ""

ACTION=${1:-start}

case "$ACTION" in
    start)
        echo "启动 Web 管理界面..."
        
        # 检查是否已运行
        if [ -f "$WEB_PID_FILE" ]; then
            OLD_PID=$(cat "$WEB_PID_FILE")
            if kill -0 "$OLD_PID" 2>/dev/null; then
                echo "Web 管理已在运行 (PID: $OLD_PID)"
                exit 0
            fi
        fi
        
        # 检查主服务
        if ! lsof -i :$PORT -t >/dev/null 2>&1; then
            echo "主服务未运行，请先启动: ./scripts/start.sh"
            exit 1
        fi
        
        # 启动简单的 Web 服务器（使用 Python）
        if command -v python3 >/dev/null 2>&1; then
            cd ./knowledge-web/src/main/resources/static
            nohup python3 -m http.server $WEB_PORT > /tmp/kb-web.log 2>&1 &
            echo $! > "$WEB_PID_FILE"
            cd - >/dev/null
            
            echo "Web 管理已启动"
            echo "  地址: http://localhost:$WEB_PORT/admin.html"
            echo "  PID: $(cat $WEB_PID_FILE)"
            echo "  日志: /tmp/kb-web.log"
        else
            echo "需要 Python3 来启动 Web 服务器"
            echo "或者直接使用主服务的 Web 界面:"
            echo "  http://localhost:$PORT/admin.html"
        fi
        ;;

    stop)
        echo "停止 Web 管理..."
        
        if [ -f "$WEB_PID_FILE" ]; then
            PID=$(cat "$WEB_PID_FILE")
            if kill -0 "$PID" 2>/dev/null; then
                kill "$PID"
                rm -f "$WEB_PID_FILE"
                echo "Web 管理已停止"
            else
                echo "Web 管理未运行"
                rm -f "$WEB_PID_FILE"
            fi
        else
            echo "Web 管理未运行"
        fi
        ;;

    status)
        echo "=== Web 管理状态 ==="
        echo ""
        
        if [ -f "$WEB_PID_FILE" ]; then
            PID=$(cat "$WEB_PID_FILE")
            if kill -0 "$PID" 2>/dev/null; then
                echo "Web 管理: 运行中 (PID: $PID)"
                echo "地址: http://localhost:$WEB_PORT/admin.html"
            else
                echo "Web 管理: 已停止 (PID 文件残留)"
            fi
        else
            echo "Web 管理: 未运行"
        fi
        
        echo ""
        echo "主服务 Web 界面:"
        if lsof -i :$PORT -t >/dev/null 2>&1; then
            echo "  地址: http://localhost:$PORT/admin.html"
            echo "  状态: 可用"
        else
            echo "  状态: 主服务未运行"
        fi
        ;;

    restart)
        $0 stop
        sleep 1
        $0 start
        ;;

    *)
        echo "用法: $0 [start|stop|status|restart]"
        echo ""
        echo "  start     - 启动 Web 管理"
        echo "  stop      - 停止 Web 管理"
        echo "  status    - 查看状态"
        echo "  restart   - 重启 Web 管理"
        exit 1
        ;;
esac
