#!/bin/bash
# Knowledge Repository 控制面板脚本
# 用法: ./scripts/panel.sh [overview|services|documents|system]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 控制面板"
echo "=========================================="
echo ""

ACTION=${1:-overview}
PORT=8091

case "$ACTION" in
    overview)
        ./scripts/dashboard.sh
        ;;

    services)
        echo "=== 服务管理 ==="
        echo ""
        
        PID=$(lsof -i :$PORT -t 2>/dev/null)
        
        if [ -n "$PID" ]; then
            echo "应用服务: 运行中 (PID: $PID)"
            echo ""
            echo "操作:"
            echo "  1) 停止服务    ./scripts/stop.sh"
            echo "  2) 重启服务    ./scripts/restart.sh"
            echo "  3) 查看状态    ./scripts/status.sh"
            echo "  4) 查看日志    ./scripts/logs.sh"
        else
            echo "应用服务: 未运行"
            echo ""
            echo "操作:"
            echo "  1) 启动服务    ./scripts/start.sh"
            echo "  2) 一键部署    ./scripts/deploy.sh"
        fi
        ;;

    documents)
        echo "=== 文档管理 ==="
        echo ""
        
        if ! lsof -i :$PORT -t >/dev/null 2>&1; then
            echo "服务未运行"
            exit 1
        fi
        
        STATS=$(curl -sf "http://localhost:$PORT/api/admin/document/stats" 2>/dev/null)
        
        if [ -n "$STATS" ]; then
            echo "$STATS" | python3 -m json.tool
        fi
        
        echo ""
        echo "操作:"
        echo "  1) 文档列表    ./scripts/doc.sh list"
        echo "  2) 搜索文档    ./scripts/search.sh query '关键词'"
        echo "  3) 批量操作    ./scripts/bulk.sh"
        ;;

    system)
        echo "=== 系统信息 ==="
        echo ""
        
        echo "--- 系统 ---"
        uname -a
        echo ""
        
        echo "--- 内存 ---"
        vm_stat 2>/dev/null | head -5 || free -m 2>/dev/null
        echo ""
        
        echo "--- 磁盘 ---"
        df -h . 2>/dev/null
        echo ""
        
        echo "--- Java ---"
        java -version 2>&1 | head -1
        echo ""
        
        echo "操作:"
        echo "  1) 环境检查    ./scripts/env-check.sh"
        echo "  2) 性能分析    ./scripts/perf.sh"
        echo "  3) 磁盘分析    ./scripts/disk-usage.sh"
        ;;

    monitor)
        echo "=== 实时监控 (Ctrl+C 退出) ==="
        echo ""
        
        while true; do
            clear
            ./scripts/dashboard.sh
            sleep 5
        done
        ;;

    *)
        echo "用法: $0 [overview|services|documents|system|monitor]"
        echo ""
        echo "  overview    - 总览仪表盘"
        echo "  services    - 服务管理"
        echo "  documents   - 文档管理"
        echo "  system      - 系统信息"
        echo "  monitor     - 实时监控"
        exit 1
        ;;
esac
