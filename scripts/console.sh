#!/bin/bash
# Knowledge Repository 交互式控制台
# 用法: ./scripts/console.sh

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 交互式控制台"
echo "  输入 'help' 查看可用命令，'exit' 退出"
echo "=========================================="
echo ""

while true; do
    read -p "KB> " CMD ARGS
    
    case "$CMD" in
        help|h)
            echo ""
            echo "可用命令:"
            echo "  服务管理:"
            echo "    start         - 启动服务"
            echo "    stop          - 停止服务"
            echo "    restart       - 重启服务"
            echo "    status        - 查看状态"
            echo "    logs [N]      - 查看日志"
            echo ""
            echo "  文档管理:"
            echo "    doc list      - 文档列表"
            echo "    doc stats     - 文档统计"
            echo "    search <词>   - 搜索文档"
            echo ""
            echo "  系统:"
            echo "    health        - 健康检查"
            echo "    perf          - 性能概览"
            echo "    disk          - 磁盘使用"
            echo "    dashboard     - 仪表盘"
            echo ""
            echo "  其他:"
            echo "    clear         - 清屏"
            echo "    exit/quit     - 退出"
            echo ""
            ;;

        start)
            ./scripts/start.sh
            ;;

        stop)
            ./scripts/stop.sh
            ;;

        restart)
            ./scripts/restart.sh
            ;;

        status)
            ./scripts/status.sh
            ;;

        logs)
            ./scripts/logs.sh ${ARGS:-100}
            ;;

        doc)
            case "$ARGS" in
                list)
                    ./scripts/doc.sh list
                    ;;
                stats)
                    ./scripts/doc.sh stats
                    ;;
                *)
                    ./scripts/doc.sh $ARGS
                    ;;
            esac
            ;;

        search)
            if [ -z "$ARGS" ]; then
                echo "用法: search <关键词>"
            else
                ./scripts/search.sh query "$ARGS"
            fi
            ;;

        health)
            ./scripts/health-check.sh
            ;;

        perf)
            ./scripts/perf.sh status
            ;;

        disk)
            ./scripts/disk-usage.sh
            ;;

        dashboard)
            ./scripts/dashboard.sh
            ;;

        backup)
            ./scripts/backup.sh
            ;;

        clear|cls)
            clear
            ;;

        exit|quit|q)
            echo "再见！"
            exit 0
            ;;

        "")
            # 空命令，忽略
            ;;

        *)
            echo "未知命令: $CMD"
            echo "输入 'help' 查看可用命令"
            ;;
    esac
    
    echo ""
done
