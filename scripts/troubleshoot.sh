#!/bin/bash
# Knowledge Repository 故障排查脚本
# 用法: ./troubleshoot.sh [check|fix|collect]

cd "$(dirname "$0")/.." || exit 1

PORT=8091

echo "=== Knowledge Repository 故障排查 ==="
echo ""

ACTION=${1:-check}

case "$ACTION" in
    check)
        echo "--- 基础检查 ---"
        echo ""
        
        # 1. 服务状态
        echo -n "1. 应用服务: "
        PID=$(lsof -i :$PORT -t 2>/dev/null)
        if [ -n "$PID" ]; then
            echo "运行中 (PID: $PID)"
        else
            echo "未运行 [问题]"
        fi
        
        # 2. Milvus
        echo -n "2. Milvus: "
        if curl -sf "http://localhost:9091/healthz" >/dev/null 2>&1; then
            echo "正常"
        else
            echo "未就绪 [问题]"
        fi
        
        # 3. Ollama
        echo -n "3. Ollama: "
        if curl -sf "http://localhost:11434/api/tags" >/dev/null 2>&1; then
            echo "正常"
        else
            echo "未运行 [问题]"
        fi
        
        # 4. 磁盘空间
        echo -n "4. 磁盘空间: "
        AVAIL=$(df -h . | tail -1 | awk '{print $4}')
        echo "可用 $AVAIL"
        
        # 5. 内存
        echo -n "5. 系统内存: "
        if command -v vm_stat >/dev/null 2>&1; then
            FREE=$(vm_stat | grep "Pages free" | awk '{print $3}' | tr -d '.')
            TOTAL=$(vm_stat | grep "Pages active\|Pages wired" | awk '{sum+=$3} END {print sum}' | tr -d '.')
            echo "正常"
        else
            free -m 2>/dev/null | awk 'NR==2 {printf "已用 %sMB / 总共 %sMB\n", $3, $2}'
        fi
        
        # 6. 日志错误
        echo -n "6. 最近错误: "
        if [ -f "/tmp/kb-server.log" ]; then
            ERRORS=$(grep -ci "error\|exception" /tmp/kb-server.log 2>/dev/null)
            if [ "$ERRORS" -gt 10 ]; then
                echo "$ERRORS 条 [警告]"
            else
                echo "$ERRORS 条"
            fi
        else
            echo "无日志文件"
        fi
        ;;

    fix)
        ISSUE=${2:-}
        
        if [ -z "$ISSUE" ]; then
            echo "用法: $0 fix <问题类型>"
            echo ""
            echo "问题类型:"
            echo "  port       - 端口被占用"
            echo "  milvus     - Milvus 连接失败"
            echo "  memory     - 内存不足"
            echo "  disk       - 磁盘空间不足"
            echo "  permission - 权限问题"
            exit 1
        fi
        
        case "$ISSUE" in
            port)
                echo "=== 修复端口占用 ==="
                PID=$(lsof -i :$PORT -t 2>/dev/null)
                if [ -n "$PID" ]; then
                    echo "端口 $PORT 被 PID $PID 占用"
                    read -p "是否终止该进程? (y/N): " CONFIRM
                    if [ "$CONFIRM" = "y" ]; then
                        kill -9 $PID 2>/dev/null
                        echo "已终止"
                    fi
                else
                    echo "端口未被占用"
                fi
                ;;
            milvus)
                echo "=== 修复 Milvus 连接 ==="
                ./scripts/compose.sh status
                echo ""
                read -p "是否重启 Milvus? (y/N): " CONFIRM
                if [ "$CONFIRM" = "y" ]; then
                    ./scripts/compose.sh down
                    ./scripts/compose.sh up
                fi
                ;;
            memory)
                echo "=== 内存优化 ==="
                echo "清理系统缓存..."
                sudo purge 2>/dev/null || echo "需要 sudo 权限"
                echo ""
                echo "建议: 增加 JVM 堆内存限制"
                echo "  export JAVA_OPTS=\"-Xmx2g\""
                ;;
            disk)
                echo "=== 磁盘清理 ==="
                ./scripts/disk-usage.sh
                echo ""
                read -p "是否清理旧备份? (y/N): " CONFIRM
                if [ "$CONFIRM" = "y" ]; then
                    find ./data/backups -name "*.tar.gz" -mtime +30 -delete 2>/dev/null
                    echo "已清理30天前的备份"
                fi
                ;;
            permission)
                echo "=== 修复权限 ==="
                echo "修复 data 目录权限..."
                chmod -R 755 ./data 2>/dev/null
                chmod 600 ./data/*.db 2>/dev/null
                echo "完成"
                ;;
        esac
        ;;

    collect)
        COLLECT_DIR="./data/diagnostics"
        mkdir -p "$COLLECT_DIR"
        
        TIMESTAMP=$(date +%Y%m%d_%H%M%S)
        COLLECT_PATH="$COLLECT_DIR/diag_$TIMESTAMP"
        mkdir -p "$COLLECT_PATH"
        
        echo "收集诊断信息..."
        
        # 系统信息
        echo "收集系统信息..."
        uname -a > "$COLLECT_PATH/system.txt"
        df -h >> "$COLLECT_PATH/system.txt"
        
        # Java 信息
        echo "收集 Java 信息..."
        java -version > "$COLLECT_PATH/java.txt" 2>&1
        
        # 日志
        if [ -f "/tmp/kb-server.log" ]; then
            cp /tmp/kb-server.log "$COLLECT_PATH/"
            echo "日志已收集"
        fi
        
        # 配置
        cp ./knowledge-web/src/main/resources/application.yml "$COLLECT_PATH/" 2>/dev/null
        echo "配置已收集"
        
        # 打包
        cd "$COLLECT_DIR" && tar -czf "diag_$TIMESTAMP.tar.gz" "diag_$TIMESTAMP" && rm -rf "diag_$TIMESTAMP"
        
        echo ""
        echo "诊断信息已收集: $COLLECT_DIR/diag_$TIMESTAMP.tar.gz"
        ;;

    *)
        echo "用法: $0 [check|fix|collect]"
        echo ""
        echo "  check              - 检查常见问题"
        echo "  fix <类型>         - 修复指定问题"
        echo "  collect            - 收集诊断信息"
        exit 1
        ;;
esac
