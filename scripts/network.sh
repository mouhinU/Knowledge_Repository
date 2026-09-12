#!/bin/bash
# Knowledge Repository 网络诊断脚本
# 用法: ./network.sh [status|ping|ports|connections|dns]

cd "$(dirname "$0")/.." || exit 1

PORT=8091

echo "=== Knowledge Repository 网络诊断 ==="
echo ""

ACTION=${1:-status}

case "$ACTION" in
    status)
        echo "--- 网络接口 ---"
        ifconfig | grep -E "^[a-z]|inet " | head -20
        
        echo ""
        echo "--- 服务端口 ---"
        lsof -i :$PORT 2>/dev/null | grep LISTEN
        
        echo ""
        echo "--- 外部连接 ---"
        lsof -i :$PORT 2>/dev/null | grep ESTABLISHED | head -10
        ;;

    ping)
        echo "--- 服务连通性测试 ---"
        echo ""
        
        # 测试应用服务
        echo -n "应用服务 (localhost:$PORT): "
        if curl -sf -o /dev/null --max-time 3 "http://localhost:$PORT/admin.html" 2>/dev/null; then
            echo "OK"
        else
            echo "FAIL"
        fi
        
        # 测试 Milvus
        echo -n "Milvus (localhost:19530): "
        if curl -sf -o /dev/null --max-time 3 "http://localhost:9091/healthz" 2>/dev/null; then
            echo "OK"
        else
            echo "FAIL"
        fi
        
        # 测试 Ollama
        echo -n "Ollama (localhost:11434): "
        if curl -sf -o /dev/null --max-time 3 "http://localhost:11434/api/tags" 2>/dev/null; then
            echo "OK"
        else
            echo "FAIL"
        fi
        ;;

    ports)
        echo "--- 相关端口状态 ---"
        echo ""
        
        PORTS="8091 19530 9091 11434 9001 2379"
        
        for P in $PORTS; do
            PID=$(lsof -i :$P -t 2>/dev/null)
            if [ -n "$PID" ]; then
                PROC=$(ps -p $PID -o comm= 2>/dev/null)
                echo "端口 $P: 占用 (PID: $PID, 进程: $PROC)"
            else
                echo "端口 $P: 空闲"
            fi
        done
        ;;

    connections)
        echo "--- 连接统计 ---"
        echo ""
        
        if ! lsof -i :$PORT -t >/dev/null 2>&1; then
            echo "服务未运行"
            exit 1
        fi
        
        echo "按状态统计:"
        lsof -i :$PORT 2>/dev/null | awk 'NR>1 {print $10}' | cut -d'(' -f1 | sort | uniq -c
        
        echo ""
        echo "按来源IP统计:"
        lsof -i :$PORT 2>/dev/null | awk 'NR>1 {print $9}' | cut -d'>' -f2 | cut -d':' -f1 | sort | uniq -c | sort -rn | head -10
        ;;

    dns)
        echo "--- DNS 解析测试 ---"
        echo ""
        
        HOSTS="localhost localhost.localdomain"
        
        for H in $HOSTS; do
            echo -n "$H: "
            if host "$H" >/dev/null 2>&1; then
                host "$H" | head -1
            else
                echo "无法解析"
            fi
        done
        
        echo ""
        echo "--- /etc/hosts ---"
        cat /etc/hosts | grep -v "^#" | grep -v "^$"
        ;;

    speed)
        echo "--- 接口响应速度测试 ---"
        echo ""
        
        if ! lsof -i :$PORT -t >/dev/null 2>&1; then
            echo "服务未运行"
            exit 1
        fi
        
        ENDPOINTS="/admin.html /api/admin/document/stats /api/admin/document/list"
        
        for EP in $ENDPOINTS; do
            TIME=$(curl -sf -o /dev/null -w "%{time_total}" "http://localhost:$PORT$EP" 2>/dev/null)
            printf "%-35s %ss\n" "$EP" "$TIME"
        done
        ;;

    trace)
        echo "--- 网络路由追踪 ---"
        echo ""
        
        if command -v traceroute >/dev/null 2>&1; then
            traceroute -m 5 localhost 2>/dev/null
        else
            echo "traceroute 未安装"
        fi
        ;;

    *)
        echo "用法: $0 [status|ping|ports|connections|dns|speed|trace]"
        echo ""
        echo "  status        - 网络状态概览"
        echo "  ping          - 连通性测试"
        echo "  ports         - 端口状态"
        echo "  connections   - 连接统计"
        echo "  dns           - DNS 诊断"
        echo "  speed         - 接口响应速度"
        echo "  trace         - 路由追踪"
        exit 1
        ;;
esac
