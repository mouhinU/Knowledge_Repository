#!/bin/bash
# ============================================================
# Knowledge Repository - GHCR 镜像拉取部署脚本
# ============================================================
# 从 GitHub Container Registry 拉取已由 Actions 构建好的镜像，
# 部署到本地 Docker，跳过本地编译（比 deploy.sh 更快、可复现）。
#
# 版本策略（对应 CI 产出的 tag）：
#   - 本地(默认)  ：Git 提交快照  sha-<7>   （不可变，main 分支最新构建）
#   - 本地(可选)  ：滚动标签      latest     （始终指向最近一次 main 构建）
#   - 生产(预留)  ：发布里程碑    v<semver>  （由打 v*.*.* tag 触发 CI 产出）
#
# 用法：
#   ./scripts/gh-deploy.sh                 # 本地：部署 main 最新 sha-<7>
#   ./scripts/gh-deploy.sh --latest        # 本地：部署 latest 滚动标签
#   ./scripts/gh-deploy.sh --sha 1a2b3c4   # 本地：部署指定 7 位提交号
#   ./scripts/gh-deploy.sh --infra         # 本地：连带拉起基础设施
#   ./scripts/gh-deploy.sh --prod v1.2.3   # 生产(预留)：部署发布标签 v1.2.3
#   ./scripts/gh-deploy.sh --dry-run       # 只打印将执行的命令，不实际部署
#
# 依赖：docker + docker compose（支持 V2 `docker compose`）；
#       首次拉取私有 GHCR 包需已 `docker login ghcr.io`（可用 PAT）。
# ============================================================

set -euo pipefail

# ------------------------------------------------------------
# 定位项目根目录（脚本在 scripts/ 下，根目录为其父级）
# ------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$ROOT_DIR"

# ------------------------------------------------------------
# 默认配置（均可用同名环境变量覆盖）
# ------------------------------------------------------------
REGISTRY="${REGISTRY:-ghcr.io}"
# 从 git remote 推断 owner/repo，兜底为已知值；GHCR 要求全小写
if [ -z "${IMAGE_NAME:-}" ]; then
    REMOTE_URL="$(git remote get-url origin 2>/dev/null || true)"
    OWNER_REPO="$(echo "$REMOTE_URL" | sed -E 's#.*github\.com[:/]##; s#\.git$##')"
    [ -z "$OWNER_REPO" ] && OWNER_REPO="mouhinU/Knowledge_Repository"
    IMAGE_NAME="$(echo "${REGISTRY}/${OWNER_REPO}" | tr '[:upper:]' '[:lower:]')"
fi
APP_SERVICE="knowledge-app"
COMPOSE_APP="docker-compose.yml"
COMPOSE_INFRA="docker-compose.infra.yml"
HEALTH_URL="${HEALTH_URL:-http://localhost:8091/actuator/health}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-240}"   # 冷启动实测 128~160s；tag 切换会 recreate 完整 JVM，取 240 兜底

# ------------------------------------------------------------
# 参数解析
# ------------------------------------------------------------
MODE="local-sha"          # local-sha | local-latest | local-explicit-sha | prod-release
PROD_VERSION=""
DEPLOY_INFRA=false
DRY_RUN=false

for arg in "$@"; do
    case "$arg" in
        --latest)      MODE="local-latest" ;;
        --sha)         MODE="local-explicit-sha" ;;
        --infra)       DEPLOY_INFRA=true ;;
        --prod|--release) MODE="prod-release" ;;
        --dry-run)     DRY_RUN=true ;;
        -h|--help)     sed -n '2,26p' "$0"; exit 0 ;;
        v*|sha-*)      # 位置参数：版本号（v* 视为发布标签，sha-* 视为快照标签）
            case "$arg" in
                v*)     MODE="prod-release"; PROD_VERSION="$arg" ;;
                sha-*)  MODE="local-explicit-sha"; PROD_VERSION="$arg" ;;
            esac ;;
        *)
            if [ "$MODE" = "local-explicit-sha" ] && [ -z "$PROD_VERSION" ]; then
                PROD_VERSION="sha-$arg"          # --sha 1a2b3c4
            elif [ "$MODE" = "prod-release" ] && [ -z "$PROD_VERSION" ]; then
                PROD_VERSION="$arg"              # --prod v1.2.3
            else
                echo "未知参数: $arg" >&2
                echo "用法: $0 [--latest|--sha <7位>|--prod v<semver>|--infra|--dry-run]" >&2
                exit 1
            fi ;;
    esac
done

# ------------------------------------------------------------
# 工具函数
# ------------------------------------------------------------
log() { printf '>>> %s\n' "$*"; }

run() {
    # dry-run 时只打印，不执行
    if [ "$DRY_RUN" = true ]; then
        printf '[dry-run] %s\n' "$*"
    else
        "$@"
    fi
}

# 从 GitHub API 读取 main 最新提交（7 位短号）；失败返回空
latest_main_sha() {
    local repo_api owner_repo
    owner_repo="$(echo "$IMAGE_NAME" | sed -E "s#^${REGISTRY}/##")"
    repo_api="https://api.github.com/repos/${owner_repo}/commits/main"
    curl -fsSL --max-time 15 "$repo_api" 2>/dev/null \
        | grep -m1 -oE '"sha":[[:space:]]*"[0-9a-f]{40}"' \
        | grep -oE '[0-9a-f]{40}' | cut -c1-7 || true
}

