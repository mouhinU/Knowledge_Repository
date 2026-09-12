#!/bin/bash
# Knowledge Repository 仪表盘脚本
# 用法: ./scripts/dashboard.sh [show|refresh|config]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 仪表盘"
echo "  $(date '+%Y-%m-%d %H:%M:%S')"
echo "=========================================="
echo ""

PORT=8091

# 服务状态
echo "┌─────────────────────────────────────────────────────────────┐"
echo "│                        服务状态                              │"
echo "├─────────────────────────────────────────────────────────────┤"

# 应用服务
PID=$(lsof -i :$PORT -t 2>/dev/null)
if [ -n "$PID" ]; then
    MEM=$(ps -p $PID -o rss= 2>/dev/null | awk '{printf "%.0f", $1/1024}')
    CPU=$(ps -p $PID -o %cpu= 2>/dev/null)
    echo "│  应用服务    ● 运行中   PID: $PID   内存: ${MEM}MB   CPU: ${CPU}%  │"
else
    echo "│  应用服务    ○ 未运行                                              │"
fi

# Milvus
if curl -sf "http://localhost:9091/healthz" >/dev/null 2>&1; then
    echo "│  Milvus      ● 健康                                              │"
else
    echo "│  Milvus      ○ 未就绪                                            │"
fi

# Ollama
if curl -sf "http://localhost:11434/api/tags" >/dev/null 2>&1; then
    echo "│  Ollama      ● 运行中                                            │"
else
    echo "│  Ollama      ○ 未运行                                            │"
fi

echo "└─────────────────────────────────────────────────────────────┘"
echo ""

# 文档统计
echo "┌─────────────────────────────────────────────────────────────┐"
echo "│                        文档统计                              │"
echo "├─────────────────────────────────────────────────────────────┤"

if [ -n "$PID" ]; then
    STATS=$(curl -sf "http://localhost:$PORT/api/admin/document/stats" 2>/dev/null)
    if [ -n "$STATS" ]; then
        TOTAL=$(echo "$STATS" | python3 -c "import sys,json; print(json.load(sys.stdin).get('totalDocuments', 0))" 2>/dev/null)
        INDEXED=$(echo "$STATS" | python3 -c "import sys,json; print(json.load(sys.stdin).get('indexedDocuments', 0))" 2>/dev/null)
        FAILED=$(echo "$STATS" | python3 -c "import sys,json; print(json.load(sys.stdin).get('failedDocuments', 0))" 2>/dev/null)
        
        printf "│  文档总数: %-10s  已索引: %-10s  失败: %-10s      │\n" "$TOTAL" "$INDEXED" "$FAILED"
    else
        echo "│  获取统计失败                                            │"
    fi
else
    echo "│  服务未运行，无法获取统计                                    │"
fi

echo "└─────────────────────────────────────────────────────────────┘"
echo ""

# 磁盘使用
echo "┌─────────────────────────────────────────────────────────────┐"
echo "│                        磁盘使用                              │"
echo "├─────────────────────────────────────────────────────────────┤"

if [ -d "./data" ]; then
    TOTAL_SIZE=$(du -sh ./data 2>/dev/null | cut -f1)
    DB_SIZE=$(du -sh ./data/knowledge-repository.mv.db 2>/dev/null | cut -f1)
    DOC_SIZE=$(du -sh ./data/documents 2>/dev/null | cut -f1)
    
    printf "│  数据目录: %-10s  数据库: %-10s  文档: %-10s      │\n" "$TOTAL_SIZE" "${DB_SIZE:-0}" "${DOC_SIZE:-0}"
else
    echo "│  数据目录不存在                                            │"
fi

echo "└─────────────────────────────────────────────────────────────┘"
echo ""

# 快速操作
echo "┌─────────────────────────────────────────────────────────────┐"
echo "│                        快速操作                              │"
echo "├─────────────────────────────────────────────────────────────┤"
echo "│  管理控制台: http://localhost:$PORT/admin.html                 │"
echo "│  健康检查:   ./scripts/health-check.sh                       │"
echo "│  查看日志:   ./scripts/logs.sh                               │"
echo "│  性能分析:   ./scripts/perf.sh                               │"
echo "└─────────────────────────────────────────────────────────────┘"
