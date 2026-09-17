#!/bin/bash
# ============================================================
# Knowledge Repository - 部署脚本
# ============================================================
# 用法：
#   ./deploy.sh                    # 自动生成版本号（V{YYYYMMDD}{序号}）
#   ./deploy.sh V1.1.0             # 指定版本号
#   ./deploy.sh --build-only       # 仅构建不重启
#   ./deploy.sh --infra            # 同时部署基础设施
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

IMAGE_NAME="knowledge-repository"
COMPOSE_APP="docker-compose.yml"
COMPOSE_INFRA="docker-compose.infra.yml"

# ============================================================
# 版本号生成
# ============================================================
generate_version() {
    local date_prefix
    date_prefix="V$(date +%Y%m%d)"

    # 查找当天已有的最大序号
    local max_seq=0
    local existing_tags
    existing_tags=$(docker images --format "{{.Tag}}" "${IMAGE_NAME}" 2>/dev/null \
        | grep "^${date_prefix}" || true)

    if [ -n "$existing_tags" ]; then
        while IFS= read -r tag; do
            local seq_part="${tag#${date_prefix}}"
            # 提取数字部分
            local seq_num
            seq_num=$(echo "$seq_part" | grep -oE '[0-9]+' | head -1)
            if [ -n "$seq_num" ] && [ "$seq_num" -gt "$max_seq" ]; then
                max_seq=$seq_num
            fi
        done <<< "$existing_tags"
    fi

    # 递增序号，格式化为两位（强制十进制，避免 08/09 被当作八进制）
    local next_seq
    next_seq=$(printf "%02d" $((10#$max_seq + 1)))

    echo "${date_prefix}${next_seq}"
}

# ============================================================
# 参数解析
# ============================================================
BUILD_ONLY=false
DEPLOY_INFRA=false
CUSTOM_VERSION=""

for arg in "$@"; do
    case $arg in
        --build-only)
            BUILD_ONLY=true
            ;;
        --infra)
            DEPLOY_INFRA=true
            ;;
        V*)
            CUSTOM_VERSION="$arg"
            ;;
        *)
            echo "未知参数: $arg"
            echo "用法: $0 [V版本号] [--build-only] [--infra]"
            exit 1
            ;;
    esac
done

# ============================================================
# 确定版本号
# ============================================================
if [ -n "$CUSTOM_VERSION" ]; then
    APP_VERSION="$CUSTOM_VERSION"
    echo ">>> 使用自定义版本: $APP_VERSION"
else
    APP_VERSION=$(generate_version)
    echo ">>> 自动生成版本: $APP_VERSION"
fi

# ============================================================
# 更新 .env 文件
# ============================================================
sed -i '' "s/^APP_VERSION=.*/APP_VERSION=${APP_VERSION}/" .env
echo ">>> 已更新 .env (APP_VERSION=${APP_VERSION})"

# ============================================================
# 部署基础设施（可选）
# ============================================================
if [ "$DEPLOY_INFRA" = true ]; then
    echo ">>> 部署基础设施..."
    docker compose -f "$COMPOSE_INFRA" up -d
    echo ">>> 基础设施部署完成"
fi

# ============================================================
# 构建镜像
# ============================================================
echo ">>> 构建镜像: ${IMAGE_NAME}:${APP_VERSION}"
docker compose -f "$COMPOSE_APP" build

# ============================================================
# 重启应用（非 build-only 模式）
# ============================================================
if [ "$BUILD_ONLY" = false ]; then
    echo ">>> 重启应用..."
    docker compose -f "$COMPOSE_APP" up -d
    echo ">>> 应用部署完成"

    # 清理旧镜像（保留最新，删除其余）
    OLD_TAGS=$(docker images --format "{{.Tag}}" "${IMAGE_NAME}" \
        | grep -v "${APP_VERSION}" | grep -v "<none>")
    if [ -n "$OLD_TAGS" ]; then
        echo ">>> 清理旧镜像..."
        echo "$OLD_TAGS" | while read -r tag; do
            echo "    删除 ${IMAGE_NAME}:${tag}"
            docker rmi "${IMAGE_NAME}:${tag}" >/dev/null 2>&1 || true
        done
        echo ">>> 旧镜像已清理"
    fi
else
    echo ">>> 仅构建模式，跳过重启"
fi

# ============================================================
# 输出结果
# ============================================================
echo ""
echo "============================================================"
echo " 部署完成"
echo " 镜像: ${IMAGE_NAME}:${APP_VERSION}"
echo " 时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================================"
echo ""
echo " 常用命令:"
echo "   docker logs -f knowledge-app          # 查看日志"
echo "   docker ps --filter name=knowledge-app # 查看状态"
echo "   docker images ${IMAGE_NAME}           # 查看镜像列表"
echo "============================================================"
