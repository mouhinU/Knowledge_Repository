#!/bin/bash
# Knowledge Repository 监控指标查看脚本
# 用法: ./metrics.sh [all|jvm|http|custom|prometheus]

cd "$(dirname "$0")/.." || exit 1

PORT=8091
ACTUATOR_BASE="http://localhost:$PORT/actuator"

# 检查服务
if ! lsof -i :$PORT -t >/dev/null 2>&1; then
    echo "服务未运行，请先启动服务"
    exit 1
fi

ACTION=${1:-all}

case "$ACTION" in
    all)
        echo "=== Knowledge Repository 监控指标 ==="
        echo ""
        
        echo "--- 健康检查 ---"
        curl -sf "$ACTUATOR_BASE/health" 2>/dev/null | python3 -m json.tool 2>/dev/null
        echo ""
        
        echo "--- 内存使用 ---"
        curl -sf "$ACTUATOR_BASE/metrics/jvm.memory.used" 2>/dev/null | python3 -m json.tool 2>/dev/null
        echo ""
        
        echo "--- HTTP 请求统计 ---"
        curl -sf "$ACTUATOR_BASE/metrics/http.server.requests" 2>/dev/null | python3 -m json.tool 2>/dev/null
        ;;

    jvm)
        echo "=== JVM 指标 ==="
        echo ""
        
        echo "--- 内存 ---"
        curl -sf "$ACTUATOR_BASE/metrics/jvm.memory.used" 2>/dev/null | python3 -m json.tool
        echo ""
        
        echo "--- GC ---"
        curl -sf "$ACTUATOR_BASE/metrics/jvm.gc.pause" 2>/dev/null | python3 -m json.tool
        echo ""
        
        echo "--- 线程 ---"
        curl -sf "$ACTUATOR_BASE/metrics/jvm.threads.live" 2>/dev/null | python3 -m json.tool
        ;;

    http)
        echo "=== HTTP 请求指标 ==="
        echo ""
        
        curl -sf "$ACTUATOR_BASE/metrics/http.server.requests" 2>/dev/null | python3 -m json.tool
        ;;

    custom)
        echo "=== 自定义业务指标 ==="
        echo ""
        
        # 文档处理统计
        echo "--- 文档统计 ---"
        curl -sf "http://localhost:$PORT/api/admin/document/stats" 2>/dev/null | python3 -m json.tool
        echo ""
        
        # 这里可以添加更多自定义指标
        ;;

    prometheus)
        echo "=== Prometheus 格式指标 ==="
        echo ""
        echo "访问: $ACTUATOR_BASE/prometheus"
        echo ""
        curl -sf "$ACTUATOR_BASE/prometheus" 2>/dev/null | head -50
        ;;

    info)
        echo "=== 应用信息 ==="
        curl -sf "$ACTUATOR_BASE/info" 2>/dev/null | python3 -m json.tool
        ;;

    env)
        echo "=== 环境变量 ==="
        curl -sf "$ACTUATOR_BASE/env" 2>/dev/null | python3 -m json.tool | head -100
        ;;

    *)
        echo "用法: $0 [all|jvm|http|custom|prometheus|info|env]"
        echo ""
        echo "  all         - 查看所有指标"
        echo "  jvm         - JVM 相关指标"
        echo "  http        - HTTP 请求指标"
        echo "  custom      - 自定义业务指标"
        echo "  prometheus  - Prometheus 格式输出"
        echo "  info        - 应用信息"
        echo "  env         - 环境变量"
        exit 1
        ;;
esac
