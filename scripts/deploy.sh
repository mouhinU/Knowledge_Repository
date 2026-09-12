#!/bin/bash
# Knowledge Repository 部署脚本
# 一键部署完整环境: Milvus + 应用服务

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 一键部署"
echo "=========================================="
echo ""

# 1. 环境检查
echo ">>> 步骤 1/4: 环境检查"
./scripts/env-check.sh
if [ $? -ne 0 ]; then
    echo "环境检查未通过，请先解决上述问题"
    exit 1
fi
echo ""

# 2. 启动 Milvus
echo ">>> 步骤 2/4: 启动 Milvus 向量数据库"
if curl -sf "http://localhost:9091/healthz" >/dev/null 2>&1; then
    echo "    Milvus 已在运行，跳过"
else
    ./scripts/compose.sh up
    if [ $? -ne 0 ]; then
        echo "Milvus 启动失败"
        exit 1
    fi
fi
echo ""

# 3. 检查 Ollama
echo ">>> 步骤 3/4: 检查 Embedding 模型"
if curl -sf "http://localhost:11434/api/tags" >/dev/null 2>&1; then
    echo "    Ollama 已在运行"
else
    echo "    Ollama 未运行，请手动启动:"
    echo "    ollama serve"
    echo "    ollama pull bge-m3"
    echo ""
    read -p "是否继续部署应用? (y/N): " CONT
    if [ "$CONT" != "y" ] && [ "$CONT" != "Y" ]; then
        exit 0
    fi
fi
echo ""

# 4. 启动应用
echo ">>> 步骤 4/4: 启动应用服务"
./scripts/start.sh

echo ""
echo "=========================================="
echo "  部署完成"
echo "=========================================="
echo ""
echo "  管理控制台: http://localhost:8091/admin.html"
echo "  默认账号:   admin / admin"
echo ""
echo "  常用命令:"
echo "    查看状态: ./scripts/status.sh"
echo "    查看日志: ./scripts/logs.sh"
echo "    停止服务: ./scripts/stop.sh"
echo "    健康检查: ./scripts/health-check.sh"
