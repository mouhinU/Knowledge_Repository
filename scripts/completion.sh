#!/bin/bash
# Knowledge Repository Bash/Zsh 自动补全脚本
# 用法: source ./scripts/completion.sh

_kb_complete() {
    local cur prev opts
    COMPREPLY=()
    cur="${COMP_WORDS[COMP_CWORD]}"
    prev="${COMP_WORDS[COMP_CWORD-1]}"
    
    # 主命令
    opts="start stop restart status logs deploy env-check health-check docker-build compose backup export import init-db clean doc user dept role config search monitor metrics perf disk-usage network report security audit session apikey webhook schedule notify plugin theme cache ratelimit task debug troubleshoot log api-test help version"
    
    # 子命令
    case "${prev}" in
        doc)
            COMPREPLY=( $(compgen -W "list info delete reindex search stats" -- ${cur}) )
            return 0
            ;;
        user)
            COMPREPLY=( $(compgen -W "list add delete reset-password" -- ${cur}) )
            return 0
            ;;
        dept)
            COMPREPLY=( $(compgen -W "list tree add delete" -- ${cur}) )
            return 0
            ;;
        role)
            COMPREPLY=( $(compgen -W "list add delete assign permissions" -- ${cur}) )
            return 0
            ;;
        config)
            COMPREPLY=( $(compgen -W "list get set delete reload" -- ${cur}) )
            return 0
            ;;
        search)
            COMPREPLY=( $(compgen -W "query reindex stats optimize clear" -- ${cur}) )
            return 0
            ;;
        compose)
            COMPREPLY=( $(compgen -W "up down status logs restart" -- ${cur}) )
            return 0
            ;;
        metrics)
            COMPREPLY=( $(compgen -W "all jvm http custom prometheus info env" -- ${cur}) )
            return 0
            ;;
        perf)
            COMPREPLY=( $(compgen -W "status cpu memory gc thread slow top" -- ${cur}) )
            return 0
            ;;
        network)
            COMPREPLY=( $(compgen -W "status ping ports connections dns speed trace" -- ${cur}) )
            return 0
            ;;
        security)
            COMPREPLY=( $(compgen -W "status scan firewall cert audit password" -- ${cur}) )
            return 0
            ;;
        audit)
            COMPREPLY=( $(compgen -W "list search user export clean stats" -- ${cur}) )
            return 0
            ;;
        debug)
            COMPREPLY=( $(compgen -W "info dump heap thread jstack gc sysprops vm" -- ${cur}) )
            return 0
            ;;
        troubleshoot)
            COMPREPLY=( $(compgen -W "check fix collect" -- ${cur}) )
            return 0
            ;;
        log)
            COMPREPLY=( $(compgen -W "view follow level rotate clean search error size" -- ${cur}) )
            return 0
            ;;
        cache)
            COMPREPLY=( $(compgen -W "status clear keys stats warm" -- ${cur}) )
            return 0
            ;;
        task)
            COMPREPLY=( $(compgen -W "list status cancel retry clean stats queue" -- ${cur}) )
            return 0
            ;;
        session)
            COMPREPLY=( $(compgen -W "list clear info kick stats config" -- ${cur}) )
            return 0
            ;;
        *)
            ;;
    esac
    
    COMPREPLY=( $(compgen -W "${opts}" -- ${cur}) )
    return 0
}

# 绑定补全函数
complete -F _kb_complete ./scripts/start.sh
complete -F _kb_complete ./scripts/stop.sh
complete -F _kb_complete ./scripts/restart.sh
complete -F _kb_complete ./scripts/status.sh
complete -F _kb_complete ./scripts/logs.sh
complete -F _kb_complete ./scripts/deploy.sh
complete -F _kb_complete ./scripts/env-check.sh
complete -F _kb_complete ./scripts/health-check.sh
complete -F _kb_complete ./scripts/docker-build.sh
complete -F _kb_complete ./scripts/compose.sh
complete -F _kb_complete ./scripts/backup.sh
complete -F _kb_complete ./scripts/export.sh
complete -F _kb_complete ./scripts/import.sh
complete -F _kb_complete ./scripts/init-db.sh
complete -F _kb_complete ./scripts/clean.sh
complete -F _kb_complete ./scripts/doc.sh
complete -F _kb_complete ./scripts/user.sh
complete -F _kb_complete ./scripts/dept.sh
complete -F _kb_complete ./scripts/role.sh
complete -F _kb_complete ./scripts/config.sh
complete -F _kb_complete ./scripts/search.sh
complete -F _kb_complete ./scripts/monitor.sh
complete -F _kb_complete ./scripts/metrics.sh
complete -F _kb_complete ./scripts/perf.sh
complete -F _kb_complete ./scripts/disk-usage.sh
complete -F _kb_complete ./scripts/network.sh
complete -F _kb_complete ./scripts/report.sh
complete -F _kb_complete ./scripts/security.sh
complete -F _kb_complete ./scripts/audit.sh
complete -F _kb_complete ./scripts/session.sh
complete -F _kb_complete ./scripts/apikey.sh
complete -F _kb_complete ./scripts/webhook.sh
complete -F _kb_complete ./scripts/schedule.sh
complete -F _kb_complete ./scripts/notify.sh
complete -F _kb_complete ./scripts/plugin.sh
complete -F _kb_complete ./scripts/theme.sh
complete -F _kb_complete ./scripts/cache.sh
complete -F _kb_complete ./scripts/ratelimit.sh
complete -F _kb_complete ./scripts/task.sh
complete -F _kb_complete ./scripts/debug.sh
complete -F _kb_complete ./scripts/troubleshoot.sh
complete -F _kb_complete ./scripts/log.sh
complete -F _kb_complete ./scripts/api-test.sh
complete -F _kb_complete ./scripts/help.sh
complete -F _kb_complete ./scripts/version.sh

echo "Knowledge Repository 自动补全已加载"
echo "现在可以使用 Tab 键自动补全命令和参数"
