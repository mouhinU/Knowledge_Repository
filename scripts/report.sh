#!/bin/bash
# Knowledge Repository 报告生成脚本
# 生成系统运行报告

cd "$(dirname "$0")/.." || exit 1

PORT=8091
REPORT_DIR="./data/reports"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REPORT_FILE="$REPORT_DIR/report_$TIMESTAMP.txt"

mkdir -p "$REPORT_DIR"

echo "生成系统报告..."

{
    echo "=========================================="
    echo "  Knowledge Repository 系统报告"
    echo "  生成时间: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "=========================================="
    echo ""

    # 1. 服务状态
    echo "=== 服务状态 ==="
    PID=$(lsof -i :$PORT -t 2>/dev/null)
    if [ -n "$PID" ]; then
        echo "应用服务: 运行中 (PID: $PID)"
        MEM=$(ps -p "$PID" -o rss= 2>/dev/null | awk '{printf "%.1f", $1/1024}')
        echo "内存使用: ${MEM}MB"
        UPTIME=$(ps -p "$PID" -o etime= 2>/dev/null | tr -d ' ')
        echo "运行时长: $UPTIME"
    else
        echo "应用服务: 未运行"
    fi
    echo ""

    # 2. 文档统计
    echo "=== 文档统计 ==="
    if [ -n "$PID" ]; then
        STATS=$(curl -sf "http://localhost:$PORT/api/admin/document/stats" 2>/dev/null)
        if [ -n "$STATS" ]; then
            echo "$STATS" | python3 -m json.tool 2>/dev/null || echo "$STATS"
        else
            echo "获取统计失败"
        fi
    fi
    echo ""

    # 3. 磁盘使用
    echo "=== 磁盘使用 ==="
    if [ -d "./data" ]; then
        du -sh ./data 2>/dev/null
        echo ""
        echo "分类明细:"
        [ -f "./data/knowledge-repository.mv.db" ] && du -sh ./data/knowledge-repository.mv.db 2>/dev/null
        [ -d "./data/documents" ] && du -sh ./data/documents 2>/dev/null
        [ -d "./data/backups" ] && du -sh ./data/backups 2>/dev/null
    fi
    echo ""

    # 4. Milvus 状态
    echo "=== Milvus 状态 ==="
    if curl -sf "http://localhost:9091/healthz" >/dev/null 2>&1; then
        echo "Milvus: 健康"
    else
        echo "Milvus: 未就绪"
    fi
    echo ""

    # 5. 最近备份
    echo "=== 最近备份 ==="
    if [ -d "./data/backups" ]; then
        ls -lht ./data/backups/*.tar.gz 2>/dev/null | head -5
    else
        echo "无备份"
    fi
    echo ""

    # 6. 日志摘要
    echo "=== 最近日志 ==="
    if [ -f "/tmp/kb-server.log" ]; then
        tail -20 /tmp/kb-server.log 2>/dev/null
    else
        echo "无日志文件"
    fi

} > "$REPORT_FILE"

echo "报告已生成: $REPORT_FILE"
echo ""
cat "$REPORT_FILE"
