#!/bin/bash
# Knowledge Repository 数据库初始化脚本
# 警告: 会清除所有数据！

cd "$(dirname "$0")/.." || exit 1

PORT=8091
PID=$(lsof -i :$PORT -t 2>/dev/null)

if [ -n "$PID" ]; then
    echo "服务正在运行，请先停止服务"
    echo "  ./scripts/stop.sh"
    exit 1
fi

echo "=========================================="
echo "  数据库初始化工具"
echo "  警告: 这将清除所有数据！"
echo "=========================================="
echo ""
read -p "确认要清除所有数据吗? (y/N): " CONFIRM

if [ "$CONFIRM" != "y" ] && [ "$CONFIRM" != "Y" ]; then
    echo "已取消"
    exit 0
fi

# 1. 删除 H2 数据库文件
if [ -d "./data" ]; then
    H2_FILES=$(find ./data -name "knowledge-repository*.db*" 2>/dev/null)
    if [ -n "$H2_FILES" ]; then
        rm -f ./data/knowledge-repository*.db*
        echo "已删除 H2 数据库文件"
    fi
fi

# 2. 删除上传文档
if [ -d "./data/documents" ]; then
    rm -rf ./data/documents/*
    echo "已清除上传文档"
fi

# 3. 清理 Milvus 集合（如果 Milvus 在运行）
if curl -sf http://localhost:9091/healthz >/dev/null 2>&1; then
    echo ""
    read -p "是否同时清除 Milvus 向量数据? (y/N): " CLEAR_MILVUS
    if [ "$CLEAR_MILVUS" = "y" ] || [ "$CLEAR_MILVUS" = "Y" ]; then
        # 通过 API 或直接删除 Milvus collection
        curl -sf -X DELETE "http://localhost:19530/v1/vector/collections/knowledge_chunks" 2>/dev/null
        echo "已清除 Milvus 向量数据"
    fi
fi

echo ""
echo "初始化完成"
echo "启动服务: ./scripts/start.sh"
