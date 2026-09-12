#!/bin/bash
# Knowledge Repository 服务状态检查脚本
# 端口: 8091

PORT=8091
LOG_FILE=/tmp/kb-server.log

PID=$(lsof -i :$PORT -t 2>/dev/null)

if [ -n "$PID" ]; then
    echo "服务运行中"
    echo "  PID:  $PID"
    echo "  端口: $PORT"
    echo "  日志: $LOG_FILE"
    # 显示进程运行时长
    START_TIME=$(ps -p "$PID" -o lstart= 2>/dev/null)
    [ -n "$START_TIME" ] && echo "  启动: $START_TIME"
else
    echo "服务未运行 (端口: $PORT)"
fi
