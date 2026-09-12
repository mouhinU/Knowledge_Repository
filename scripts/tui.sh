#!/bin/bash
# Knowledge Repository 终端 UI 管理工具
# 用法: ./scripts/tui.sh

cd "$(dirname "$0")/.." || exit 1

# 检查是否安装了 dialog/whiptail
if command -v dialog >/dev/null 2>&1; then
    DIALOG=dialog
elif command -v whiptail >/dev/null 2>&1; then
    DIALOG=whiptail
else
    echo "需要安装 dialog 或 whiptail"
    echo "  macOS: brew install dialog"
    echo "  Ubuntu: apt-get install dialog"
    exit 1
fi

# 主菜单
show_menu() {
    while true; do
        CHOICE=$($DIALOG --clear --title "Knowledge Repository 管理" \
            --menu "请选择操作:" 20 60 12 \
            "1" "启动服务" \
            "2" "停止服务" \
            "3" "重启服务" \
            "4" "查看状态" \
            "5" "健康检查" \
            "6" "查看日志" \
            "7" "文档管理" \
            "8" "数据备份" \
            "9" "性能分析" \
            "10" "仪表盘" \
            "11" "环境检查" \
            "12" "退出" \
            2>&1 >/dev/tty)
        
        case "$CHOICE" in
            "1")
                ./scripts/start.sh
                $DIALOG --msgbox "操作完成，按确定继续" 10 40
                ;;
            "2")
                ./scripts/stop.sh
                $DIALOG --msgbox "操作完成，按确定继续" 10 40
                ;;
            "3")
                ./scripts/restart.sh
                $DIALOG --msgbox "操作完成，按确定继续" 10 40
                ;;
            "4")
                OUTPUT=$(./scripts/status.sh)
                $DIALOG --msgbox "$OUTPUT" 15 60
                ;;
            "5")
                OUTPUT=$(./scripts/health-check.sh)
                $DIALOG --msgbox "$OUTPUT" 20 70
                ;;
            "6")
                ./scripts/logs.sh 50
                ;;
            "7")
                show_doc_menu
                ;;
            "8")
                ./scripts/backup.sh
                $DIALOG --msgbox "备份完成，按确定继续" 10 40
                ;;
            "9")
                OUTPUT=$(./scripts/perf.sh status)
                $DIALOG --msgbox "$OUTPUT" 20 70
                ;;
            "10")
                clear
                ./scripts/dashboard.sh
                read -p "按回车继续..."
                ;;
            "11")
                OUTPUT=$(./scripts/env-check.sh)
                $DIALOG --msgbox "$OUTPUT" 20 70
                ;;
            "12"|"")
                exit 0
                ;;
        esac
    done
}

# 文档管理子菜单
show_doc_menu() {
    while true; do
        CHOICE=$($DIALOG --clear --title "文档管理" \
            --menu "请选择操作:" 15 50 6 \
            "1" "文档列表" \
            "2" "文档统计" \
            "3" "搜索文档" \
            "4" "重新索引" \
            "5" "批量操作" \
            "6" "返回主菜单" \
            2>&1 >/dev/tty)
        
        case "$CHOICE" in
            "1")
                OUTPUT=$(./scripts/doc.sh list)
                $DIALOG --msgbox "$OUTPUT" 20 70
                ;;
            "2")
                OUTPUT=$(./scripts/doc.sh stats)
                $DIALOG --msgbox "$OUTPUT" 15 60
                ;;
            "3")
                KEYWORD=$($DIALOG --inputbox "输入搜索关键词:" 10 40 2>&1 >/dev/tty)
                if [ -n "$KEYWORD" ]; then
                    OUTPUT=$(./scripts/search.sh query "$KEYWORD")
                    $DIALOG --msgbox "$OUTPUT" 20 70
                fi
                ;;
            "4")
                ./scripts/search.sh reindex all
                $DIALOG --msgbox "重新索引已启动" 10 40
                ;;
            "5")
                show_bulk_menu
                ;;
            "6"|"")
                return
                ;;
        esac
    done
}

# 批量操作子菜单
show_bulk_menu() {
    CHOICE=$($DIALOG --clear --title "批量操作" \
        --menu "请选择操作:" 12 50 4 \
        "1" "批量删除失败文档" \
        "2" "批量重新索引" \
        "3" "批量导出" \
        "4" "返回" \
        2>&1 >/dev/tty)
    
    case "$CHOICE" in
        "1")
            if $DIALOG --yesno "确认删除所有失败文档?" 10 40; then
                ./scripts/bulk.sh delete FAILED
                $DIALOG --msgbox "删除完成" 10 40
            fi
            ;;
        "2")
            if $DIALOG --yesno "确认重新索引所有文档?" 10 40; then
                ./scripts/bulk.sh reindex
                $DIALOG --msgbox "重新索引已启动" 10 40
            fi
            ;;
        "3")
            DIR=$($DIALOG --inputbox "输出目录:" 10 40 "./data/exports" 2>&1 >/dev/tty)
            if [ -n "$DIR" ]; then
                ./scripts/bulk.sh export "$DIR"
                $DIALOG --msgbox "导出完成: $DIR" 10 50
            fi
            ;;
        "4"|"")
            return
            ;;
    esac
}

# 启动主菜单
show_menu
