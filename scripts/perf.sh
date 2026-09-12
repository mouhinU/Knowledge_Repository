#!/bin/bash
# Knowledge Repository 性能分析脚本
# 用法: ./perf.sh [status|cpu|memory|gc|thread|slow]

cd "$(dirname "$0")/.." || exit 1

PORT=8091
PID=$(lsof -i :$PORT -t 2>/dev/null)

if [ -z "$PID" ]; then
    echo "服务未运行"
    exit 1
fi

ACTION=${1:-status}

case "$ACTION" in
    status)
        echo "=== Knowledge Repository 性能概览 ==="
        echo ""
        
        echo "--- 进程信息 ---"
        echo "PID: $PID"
        ps -p $PID -o pid,pcpu,pmem,rss,vsz,etime 2>/dev/null | tail -1
        
        echo ""
        echo "--- JVM 内存 ---"
        curl -sf "http://localhost:$PORT/actuator/metrics/jvm.memory.used" 2>/dev/null | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(f\"已用内存: {data['measurements'][0]['value'] / 1024 / 1024:.1f} MB\")
" 2>/dev/null
        
        echo ""
        echo "--- 线程数 ---"
        curl -sf "http://localhost:$PORT/actuator/metrics/jvm.threads.live" 2>/dev/null | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(f\"活跃线程: {data['measurements'][0]['value']}\")
" 2>/dev/null
        ;;

    cpu)
        echo "=== CPU 使用分析 ==="
        echo ""
        
        echo "--- 进程 CPU ---"
        ps -p $PID -o pid,pcpu,time 2>/dev/null | tail -1
        
        echo ""
        echo "--- 系统 CPU ---"
        top -l 1 -n 0 | grep "CPU usage"
        
        echo ""
        echo "--- 负载 ---"
        uptime
        ;;

    memory)
        echo "=== 内存使用分析 ==="
        echo ""
        
        echo "--- JVM 堆内存 ---"
        curl -sf "http://localhost:$PORT/actuator/metrics/jvm.memory.used" 2>/dev/null | python3 -c "
import sys, json
data = json.load(sys.stdin)
for m in data['measurements']:
    if m['statistic'] == 'VALUE':
        print(f\"已用: {m['value'] / 1024 / 1024:.1f} MB\")
" 2>/dev/null
        
        curl -sf "http://localhost:$PORT/actuator/metrics/jvm.memory.max" 2>/dev/null | python3 -c "
import sys, json
data = json.load(sys.stdin)
for m in data['measurements']:
    if m['statistic'] == 'VALUE':
        print(f\"最大: {m['value'] / 1024 / 1024:.1f} MB\")
" 2>/dev/null
        
        echo ""
        echo "--- 进程内存 ---"
        ps -p $PID -o rss,vsz 2>/dev/null | tail -1
        
        echo ""
        echo "--- 系统内存 ---"
        vm_stat 2>/dev/null | head -10 || free -m 2>/dev/null
        ;;

    gc)
        echo "=== GC 分析 ==="
        echo ""
        
        echo "--- GC 暂停时间 ---"
        curl -sf "http://localhost:$PORT/actuator/metrics/jvm.gc.pause" 2>/dev/null | python3 -c "
import sys, json
data = json.load(sys.stdin)
for m in data['measurements']:
    stat = m['statistic']
    val = m['value']
    if stat == 'COUNT':
        print(f\"GC 次数: {int(val)}\")
    elif stat == 'TOTAL_TIME':
        print(f\"总暂停: {val * 1000:.1f} ms\")
    elif stat == 'MAX':
        print(f\"最大暂停: {val * 1000:.1f} ms\")
" 2>/dev/null
        
        echo ""
        echo "--- 内存分配率 ---"
        curl -sf "http://localhost:$PORT/actuator/metrics/jvm.gc.memory.allocated" 2>/dev/null | python3 -c "
import sys, json
data = json.load(sys.stdin)
val = data['measurements'][0]['value']
print(f\"已分配: {val / 1024 / 1024:.1f} MB\")
" 2>/dev/null
        ;;

    thread)
        echo "=== 线程分析 ==="
        echo ""
        
        echo "--- 线程统计 ---"
        curl -sf "http://localhost:$PORT/actuator/metrics/jvm.threads.live" 2>/dev/null | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(f\"活跃线程: {data['measurements'][0]['value']}\")
" 2>/dev/null
        
        curl -sf "http://localhost:$PORT/actuator/metrics/jvm.threads.daemon" 2>/dev/null | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(f\"守护线程: {data['measurements'][0]['value']}\")
" 2>/dev/null
        
        curl -sf "http://localhost:$PORT/actuator/metrics/jvm.threads.peak" 2>/dev/null | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(f\"峰值线程: {data['measurements'][0]['value']}\")
" 2>/dev/null
        
        echo ""
        echo "--- 线程转储 (前20行) ---"
        jstack $PID 2>/dev/null | head -20
        ;;

    slow)
        echo "=== 慢请求分析 ==="
        echo ""
        
        if [ -f "/tmp/kb-server.log" ]; then
            echo "--- 响应时间超过1秒的请求 ---"
            grep -i "slow\|took.*ms\|duration" /tmp/kb-server.log 2>/dev/null | tail -20
            
            echo ""
            echo "--- 错误请求 ---"
            grep -i "error\|exception" /tmp/kb-server.log 2>/dev/null | tail -10
        else
            echo "日志文件不存在"
        fi
        ;;

    top)
        echo "=== 实时性能监控 (Ctrl+C 退出) ==="
        echo ""
        top -pid $PID
        ;;

    *)
        echo "用法: $0 [status|cpu|memory|gc|thread|slow|top]"
        echo ""
        echo "  status    - 性能概览"
        echo "  cpu       - CPU 分析"
        echo "  memory    - 内存分析"
        echo "  gc        - GC 分析"
        echo "  thread    - 线程分析"
        echo "  slow      - 慢请求分析"
        echo "  top       - 实时监控"
        exit 1
        ;;
esac
