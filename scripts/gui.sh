#!/bin/bash
# Knowledge Repository 快速启动 GUI 管理界面
# 用法: ./scripts/gui.sh

cd "$(dirname "$0")/.." || exit 1

PORT=8091

# 检查服务是否运行
PID=$(lsof -i :$PORT -t 2>/dev/null)

if [ -z "$PID" ]; then
    echo "服务未运行，正在启动..."
    ./scripts/start.sh
    sleep 3
fi

# 打开浏览器
echo "打开管理控制台..."
echo "http://localhost:$PORT/admin.html"

if command -v open >/dev/null 2>&1; then
    # macOS
    open "http://localhost:$PORT/admin.html"
elif command -v xdg-open >/dev/null 2>&1; then
    # Linux
    xdg-open "http://localhost:$PORT/admin.html"
elif command -v start >/dev/null 2>&1; then
    # Windows (Git Bash)
    start "http://localhost:$PORT/admin.html"
else
    echo "请手动在浏览器中打开上述地址"
fi