# ------------------------------------------------------------
# 解析目标版本
# ------------------------------------------------------------
case "$MODE" in
    local-sha)
        log "解析 main 分支最新提交号（GitHub API）..."
        SHORT="$(latest_main_sha)"
        if [ -z "$SHORT" ]; then
            log "GitHub API 获取失败，回退到本地 origin/main"
            SHORT="$(git rev-parse --short=7 origin/main 2>/dev/null \
                     || git rev-parse --short=7 HEAD)"
        fi
        APP_VERSION="sha-${SHORT}"
        ;;
    local-latest)
        APP_VERSION="latest"
        ;;
    local-explicit-sha)
        APP_VERSION="$PROD_VERSION"
        ;;
    prod-release)
        # ============================================
        # 生产部署通道 —— 预留在此，暂不常态化启用
        # 触发方式：在仓库打 v*.*.* 标签 → CI 产出 ${IMAGE_NAME}:v*.*.*
        # 本脚本仅负责「拉取 + 起容器」，不含灰度/回滚/审批编排。
        # TODO(prod): 接入审批门、健康门禁与自动回滚后方可用于生产。
        # ============================================
        [ -z "$PROD_VERSION" ] && { echo "生产模式需指定发布标签，如 --prod v1.2.3" >&2; exit 1; }
        APP_VERSION="$PROD_VERSION"
        log "⚠ 生产部署通道（预留）：目标发布标签 ${APP_VERSION}"
        log "  该路径尚未接入审批/回滚编排，仅完成拉取与重启，请谨慎使用。"
        ;;
esac

log "目标镜像: ${IMAGE_NAME}:${APP_VERSION}"

# ------------------------------------------------------------
# （可选）GHCR 登录：私有包匿名拉取会 401，需带 token
# ------------------------------------------------------------
ensure_ghcr_login() {
    # 已登录则跳过
    if [ -f "$HOME/.docker/config.json" ] && grep -q "${REGISTRY}" "$HOME/.docker/config.json" 2>/dev/null; then
        return 0
    fi
    local user token
    user="${GHCR_USER:-mouhinU}"
    token="${GHCR_TOKEN:-}"
    if [ -z "$token" ]; then
        # 尝试从 git 凭据助手取（GitHub 密码域，作为 PAT 兜底）
        token="$(printf 'protocol=https\nhost=github.com\n\n' \
                 | git credential fill 2>/dev/null | sed -n 's/^password=//p')" || true
    fi
    if [ -n "$token" ]; then
        log "使用 PAT 登录 ${REGISTRY} ..."
        printf '%s' "$token" | run docker login "$REGISTRY" -u "$user" --password-stdin
    else
        log "未检测到 ${REGISTRY} 登录凭据；若镜像包为私有，请先手动 'docker login ${REGISTRY}'。"
    fi
}
ensure_ghcr_login

# ------------------------------------------------------------
# 基础设施（可选）
# ------------------------------------------------------------
if [ "$DEPLOY_INFRA" = true ]; then
    log "拉起基础设施 (MySQL / Milvus / etcd / MinIO)..."
    run docker compose -f "$COMPOSE_INFRA" up -d
fi

# ------------------------------------------------------------
# 拉取镜像（--no-build：直接用 Actions 产物，不在本地编译）
# ------------------------------------------------------------
export IMAGE_NAME APP_VERSION
log "拉取镜像..."
if ! run docker compose -f "$COMPOSE_APP" pull "$APP_SERVICE"; then
    # sha-<7> 快照可能因 CI paths 过滤（如纯文档提交）而未产出，回退到 latest
    case "$APP_VERSION" in
        sha-*)
            log "⚠ ${APP_VERSION} 拉取失败（该提交可能未触发镜像构建），回退到 latest ..."
            APP_VERSION="latest"
            export APP_VERSION
            run docker compose -f "$COMPOSE_APP" pull "$APP_SERVICE"
            ;;
        *)
            echo "镜像拉取失败：${IMAGE_NAME}:${APP_VERSION}" >&2
            exit 1
            ;;
    esac
fi

# ------------------------------------------------------------
# 启动/更新应用容器
# ------------------------------------------------------------
log "启动应用容器..."
run docker compose -f "$COMPOSE_APP" up -d --no-build "$APP_SERVICE"

# ------------------------------------------------------------
# 健康检查
# ------------------------------------------------------------
if [ "$DRY_RUN" = true ]; then
    echo ""
    echo "[dry-run] 跳过健康检查。以上为将要执行的命令。"
    exit 0
fi

log "等待服务健康（最多 ${HEALTH_TIMEOUT}s）..."
deadline=$(( $(date +%s) + HEALTH_TIMEOUT ))
ok=false
while [ "$(date +%s)" -lt "$deadline" ]; do
    body="$(curl -fsS --max-time 5 "$HEALTH_URL" 2>/dev/null || true)"
    if echo "$body" | grep -q '"status":"UP"'; then
        ok=true
        break
    fi
    sleep 5
done

# ------------------------------------------------------------
# 结果输出
# ------------------------------------------------------------
echo ""
echo "============================================================"
if [ "$ok" = true ]; then
    echo " ✅ 部署成功"
else
    echo " ⚠ 已下命令但健康检查未通过，请查日志：docker logs ${APP_SERVICE}"
fi
echo " 镜像 : ${IMAGE_NAME}:${APP_VERSION}"
echo " 服务 : http://localhost:8091/admin.html"
echo " 时间 : $(date '+%Y-%m-%d %H:%M:%S')"
echo "------------------------------------------------------------"
echo " 常用命令:"
echo "   docker logs -f ${APP_SERVICE}            # 查看日志"
echo "   $0 --dry-run                          # 预览部署动作"
echo "============================================================"
[ "$ok" = true ] || exit 1
