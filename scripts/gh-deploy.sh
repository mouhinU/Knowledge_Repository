#!/bin/bash
# ============================================================
# Knowledge Repository - GHCR 镜像拉取部署脚本
# ============================================================
# 从 GitHub Container Registry 拉取已由 Actions 构建好的镜像，
# 部署到本地 Docker，跳过本地编译（比 deploy.sh 更快、可复现）。
#
# 拉取通道：crane（go-containerregistry）。之所以不再走
# `docker pull` / `docker compose pull`，是因为 Docker Desktop 内置
# 代理 `http.docker.internal:3128` 常年在 macOS 上把 ghcr.io 直接
# EOF 掉；crane 是纯用户态 CLI，读取当前 shell 的代理与凭据，
# 用同一条 HTTPS 通道即可完成 manifest/blob 拉取并 `docker load`。
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
# 依赖：
#   - docker + docker compose V2（load 与 up 仍走本机 Docker daemon）
#   - curl + git（PAT 从 git credential helper 读取）
#   - crane：本机若无，脚本会自动从 GitHub Releases 下载至 ${CRANE_HOME}
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
PLATFORM="${PLATFORM:-linux/amd64}"       # crane 拉取目标平台
CRANE_HOME="${CRANE_HOME:-${HOME}/.local/bin}"
CRANE_BIN="${CRANE_BIN:-${CRANE_HOME}/crane}"
CRANE_VERSION="${CRANE_VERSION:-v0.22.1}" # go-containerregistry release tag
CRANE_TMP_TAR="${CRANE_TMP_TAR:-}"        # 空则用 mktemp 生成

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
        -h|--help)     sed -n '2,32p' "$0"; exit 0 ;;
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
# crane 就位：PATH 命中直接用；否则下载到 ${CRANE_HOME}
# ------------------------------------------------------------
ensure_crane() {
    if command -v crane >/dev/null 2>&1; then
        CRANE_BIN="$(command -v crane)"
        log "使用系统 crane: ${CRANE_BIN}"
        return 0
    fi
    if [ -x "$CRANE_BIN" ]; then
        log "使用缓存 crane: ${CRANE_BIN} ($(${CRANE_BIN} version 2>/dev/null || echo unknown))"
        return 0
    fi
    mkdir -p "$CRANE_HOME"
    local os arch asset url tmp
    case "$(uname -s)" in
        Darwin) os="Darwin" ;;
        Linux)  os="Linux" ;;
        *) echo "不支持的 OS: $(uname -s)" >&2; exit 1 ;;
    esac
    case "$(uname -m)" in
        x86_64|amd64) arch="x86_64" ;;
        arm64|aarch64) arch="arm64" ;;
        *) echo "不支持的架构: $(uname -m)" >&2; exit 1 ;;
    esac
    asset="go-containerregistry_${os}_${arch}.tar.gz"
    url="https://github.com/google/go-containerregistry/releases/download/${CRANE_VERSION}/${asset}"
    tmp="$(mktemp -d)"
    log "首次运行：下载 crane ${CRANE_VERSION} (${os}/${arch})"
    if [ "$DRY_RUN" = true ]; then
        printf '[dry-run] curl -SL %s -o %s/%s\n' "$url" "$tmp" "$asset"
        printf '[dry-run] tar xzf %s/%s -C %s crane\n' "$tmp" "$asset" "$CRANE_HOME"
        return 0
    fi
    curl -fSL --max-time 60 "$url" -o "${tmp}/${asset}"
    tar -xzf "${tmp}/${asset}" -C "$tmp" crane
    mv -f "${tmp}/crane" "$CRANE_BIN"
    chmod +x "$CRANE_BIN"
    rm -rf "$tmp"
    log "crane 已安装到 ${CRANE_BIN}"
}

# ------------------------------------------------------------
# GHCR 登录：把 PAT 交给 crane，让它写 ~/.docker/config.json
# 说明：crane 与 docker 共用同一份 config.json，若里面留有 ghcr.io
# 的空 auths 条目会让后续 token 交换 DENIED，须先删除。
# ------------------------------------------------------------
ghcr_auth_login() {
    local user token
    user="${GHCR_USER:-mouhinU}"
    token="${GHCR_TOKEN:-}"
    if [ -z "$token" ]; then
        token="$(printf 'protocol=https\nhost=github.com\n\n' \
                 | git credential fill 2>/dev/null | sed -n 's/^password=//p')" || true
    fi
    if [ -z "$token" ]; then
        log "⚠ 未取到 GHCR PAT，尝试匿名拉取（公开包可用；私有包会 401）"
        return 0
    fi
    if [ "$DRY_RUN" = true ]; then
        printf '[dry-run] clear stale auths.ghcr.io; echo PAT | %s auth login %s -u %s --password-stdin\n' \
            "$CRANE_BIN" "$REGISTRY" "$user"
        return 0
    fi
    # 清理 stale 空条目（crane 会写正确格式回来）
    if [ -f "$HOME/.docker/config.json" ]; then
        python3 - "$HOME/.docker/config.json" <<'PY' || true
import json, sys
p = sys.argv[1]
try:
    d = json.load(open(p))
except Exception:
    sys.exit(0)
auths = d.get('auths') or {}
if 'ghcr.io' in auths and not auths['ghcr.io']:
    del auths['ghcr.io']
if auths == {}:
    d.pop('auths', None)
json.dump(d, open(p, 'w'), indent='\t')
PY
    fi
    log "使用 PAT 登录 ${REGISTRY} ..."
    printf '%s' "$token" | "$CRANE_BIN" auth login "$REGISTRY" -u "$user" --password-stdin >/dev/null
}

