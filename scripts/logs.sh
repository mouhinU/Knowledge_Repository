#!/bin/bash
# Knowledge Repository 日志查看脚本
# 用法: ./logs.sh [行数，默认100]

LOG_FILE=/tmp/kb-server.log
LINES=${1:-100}

if [ ! -f "$LOG_FILE" ]; then
    echo "日志文件不存在: $LOG_FILE"
    exit 1
fi

tail -n "$LINES" -f "$LOG_FILE"
