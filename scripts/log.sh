#!/bin/bash
# Knowledge Repository 日志管理脚本
# 用法: ./log.sh [view|level|rotate|clean]

cd "$(dirname "$0")/.." || exit 1

PORT=8091
LOG_FILE="/tmp/kb-server.log"
API_BASE="http://localhost:$PORT/api/admin/log"

ACTION=${1:-view}

case "$ACTION" in
    view|v)
        LINES=${2:-100}
        
        if [ ! -f "$LOG_FILE" ]; then
            echo "日志文件不存在: $LOG_FILE"
            exit 1
        fi
        
        echo "=== 最近 $LINES 行日志 ==="
        tail -n "$LINES" "$LOG_FILE"
        ;;

    follow|f)
        if [ ! -f "$LOG_FILE" ]; then
            echo "日志文件不存在: $LOG_FILE"
            exit 1
        fi
        
        echo "=== 实时日志 (Ctrl+C 退出) ==="
        tail -f "$LOG_FILE"
        ;;

    level)
        if [ -z "$2" ]; then
            echo "用法: $0 level <日志级别>"
            echo ""
            echo "级别: TRACE, DEBUG, INFO, WARN, ERROR"
            echo ""
            echo "当前级别:"
            if lsof -i :$PORT -t >/dev/null 2>&1; then
                curl -sf "$API_BASE/level" 2>/dev/null
            fi
            exit 1
        fi
        LEVEL=$2
        
        if ! lsof -i :$PORT -t >/dev/null 2>&1; then
            echo "服务未运行"
            exit 1
        fi
        
        echo "设置日志级别: $LEVEL"
        RESULT=$(curl -sf -X PUT "$API_BASE/level" \
            -H "Content-Type: application/json" \
            -d "{\"level\":\"$LEVEL\"}" 2>/dev/null)
        
        if [ -n "$RESULT" ]; then
            echo "设置成功"
        else
            echo "设置失败"
        fi
        ;;

    rotate)
        if [ ! -f "$LOG_FILE" ]; then
            echo "日志文件不存在"
            exit 1
        fi
        
        TIMESTAMP=$(date +%Y%m%d_%H%M%S)
        BACKUP_FILE="${LOG_FILE}.${TIMESTAMP}"
        
        echo "轮转日志..."
        mv "$LOG_FILE" "$BACKUP_FILE"
        
        # 创建新日志文件
        touch "$LOG_FILE"
        
        echo "日志已轮转: $BACKUP_FILE"
        ;;

    clean)
        DAYS=${2:-7}
        
        echo "清理 $DAYS 天前的日志文件..."
        
        # 清理轮转的旧日志
        find /tmp -name "kb-server.log.*" -mtime +$DAYS -delete 2>/dev/null
        
        echo "清理完成"
        ;;

    search|s)
        if [ -z "$2" ]; then
            echo "用法: $0 search <关键词>"
            exit 1
        fi
        KEYWORD=$2
        
        if [ ! -f "$LOG_FILE" ]; then
            echo "日志文件不存在"
            exit 1
        fi
        
        echo "=== 搜索: $KEYWORD ==="
        grep -n "$KEYWORD" "$LOG_FILE" | tail -50
        ;;

    error|e)
        if [ ! -f "$LOG_FILE" ]; then
            echo "日志文件不存在"
            exit 1
        fi
        
        echo "=== 错误日志 ==="
        grep -i "error\|exception\|fail" "$LOG_FILE" | tail -50
        ;;

    size)
        echo "=== 日志文件大小 ==="
        if [ -f "$LOG_FILE" ]; then
            du -h "$LOG_FILE"
        else
            echo "日志文件不存在"
        fi
        
        # 统计轮转的日志
        echo ""
        echo "轮转日志:"
        ls -lh /tmp/kb-server.log.* 2>/dev/null || echo "无"
        ;;

    *)
        echo "用法: $0 [view|follow|level|rotate|clean|search|error|size]"
        echo ""
        echo "  view [行数]        - 查看日志 (默认100行)"
        echo "  follow             - 实时跟踪日志"
        echo "  level <级别>       - 设置日志级别"
        echo "  rotate             - 轮转日志文件"
        echo "  clean [天数]       - 清理旧日志 (默认7天)"
        echo "  search <关键词>    - 搜索日志"
        echo "  error              - 查看错误日志"
        echo "  size               - 查看日志大小"
        exit 1
        ;;
esac
