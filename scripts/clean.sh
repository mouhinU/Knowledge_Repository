#!/bin/bash
# Knowledge Repository 清理脚本
# 清理编译产物、日志、临时文件

cd "$(dirname "$0")/.." || exit 1

echo "开始清理..."

# 1. Maven 编译产物
echo "  清理 target 目录..."
find . -type d -name "target" -exec rm -rf {} + 2>/dev/null
echo "    已清理"

# 2. 日志文件
if [ -f "/tmp/kb-server.log" ]; then
    rm -f /tmp/kb-server.log
    echo "  清理日志: /tmp/kb-server.log"
fi

# 3. H2 数据库锁文件（服务未运行时）
PORT=8091
PID=$(lsof -i :$PORT -t 2>/dev/null)
if [ -z "$PID" ] && [ -d "./data" ]; then
    find ./data -name "*.lock.db" -delete 2>/dev/null
    find ./data -name "*.trace.db" -delete 2>/dev/null
    echo "  清理 H2 锁文件"
fi

# 4. 系统临时文件
if [ -d "/tmp/kb-extract-*" ]; then
    rm -rf /tmp/kb-extract-*
    echo "  清理临时提取文件"
fi

echo ""
echo "清理完成"
