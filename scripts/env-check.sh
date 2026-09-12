#!/bin/bash
# Knowledge Repository 环境检查脚本
# 检查运行所需的依赖环境

echo "=== Knowledge Repository 环境检查 ==="
echo ""

PASS=0
WARN=0
FAIL=0

check_cmd() {
    if command -v "$1" >/dev/null 2>&1; then
        echo "[OK]   $1  $(command -v "$1")"
        ((PASS++))
    else
        echo "[FAIL] $1  未安装"
        ((FAIL++))
    fi
}

check_port() {
    if lsof -i :"$1" -t >/dev/null 2>&1; then
        echo "[OK]   端口 $1  已占用"
        ((PASS++))
    else
        echo "[WARN] 端口 $1  空闲"
        ((WARN++))
    fi
}

# 1. Java
echo "--- 运行时 ---"
if command -v java >/dev/null 2>&1; then
    JAVA_VER=$(java -version 2>&1 | head -1 | cut -d'"' -f2)
    echo "[OK]   Java  $JAVA_VER"
    ((PASS++))
else
    echo "[FAIL] Java  未安装 (需要 JDK 21+)"
    ((FAIL++))
fi

# 2. Maven (项目自带 mvnw)
if [ -f "./mvnw" ]; then
    echo "[OK]   Maven 项目自带 mvnw"
    ((PASS++))
else
    check_cmd mvn
fi

# 3. Docker
echo ""
echo "--- Docker ---"
if command -v docker >/dev/null 2>&1; then
    DOCKER_VER=$(docker --version | cut -d' ' -f3 | tr -d ',')
    echo "[OK]   Docker  $DOCKER_VER"
    ((PASS++))
else
    echo "[WARN] Docker  未安装 (Milvus 需要 Docker)"
    ((WARN++))
fi

if command -v docker-compose >/dev/null 2>&1 || docker compose version >/dev/null 2>&1; then
    echo "[OK]   docker-compose  已安装"
    ((PASS++))
else
    echo "[WARN] docker-compose  未安装"
    ((WARN++))
fi

# 4. 端口检查
echo ""
echo "--- 端口 ---"
check_port 8091   # 应用
check_port 19530  # Milvus
check_port 11434  # Ollama

# 5. 目录检查
echo ""
echo "--- 目录 ---"
if [ -d "./data" ]; then
    echo "[OK]   data/  存在"
    ((PASS++))
else
    echo "[WARN] data/  不存在(首次启动时创建)"
    ((WARN++))
fi

if [ -d "./docker" ]; then
    echo "[OK]   docker/  存在"
    ((PASS++))
else
    echo "[WARN] docker/  不存在"
    ((WARN++))
fi

# 汇总
echo ""
echo "=========================================="
echo "  通过: $PASS   警告: $WARN   失败: $FAIL"
echo "=========================================="

if [ $FAIL -gt 0 ]; then
    echo "存在必须项未满足，请先安装缺失依赖"
    exit 1
elif [ $WARN -gt 0 ]; then
    echo "部分可选项未就绪，服务可能无法正常工作"
    exit 0
else
    echo "环境检查全部通过"
    exit 0
fi
