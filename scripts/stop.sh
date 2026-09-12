#!/bin/bash
# Knowledge Repository 服务关闭脚本
# 端口: 8091

PORT=8091

PID=$(lsof -i :$PORT -t 2>/dev/null)

if [ -z "$PID" ]; then
    echo "端口 $PORT 上没有运行中的服务"
    exit 0
fi

echo "正在关闭服务 (PID: $PID)..."
kill "$PID"

# 等待进程退出，最多 10 秒
for i in $(seq 1 10); do
    if ! kill -0 "$PID" 2>/dev/null; then
        echo "服务已停止"
        exit 0
    fi
    sleep 1
done

echo "优雅关闭超时，强制终止..."
kill -9 "$PID" 2>/dev/null
echo "服务已强制停止"
