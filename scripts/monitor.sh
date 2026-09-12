#!/bin/bash
# Knowledge Repository 服务监控脚本
# 实时监控服务状态，异常时记录日志

cd "$(dirname "$0")/.." || exit 1

PORT=8091
MONITOR_LOG="./data/monitor.log"
INTERVAL=30  # 检查间隔（秒）

mkdir -p "./data"

echo "=== Knowledge Repository 服务监控 ==="
echo "检查间隔: ${INTERVAL}秒"
echo "监控日志: $MONITOR_LOG"
echo "按 Ctrl+C 停止监控"
echo ""

log_msg() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$MONITOR_LOG"
}

log_msg "监控启动"

while true; do
    # 检查进程
    PID=$(lsof -i :$PORT -t 2>/dev/null)
    
    if [ -z "$PID" ]; then
        log_msg "[FAIL] 服务未运行 (端口:$PORT)"
    else
        # 检查 HTTP 响应
        HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" --max-time 5 "http://localhost:$PORT/admin.html" 2>/dev/null)
        
        if [ "$HTTP_CODE" = "200" ]; then
            # 获取内存使用
            MEM=$(ps -p "$PID" -o rss= 2>/dev/null | awk '{printf "%.1f", $1/1024}')
            log_msg "[OK] 服务正常 PID:$PID 内存:${MEM}MB"
        else
            log_msg "[WARN] 服务异常 HTTP:$HTTP_CODE PID:$PID"
        fi
    fi
    
    # 检查 Milvus
    if curl -sf "http://localhost:9091/healthz" >/dev/null 2>&1; then
        MILVUS_STATUS="OK"
    else
        MILVUS_STATUS="FAIL"
        log_msg "[FAIL] Milvus 未就绪"
    fi
    
    sleep $INTERVAL
done
