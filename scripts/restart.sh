#!/bin/bash
# Knowledge Repository 服务重启脚本
# 端口: 8091

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo ">>> 停止服务..."
"$SCRIPT_DIR/stop.sh"

echo ""
echo ">>> 启动服务..."
"$SCRIPT_DIR/start.sh"
