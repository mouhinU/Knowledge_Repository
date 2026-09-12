#!/bin/bash
# Knowledge Repository 升级脚本
# 用法: ./scripts/upgrade.sh

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 升级工具"
echo "=========================================="
echo ""

# 1. 检查当前版本
echo ">>> 当前版本"
./scripts/version.sh | grep "应用版本"
echo ""

# 2. 备份数据
echo ">>> 备份当前数据..."
read -p "是否先备份数据? (推荐) (Y/n): " BACKUP
if [ "$BACKUP" != "n" ] && [ "$BACKUP" != "N" ]; then
    ./scripts/backup.sh
fi
echo ""

# 3. 停止服务
echo ">>> 停止服务..."
./scripts/stop.sh
echo ""

# 4. 拉取最新代码
echo ">>> 拉取最新代码..."
if [ -d ".git" ]; then
    git pull
    if [ $? -ne 0 ]; then
        echo "代码拉取失败，请检查网络连接"
        exit 1
    fi
else
    echo "非 Git 仓库，跳过代码更新"
fi
echo ""

# 5. 编译项目
echo ">>> 编译项目..."
./mvnw clean compile -DskipTests -q
if [ $? -ne 0 ]; then
    echo "编译失败，请检查代码"
    exit 1
fi
echo "    编译成功"
echo ""

# 6. 数据库迁移
echo ">>> 检查数据库迁移..."
echo "    数据库迁移将在启动时自动执行"
echo ""

# 7. 启动服务
echo ">>> 启动服务..."
read -p "是否立即启动服务? (Y/n): " START
if [ "$START" != "n" ] && [ "$START" != "N" ]; then
    ./scripts/start.sh
fi

echo ""
echo "=========================================="
echo "  升级完成"
echo "=========================================="
echo ""
echo "新版本信息:"
./scripts/version.sh | grep -E "应用版本|构建时间"
echo ""
echo "请检查:"
echo "  1. 服务状态:  ./scripts/status.sh"
echo "  2. 健康检查:  ./scripts/health-check.sh"
echo "  3. 功能测试:  ./scripts/api-test.sh"
