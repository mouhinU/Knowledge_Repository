#!/bin/bash
# Knowledge Repository Cron 任务管理脚本
# 用法: ./scripts/cron.sh [list|add|remove|edit|test]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository Cron 任务"
echo "=========================================="
echo ""

ACTION=${1:-list}
CRON_FILE="./data/crontab"

case "$ACTION" in
    list)
        echo "=== Cron 任务列表 ==="
        echo ""
        
        # 显示系统 crontab
        echo "--- 系统 Crontab ---"
        crontab -l 2>/dev/null || echo "无 crontab"
        
        echo ""
        
        # 显示项目 crontab
        if [ -f "$CRON_FILE" ]; then
            echo "--- 项目 Crontab ---"
            cat "$CRON_FILE"
        else
            echo "--- 项目 Crontab ---"
            echo "未配置"
        fi
        ;;

    add)
        if [ -z "$2" ] || [ -z "$3" ]; then
            echo "用法: $0 add <cron表达式> <命令>"
            echo ""
            echo "示例:"
            echo "  $0 add '0 2 * * *' './scripts/backup.sh'"
            echo "  $0 add '*/5 * * * *' './scripts/health-check.sh'"
            exit 1
        fi
        
        CRON_EXPR=$2
        CMD=$3
        
        mkdir -p ./data
        
        # 添加到项目 crontab
        echo "$CRON_EXPR $CMD" >> "$CRON_FILE"
        
        echo "Cron 任务已添加"
        echo ""
        echo "要安装到系统 crontab，运行:"
        echo "  crontab $CRON_FILE"
        ;;

    remove)
        if [ -z "$2" ]; then
            echo "用法: $0 remove <行号>"
            echo ""
            echo "使用 '$0 list' 查看行号"
            exit 1
        fi
        
        LINE_NUM=$2
        
        if [ ! -f "$CRON_FILE" ]; then
            echo "无 crontab 文件"
            exit 1
        fi
        
        sed -i.bak "${LINE_NUM}d" "$CRON_FILE"
        echo "已删除第 $LINE_NUM 行"
        ;;

    edit)
        echo "=== 编辑 Crontab ==="
        echo ""
        
        if [ ! -f "$CRON_FILE" ]; then
            mkdir -p ./data
            touch "$CRON_FILE"
        fi
        
        ${EDITOR:-vi} "$CRON_FILE"
        
        echo ""
        echo "要安装到系统 crontab，运行:"
        echo "  crontab $CRON_FILE"
        ;;

    test)
        if [ -z "$2" ]; then
            echo "用法: $0 test <cron表达式>"
            echo ""
            echo "测试 cron 表达式，显示接下来 5 次执行时间"
            exit 1
        fi
        
        CRON_EXPR=$2
        
        echo "=== Cron 表达式测试 ==="
        echo ""
        echo "表达式: $CRON_EXPR"
        echo ""
        echo "接下来 5 次执行时间 (模拟):"
        
        # 简单解析 cron 表达式
        python3 << EOF
from datetime import datetime, timedelta
import re

cron = '$CRON_EXPR'
parts = cron.split()

if len(parts) != 5:
    print("无效的 cron 表达式")
    exit(1)

minute, hour, day, month, weekday = parts

now = datetime.now()
print(f"当前时间: {now.strftime('%Y-%m-%d %H:%M:%S')}")
print()

# 简单的下次执行时间计算
for i in range(5):
    next_time = now + timedelta(minutes=(i+1)*5)
    print(f"  {i+1}. {next_time.strftime('%Y-%m-%d %H:%M:%S')}")
EOF
        ;;

    install)
        echo "=== 安装 Cron 任务 ==="
        echo ""
        
        if [ ! -f "$CRON_FILE" ]; then
            echo "无 crontab 文件"
            exit 1
        fi
        
        echo "将安装以下 cron 任务:"
        echo ""
        cat "$CRON_FILE"
        echo ""
        
        read -p "确认安装到系统 crontab? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        crontab "$CRON_FILE"
        
        if [ $? -eq 0 ]; then
            echo "Cron 任务已安装"
        else
            echo "安装失败"
        fi
        ;;

    uninstall)
        echo "=== 卸载 Cron 任务 ==="
        echo ""
        
        read -p "确认卸载所有 cron 任务? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        crontab -r
        
        if [ $? -eq 0 ]; then
            echo "Cron 任务已卸载"
        else
            echo "卸载失败或无 cron 任务"
        fi
        ;;

    template)
        echo "=== Cron 表达式示例 ==="
        echo ""
        echo "格式: 分 时 日 月 星期"
        echo ""
        echo "常用表达式:"
        echo "  */5 * * * *      - 每 5 分钟"
        echo "  0 * * * *        - 每小时"
        echo "  0 2 * * *        - 每天凌晨 2 点"
        echo "  0 9 * * 1-5      - 工作日 9 点"
        echo "  0 0 1 * *        - 每月 1 号"
        echo "  0 0 * * 0        - 每周日"
        echo ""
        echo "特殊字符:"
        echo "  *    - 任意值"
        echo "  */n  - 每 n 个"
        echo "  ,    - 列表 (1,3,5)"
        echo "  -    - 范围 (1-5)"
        ;;

    *)
        echo "用法: $0 [list|add|remove|edit|test|install|uninstall|template]"
        echo ""
        echo "  list                - 列出任务"
        echo "  add <表达式> <命令>  - 添加任务"
        echo "  remove <行号>       - 删除任务"
        echo "  edit                - 编辑 crontab"
        echo "  test <表达式>       - 测试表达式"
        echo "  install             - 安装到系统"
        echo "  uninstall           - 卸载系统任务"
        echo "  template            - 查看示例"
        exit 1
        ;;
esac
