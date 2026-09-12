#!/bin/bash
# Knowledge Repository 卸载脚本
# 用法: ./scripts/uninstall.sh

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 卸载工具"
echo "=========================================="
echo ""
echo "警告: 此操作将删除所有数据，包括:"
echo "  - 数据库文件"
echo "  - 上传的文档"
echo "  - 备份文件"
echo "  - 所有配置"
echo ""

read -p "确认要完全卸载吗? 此操作不可恢复! (输入 YES 确认): " CONFIRM

if [ "$CONFIRM" != "YES" ]; then
    echo "已取消"
    exit 0
fi

# 1. 停止服务
echo ""
echo ">>> 停止服务..."
./scripts/stop.sh 2>/dev/null

# 2. 停止 Docker 容器
echo ""
echo ">>> 停止 Docker 容器..."
read -p "是否同时删除 Milvus 数据? (y/N): " REMOVE_MILVUS
if [ "$REMOVE_MILVUS" = "y" ] || [ "$REMOVE_MILVUS" = "Y" ]; then
    docker-compose down -v 2>/dev/null
    echo "    Docker 容器和数据已删除"
else
    docker-compose down 2>/dev/null
    echo "    Docker 容器已停止，数据保留"
fi

# 3. 删除数据目录
echo ""
echo ">>> 删除数据..."
if [ -d "./data" ]; then
    rm -rf ./data
    echo "    data/ 目录已删除"
fi

# 4. 删除日志
echo ""
echo ">>> 清理日志..."
if [ -f "/tmp/kb-server.log" ]; then
    rm -f /tmp/kb-server.log
    echo "    日志文件已删除"
fi

# 5. 删除编译产物
echo ""
echo ">>> 清理编译产物..."
find . -type d -name "target" -exec rm -rf {} + 2>/dev/null
echo "    target 目录已清理"

# 6. 清理 shell 配置（可选）
echo ""
read -p "是否清理 shell 配置中的别名? (y/N): " CLEAN_SHELL
if [ "$CLEAN_SHELL" = "y" ] || [ "$CLEAN_SHELL" = "Y" ]; then
    for RC in "$HOME/.bashrc" "$HOME/.zshrc"; do
        if [ -f "$RC" ]; then
            sed -i.bak '/Knowledge Repository/d' "$RC" 2>/dev/null
            sed -i.bak '/scripts\/alias.sh/d' "$RC" 2>/dev/null
            sed -i.bak '/scripts\/completion.sh/d' "$RC" 2>/dev/null
            echo "    已清理 $RC"
        fi
    done
fi

echo ""
echo "=========================================="
echo "  卸载完成"
echo "=========================================="
echo ""
echo "Knowledge Repository 已完全卸载"
echo "项目目录可以手动删除: $(pwd)"
