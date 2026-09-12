#!/bin/bash
# Knowledge Repository 调试工具脚本
# 用法: ./debug.sh [info|dump|heap|thread|jstack]

cd "$(dirname "$0")/.." || exit 1

PORT=8091
PID=$(lsof -i :$PORT -t 2>/dev/null)

if [ -z "$PID" ]; then
    echo "服务未运行"
    exit 1
fi

ACTION=${1:-info}

case "$ACTION" in
    info)
        echo "=== 调试信息 ==="
        echo ""
        echo "PID: $PID"
        echo "端口: $PORT"
        echo "时间: $(date)"
        echo ""
        
        echo "--- Java 版本 ---"
        java -version 2>&1
        
        echo ""
        echo "--- JVM 参数 ---"
        jcmd $PID VM.flags 2>/dev/null | head -20
        
        echo ""
        echo "--- 环境变量 ---"
        env | grep -i "java\|spring\|kb" | sort
        ;;

    dump)
        DUMP_DIR="./data/dumps"
        mkdir -p "$DUMP_DIR"
        
        TIMESTAMP=$(date +%Y%m%d_%H%M%S)
        DUMP_PATH="$DUMP_DIR/dump_$TIMESTAMP"
        
        echo "生成线程转储: $DUMP_PATH"
        jstack $PID > "${DUMP_PATH}_thread.txt" 2>/dev/null
        echo "线程转储完成"
        
        echo ""
        echo "生成堆转储 (可能需要较长时间)..."
        read -p "是否生成堆转储? (y/N): " CONFIRM
        if [ "$CONFIRM" = "y" ]; then
            jmap -dump:format=b,file="${DUMP_PATH}_heap.bin" $PID 2>/dev/null
            echo "堆转储完成: ${DUMP_PATH}_heap.bin"
        fi
        ;;

    heap)
        echo "=== 堆内存分析 ==="
        echo ""
        
        echo "--- 堆内存使用 ---"
        jcmd $PID GC.heap_info 2>/dev/null
        
        echo ""
        echo "--- 对象统计 (前30) ---"
        jmap -histo $PID 2>/dev/null | head -35
        ;;

    thread)
        echo "=== 线程转储 ==="
        echo ""
        jstack $PID 2>/dev/null
        ;;

    jstack)
        echo "=== 线程栈分析 ==="
        echo ""
        
        echo "--- 阻塞线程 ---"
        jstack $PID 2>/dev/null | grep -A5 "BLOCKED" | head -30
        
        echo ""
        echo "--- 等待线程 ---"
        jstack $PID 2>/dev/null | grep -A5 "WAITING" | head -30
        ;;

    gc)
        echo "=== GC 日志 ==="
        echo ""
        
        echo "--- GC 统计 ---"
        jstat -gc $PID 2>/dev/null
        
        echo ""
        echo "--- GC 利用率 ---"
        jstat -gcutil $PID 1000 5 2>/dev/null
        ;;

    sysprops)
        echo "=== 系统属性 ==="
        echo ""
        jcmd $PID VM.system_properties 2>/dev/null | sort
        ;;

    vm)
        echo "=== JVM 信息 ==="
        echo ""
        jcmd $PID VM.version 2>/dev/null
        
        echo ""
        echo "--- VM 标志 ---"
        jcmd $PID VM.flags 2>/dev/null
        ;;

    *)
        echo "用法: $0 [info|dump|heap|thread|jstack|gc|sysprops|vm]"
        echo ""
        echo "  info       - 调试信息概览"
        echo "  dump       - 生成转储文件"
        echo "  heap       - 堆内存分析"
        echo "  thread     - 线程转储"
        echo "  jstack     - 线程栈分析"
        echo "  gc         - GC 统计"
        echo "  sysprops   - 系统属性"
        echo "  vm         - JVM 信息"
        exit 1
        ;;
esac
