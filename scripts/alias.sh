#!/bin/bash
# Knowledge Repository 快捷命令脚本
# 用法: source ./scripts/alias.sh

# 保存当前脚本目录
export KB_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export KB_SCRIPTS="$KB_HOME/scripts"

# 服务管理
alias kb-start='$KB_SCRIPTS/start.sh'
alias kb-stop='$KB_SCRIPTS/stop.sh'
alias kb-restart='$KB_SCRIPTS/restart.sh'
alias kb-status='$KB_SCRIPTS/status.sh'
alias kb-logs='$KB_SCRIPTS/logs.sh'

# 部署运维
alias kb-deploy='$KB_SCRIPTS/deploy.sh'
alias kb-health='$KB_SCRIPTS/health-check.sh'
alias kb-env='$KB_SCRIPTS/env-check.sh'

# 数据管理
alias kb-backup='$KB_SCRIPTS/backup.sh'
alias kb-clean='$KB_SCRIPTS/clean.sh'
alias kb-disk='$KB_SCRIPTS/disk-usage.sh'

# 业务管理
alias kb-doc='$KB_SCRIPTS/doc.sh'
alias kb-user='$KB_SCRIPTS/user.sh'
alias kb-search='$KB_SCRIPTS/search.sh'

# 监控诊断
alias kb-perf='$KB_SCRIPTS/perf.sh'
alias kb-metrics='$KB_SCRIPTS/metrics.sh'
alias kb-monitor='$KB_SCRIPTS/monitor.sh'
alias kb-network='$KB_SCRIPTS/network.sh'

# 安全审计
alias kb-security='$KB_SCRIPTS/security.sh'
alias kb-audit='$KB_SCRIPTS/audit.sh'

# 调试排错
alias kb-debug='$KB_SCRIPTS/debug.sh'
alias kb-troubleshoot='$KB_SCRIPTS/troubleshoot.sh'
alias kb-log='$KB_SCRIPTS/log.sh'

# 其他
alias kb-version='$KB_SCRIPTS/version.sh'
alias kb-help='$KB_SCRIPTS/help.sh'

# 快速命令
alias kb-ll='ls -la $KB_SCRIPTS/'
alias kb-cd='cd $KB_HOME'

echo "Knowledge Repository 快捷命令已加载"
echo "使用 kb-help 查看所有命令"
echo ""
echo "常用命令:"
echo "  kb-start      - 启动服务"
echo "  kb-stop       - 停止服务"
echo "  kb-status     - 查看状态"
echo "  kb-logs       - 查看日志"
echo "  kb-health     - 健康检查"
echo "  kb-search q '关键词' - 搜索"