# ------------------------------------------------------------
# 代理隔离：crane 只信任 shell env，Docker Desktop 系统代理不作用
# 于本机 shell，因此这里显式清空代理并放行 ghcr.io 直连。
# ------------------------------------------------------------
no_proxy_env() {
    env HTTP_PROXY= HTTPS_PROXY= http_proxy= https_proxy= \
        NO_PROXY='*' no_proxy='*' "$@"
}

# ------------------------------------------------------------
# 通过 crane 拉取单个 tag 到临时 tar 并 docker load。
# 网络层重试：GHCR edge 偶发 HTTP/2 PROTOCOL_ERROR / unexpected EOF，
# 单纯重试同一命令即可恢复；固定退避 CRANE_BACKOFF_SEC 秒，最多 CRANE_RETRIES 次。
# 返回 0=成功；非 0=失败（tag 不存在 / 网络中断 / 权限拒绝）。
# ------------------------------------------------------------
CRANE_RETRIES="${CRANE_RETRIES:-3}"
CRANE_BACKOFF_SEC="${CRANE_BACKOFF_SEC:-5}"

pull_tag_via_crane() {
    local image_ref="$1"
    local tar="$2"
    local attempt=1 rc=0
    while [ "$attempt" -le "$CRANE_RETRIES" ]; do
        log "crane pull ${image_ref} (${PLATFORM}) → ${tar}  [第 ${attempt}/${CRANE_RETRIES} 次]"
        rm -f "$tar"
        if no_proxy_env "$CRANE_BIN" pull --platform="$PLATFORM" "$image_ref" "$tar"; then
            rc=0
            break
        else
            rc=$?
        fi
        if [ "$attempt" -lt "$CRANE_RETRIES" ]; then
            log "⚠ crane pull 失败 (rc=${rc})，${CRANE_BACKOFF_SEC}s 后重试..."
            sleep "$CRANE_BACKOFF_SEC"
        fi
        attempt=$((attempt + 1))
    done
    if [ "$rc" -ne 0 ]; then
        return "$rc"
    fi
    log "docker load -i ${tar}"
    if ! docker load -i "$tar"; then
        return 1
    fi
    return 0
}

# ------------------------------------------------------------
# （可选）基础设施
# ------------------------------------------------------------
if [ "$DEPLOY_INFRA" = true ]; then
    log "拉起基础设施 (MySQL / Milvus / etcd / MinIO)..."
    run docker compose -f "$COMPOSE_INFRA" up -d
fi

# ------------------------------------------------------------
# 主流程：crane 就位 → 登录 → 拉取（sha-<7> 失败自动降级 latest）→ load → up
# ------------------------------------------------------------
ensure_crane
ghcr_auth_login

if [ "$DRY_RUN" = true ]; then
    printf '[dry-run] %s pull --platform=%s %s:%s <tmp>.tar\n' \
        "$CRANE_BIN" "$PLATFORM" "$IMAGE_NAME" "$APP_VERSION"
    printf '[dry-run] docker load -i <tmp>.tar\n'
else
    TAR="${CRANE_TMP_TAR:-$(mktemp "${TMPDIR:-/tmp}/kb-image.XXXXXX")}"
    trap 'rm -f "${TAR:-}"' EXIT
    if ! pull_tag_via_crane "${IMAGE_NAME}:${APP_VERSION}" "$TAR"; then
        case "$APP_VERSION" in
            sha-*)
                log "⚠ ${APP_VERSION} 拉取失败（该提交可能未触发镜像构建），回退到 latest ..."
                APP_VERSION="latest"
                export APP_VERSION
                pull_tag_via_crane "${IMAGE_NAME}:${APP_VERSION}" "$TAR" \
                    || { echo "镜像拉取失败：${IMAGE_NAME}:${APP_VERSION}" >&2; exit 1; }
                ;;
            *)
                echo "镜像拉取失败：${IMAGE_NAME}:${APP_VERSION}" >&2
                exit 1
                ;;
        esac
    fi
    rm -f "$TAR"
    trap - EXIT
fi

# ------------------------------------------------------------
# 启动/更新应用容器
# 说明：docker-compose.yml 里 image 写作 ${IMAGE_NAME}:${APP_VERSION}，
# .env 常残留本地 build 的 V{yyyymmdd}{seq} 标签——这里用 export 强制覆盖，
# 保证 compose 用刚 load 进来的 GHCR 镜像，并按 tag 变化触发 recreate。
# ------------------------------------------------------------
export IMAGE_NAME APP_VERSION
log "启动应用容器 (IMAGE_NAME=${IMAGE_NAME} APP_VERSION=${APP_VERSION})..."
run docker compose -f "$COMPOSE_APP" up -d --no-build --force-recreate "$APP_SERVICE"

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
echo " 拉取 : crane (${CRANE_VERSION}) → docker load，未走 docker pull"
echo " 服务 : http://localhost:8091/admin.html"
echo "============================================================"
[ "$ok" = true ] || exit 1
