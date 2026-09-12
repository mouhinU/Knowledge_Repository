#!/bin/bash
# Knowledge Repository 版本信息脚本

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository"
echo "  RAG 知识库管理系统"
echo "=========================================="
echo ""

# 版本号
VERSION="1.0.0"
BUILD_TIME=$(date "+%Y-%m-%d %H:%M:%S")

echo "版本信息"
echo "--------"
echo "  应用版本: $VERSION"
echo "  构建时间: $BUILD_TIME"
echo ""

# 技术栈
echo "技术栈"
echo "--------"
echo "  Java:        $(java -version 2>&1 | head -1 | cut -d'" -f2)"
echo "  Spring Boot: 3.4.x"
echo "  Milvus:      2.5.4"
echo "  Embedding:   bge-m3 (1024维)"
echo ""

# 项目路径
echo "项目路径"
echo "--------"
echo "  项目根目录: $(pwd)"
echo "  数据目录:   $(pwd)/data"
echo "  日志文件:   /tmp/kb-server.log"
echo ""

# 端口
echo "服务端口"
echo "--------"
echo "  应用端口:   8091"
echo "  Milvus:     19530"
echo "  MinIO:      9001"
echo "  Ollama:     11434"
echo ""

# 访问地址
echo "访问地址"
echo "--------"
echo "  管理控制台: http://localhost:8091/admin.html"
echo "  H2 Console: http://localhost:8091/h2-console"
echo ""

# Git 信息
if [ -d ".git" ]; then
    echo "Git 信息"
    echo "--------"
    echo "  分支: $(git branch --show-current 2>/dev/null)"
    echo "  提交: $(git rev-parse --short HEAD 2>/dev/null)"
    echo "  时间: $(git log -1 --format=%cd --date=short 2>/dev/null)"
    echo ""
fi

# 服务状态
echo "服务状态"
echo "--------"
PORT=8091
PID=$(lsof -i :$PORT -t 2>/dev/null)
if [ -n "$PID" ]; then
    echo "  应用服务: 运行中 (PID: $PID)"
else
    echo "  应用服务: 未运行"
fi

if curl -sf "http://localhost:9091/healthz" >/dev/null 2>&1; then
    echo "  Milvus:   正常"
else
    echo "  Milvus:   未就绪"
fi

if curl -sf "http://localhost:11434/api/tags" >/dev/null 2>&1; then
    echo "  Ollama:   正常"
else
    echo "  Ollama:   未运行"
fi

echo ""
echo "=========================================="
echo "  使用 ./scripts/help.sh 查看所有脚本"
echo "=========================================="
