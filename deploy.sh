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
# 跨平台原地替换：GNU sed `-i` 不带参数，BSD/macOS sed `-i` 需一个（可为空）备份后缀。
# `sed --version` 在 GNU 下成功、BSD 下失败，据此判别。
if sed --version >/dev/null 2>&1; then
    sed -i "s/^APP_VERSION=.*/APP_VERSION=${APP_VERSION}/" .env
else
    sed -i '' "s/^APP_VERSION=.*/APP_VERSION=${APP_VERSION}/" .env
fi
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
if ! docker compose -f "$COMPOSE_APP" build; then
    echo ">>> docker compose build 失败，尝试 Dockerfile.reuse 降级构建（复用本地基镜像，绕开 Docker Hub）"
    BASE_IMAGE="${IMAGE_NAME}:V2026092307-flat"
    if ! docker image inspect "${BASE_IMAGE}" >/dev/null 2>&1; then
        echo "!!! 未找到降级所需基镜像 ${BASE_IMAGE}，请先手动构建或恢复"
        exit 1
    fi
    echo ">>> 使用基镜像: ${BASE_IMAGE}"
    docker build -f docker/Dockerfile.reuse \
        --build-arg APP_VERSION="${APP_VERSION}" \
        -t "${IMAGE_NAME}:${APP_VERSION}" .
fi

# ============================================================
# 扁平化镜像（export → import 合并为单层，消除层叠体积膨胀）
# ============================================================
# Dockerfile.reuse 在基镜像上叠加新 jar 层，旧 jar 仍计入虚拟大小（每层 ~116MB × 2 ≈ 232MB 膨胀）。
# 通过 export/import 将所有层合并为单一文件系统，恢复到 V2026092307-flat 同等体积。
echo ">>> 扁平化镜像（合并为单层）..."
PRE_FLATTEN_TAG="${IMAGE_NAME}:${APP_VERSION}-pre"
FLAT_TAG="${IMAGE_NAME}:${APP_VERSION}-flat"
docker tag "${IMAGE_NAME}:${APP_VERSION}" "${PRE_FLATTEN_TAG}"
CID=$(docker create "${PRE_FLATTEN_TAG}" true)
docker export "${CID}" | docker import \
    --change 'ENV PATH=/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin' \
    --change 'ENV JAVA_HOME=/opt/java/openjdk' \
    --change 'ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"' \
    --change "ENV APP_VERSION=${APP_VERSION}" \
    --change 'WORKDIR /app' \
    --change 'USER appuser' \
    --change 'EXPOSE 8091' \
    --change 'ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]' \
    - "${FLAT_TAG}"
docker rm "${CID}" >/dev/null 2>&1
docker rmi "${PRE_FLATTEN_TAG}" >/dev/null 2>&1 || true
# 将 -flat 镜像重新 tag 为正式版本号，供 docker compose 使用
docker tag "${FLAT_TAG}" "${IMAGE_NAME}:${APP_VERSION}"
FLAT_SIZE=$(docker images --format "{{.Size}}" "${FLAT_TAG}" | head -1)
echo ">>> 扁平化完成: ${FLAT_TAG} (${FLAT_SIZE})"

# ============================================================
# 重启应用（非 build-only 模式）
# ============================================================
if [ "$BUILD_ONLY" = false ]; then
    echo ">>> 重启应用..."
    docker compose -f "$COMPOSE_APP" up -d
    echo ">>> 应用部署完成"

    # 清理旧镜像（保留：当前 APP_VERSION 相关 tag + 基镜像 V2026092307-flat）
    # 基镜像是 Dockerfile.reuse 降级构建的必需底本，删除后下次无法再降级
    PROTECTED_BASE="${IMAGE_NAME}:V2026092307-flat"
    OLD_TAGS=$(docker images --format "{{.Tag}}" "${IMAGE_NAME}" \
        | grep -v "${APP_VERSION}" \
        | grep -v "^V2026092307-flat$" \
        | grep -v "<none>")
    if [ -n "$OLD_TAGS" ]; then
        echo ">>> 清理旧镜像（保留基镜像 V2026092307-flat）..."
        echo "$OLD_TAGS" | while read -r tag; do
            echo "    删除 ${IMAGE_NAME}:${tag}"
            docker rmi "${IMAGE_NAME}:${tag}" >/dev/null 2>&1 || true
        done
        echo ">>> 旧镜像已清理"
    fi
    # 保留 V2026092307-flat 存在性检查（若被误删，提示恢复方法）
    if ! docker image inspect "${PROTECTED_BASE}" >/dev/null 2>&1; then
        echo "!!! 警告：基镜像 ${PROTECTED_BASE} 不存在，下次 Docker Hub 不可达时将无法降级构建"
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
