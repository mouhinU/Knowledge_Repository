# CI-CD 部署方案（GitHub Actions → 本地 Docker）

> 目标：把 GitHub Actions 上构建的镜像落到本地 Docker 中运行、复用现有 `docker-compose.yml` 与 `scripts/deploy.sh`。
>
> 适用场景：本地开发机自测、演示环境、离线内网部署。
>
> 关联文件：[Dockerfile](../Dockerfile)、[docker/Dockerfile.prebuilt](../docker/Dockerfile.prebuilt)、[docker-compose.yml](../docker-compose.yml)、[docker-compose.infra.yml](../docker-compose.infra.yml)、[.github/workflows/docker-image.yml](../.github/workflows/docker-image.yml)、[.github/workflows/ci.yml](../.github/workflows/ci.yml)、[scripts/deploy.sh](../scripts/deploy.sh)、[scripts/docker-build.sh](../scripts/docker-build.sh)。

---

## 一、现状盘点（截至 2026-09-22）

**Actions 侧**

- **`.github/workflows/docker-image.yml` 已落地并跑通**：main push / tag push 会自动 build 并推送到 `ghcr.io/mouhinu/knowledge_repository`。PR 只做 dry-run build，不推。
- **顶层 `permissions: { contents: read, packages: write }`** —— 已不再依赖仓库 Settings → Actions → General → "Read and write"。管理员即使把默认改成 Read-only 也能推。
- **`concurrency + paths` 过滤**：同分支/PR 串行，新 push 会作废进行中的旧 run；纯改 `README`/workflow 定义/文档不会触发一次 GHCR 推送。tag push (`v*`) 忽略 paths 过滤，release tag 必然出镜像。
- **镜像名 lowercase 归一**：`${{ github.repository }}` 会保留大小写，GHCR 强制小写；`Compute image tag` 步骤里已用 `tr '[:upper:]' '[:lower:]'` 归一。
- **runner 已钉 `ubuntu-24.04`**（避开 2026-10-19 `ubuntu-latest` 静默迁 26）。

**本地侧**

- 多阶段 Dockerfile：`maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre`。
- **`docker/Dockerfile.prebuilt`**：单阶段兜底，直接 COPY 主机 `mvn package` 产物，绕开容器内 `mvn dependency:go-offline` 在建网慢/无镜像源时挂 16min+ 的坑。
- `docker-compose.yml` 单服务 `knowledge-app`，端口 8091，依赖宿主机 MySQL (3307) / Milvus (19530) / Ollama (11434)；`IMAGE_NAME` 与 `APP_VERSION` 由 `.env` 或环境变量注入。
- `docker-compose.infra.yml` 拉起 MySQL / Milvus / etcd / MinIO。
- `scripts/deploy.sh` 本地起环境；`scripts/docker-build.sh` 主机侧 mvn package + 本地 docker build 的兜底通道。

**结论**：**主用方案 A（GHCR）** 已完全打通；B/C 作为特殊场景兜底保留。

---

## 二、方案对比与选型建议

| 方案 | 传输媒介 | 本地一步命令 | 依赖外部服务 | 离线可用 | 推荐度 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| **A. GHCR 镜像** | OCI 镜像层 | `crane pull → docker load → compose up --no-build`（脚本封装，见下） | GitHub Container Registry（免费） | ❌ | **★★★★★** | **已落地** |
| B. Actions artifact 传 jar → 本地 build | jar 文件（~80 MB） | `curl API 下载 artifact && docker build` | Actions Artifacts 存储（90 天） | ⚠️ 需本地有 Dockerfile | ★★★☆☆ | 未启用（按需追加 job） |
| C. Actions 内 `docker save` → 拉 tar 包 | 镜像 tar（~150 MB gz） | `gunzip \| docker load && up` | Actions Artifacts 存储 | ✅ 完全离线可用 | ★★☆☆☆（内网专用） | 未启用（按需追加 job） |

> 方案 A 不再走 `docker pull` / `docker compose pull`：macOS 的 Docker Desktop 会把 daemon 的 HTTP(S) 强制经 `http.docker.internal:3128` 代理，`ghcr.io` / `registry-1.docker.io` 常年 EOF；`scripts/gh-deploy.sh` 改用 `crane` 拉 tar 后 `docker load`，绕开 daemon 网络栈。Linux / Windows 若 daemon 直连 GHCR 通畅，也可退回 `docker compose pull`，但脚本默认走 crane 保证跨平台一致。

**选型规则**

- 日常开发 / 团队协作 → **方案 A**，`./scripts/gh-deploy.sh` 一行搞定（crane 拉 → docker load → compose recreate）。
- 网络屏蔽 GHCR / 只想拿 jar → **方案 B**。
- 内网离线环境、需要 U 盘搬环境 → **方案 C**。

---

## 三、方案 A（推荐）：推镜像到 GHCR + 本地 pull

### 3.1 前置准备（只做一遍）

**① 创建 GHCR 拉取用的 PAT**（镜像包默认为 private，匿名 `docker pull` 会 401）

1. GitHub → 头像 → **Settings** → **Developer settings** → **Personal access tokens** → **Fine-grained tokens** → Generate new token
2. 配置：
   - **Repository access**：Only select repositories → 勾 `mouhinU/Knowledge_Repository`
   - **Permissions → Container registry → Read**（等价 classic PAT 的 `read:packages` scope）
   - 有效期按需，建议 ≤ 180 天
3. 复制 token（形如 `github_pat_...`）到剪贴板。

> 备选：把 GHCR 包改成 Public，`docker pull` 免登录。设置路径：`ghcr.io/packages/<name> → Package settings → Change visibility → Public`。团队协作时更省事，但代价是任何人能拉镜像（不含代码）。

**② 本机 Docker 登录 GHCR（凭据持久化到 OS keychain）**

```bash
echo "<你的GHCR_PAT>" | docker login ghcr.io -u mouhinU --password-stdin
# 期望输出：Login Succeeded
```

**③ 项目根 `.env` 已就绪**（本仓库 `.env.example` 有模板，最少填三项）：

```bash
MYSQL_PASSWORD=<你的MySQL口令>
LLM_CHAT_API_KEY=<DeepSeek API Key>
KNOWLEDGE_ADMIN_JWT_SECRET=<随机 32 字节 base64>
```

**④ 仓库级配置（管理员一次性）**：Settings → Actions → General → Workflow permissions 保留默认 **Read repository contents and packages permissions** 即可；工作流顶层的 `permissions` 块会自覆盖，不再需要"Read and write"。

### 3.2 Actions 端 workflow 关键点

实际文件见 [`.github/workflows/docker-image.yml`](../.github/workflows/docker-image.yml)。要点摘录：

```yaml
name: Build & Push Docker Image

on:
  push:
    branches: [main]
    tags: ["v*"]
    # 只在真正影响镜像内容的文件变化时才 build+push
    # tag push (v*) 与 paths 共存时 paths 会被忽略，release 仍必然出镜像
    paths:
      - "**/*.java"
      - "**/pom.xml"
      - "pom.xml"
      - "**/src/main/resources/**"
      - "Dockerfile"
      - "Dockerfile.*"
      - "docker/**"
      - ".mvn/**"
      - "mvnw"
      - "mvnw.cmd"
  pull_request:
    branches: [main]
    paths:
      - "**/*.java"
      # ... 同上
  workflow_dispatch: {}          # 手动触发口子，不受 paths 过滤

permissions:                     # workflow 顶层，覆盖仓库默认
  contents: read
  packages: write

concurrency:
  group: docker-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true        # 同分支新 push 自动作废旧 build

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  build-and-push:
    runs-on: ubuntu-24.04         # 钉死版本，避开 2026-10-19 latest→26 迁移
    steps:
      - uses: actions/checkout@v5
      - name: Compute image tag
        id: meta
        run: |
          SHORT_SHA="${GITHUB_SHA::7}"
          IMAGE="$(echo "${REGISTRY}/${IMAGE_NAME}" | tr '[:upper:]' '[:lower:]')"
          if [[ "${GITHUB_REF}" == refs/tags/* ]]; then
            echo "tags=${IMAGE}:${GITHUB_REF_NAME},${IMAGE}:latest" >> "$GITHUB_OUTPUT"
          elif [[ "${GITHUB_REF}" == refs/heads/main ]]; then
            echo "tags=${IMAGE}:sha-${SHORT_SHA},${IMAGE}:latest" >> "$GITHUB_OUTPUT"
          else
            echo "tags=${IMAGE}:pr-dryrun-${SHORT_SHA}" >> "$GITHUB_OUTPUT"
          fi
      - name: Decide push flag
        id: pushflag
        run: |
          if [[ "${{ github.event_name }}" == "pull_request" ]]; then
            echo "push=false" >> "$GITHUB_OUTPUT"
          else
            echo "push=true" >> "$GITHUB_OUTPUT"
          fi
      - uses: docker/setup-buildx-action@v3
      - name: Log in to GHCR
        if: steps.pushflag.outputs.push == 'true'
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - name: Build and push
        uses: docker/build-push-action@v6
        with:
          context: .
          push: ${{ steps.pushflag.outputs.push }}
          tags: ${{ steps.meta.outputs.tags }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
          build-args: |
            APP_VERSION=${{ github.run_number }}
```

**要点**

- `cache-from: type=gha` 打开 Actions 层缓存，第二次跑构建时间从 ~5 min 缩到 <1 min。
- PR 事件只 build 不 push（`push=false`），保证 Dockerfile 可构建即可。
- tag 策略：main 每次推 `sha-<短哈希>` + 覆盖 `latest`；release tag 时挂版本号 + `latest`。

### 3.3 每次部署（本地）

**镜像坐标**

- 仓库：`ghcr.io/mouhinu/knowledge_repository`（注意 `mouhinu` 全小写，`knowledge_repository` 由仓库名小写化得到）
- 可用 tag：`latest` / `sha-<短哈希7位>` / `v<版本号>`（release 时）

**首次或每次更新（推荐流程）**

```bash
cd ~/CodeDir/Knowledge_Repository

# 1. 拉最新镜像
docker compose pull knowledge-app

# 2. 起基础设施（若 MySQL/Milvus 已在跑可跳过）
docker compose -f docker-compose.infra.yml up -d
# 等端口 3307 (MySQL) 与 19530 (Milvus) 就绪
docker compose -f docker-compose.infra.yml ps

# 3. 用远程镜像起应用（--no-build 是关键，绕开 docker-compose.yml 的 build: 段）
IMAGE_NAME=ghcr.io/mouhinu/knowledge_repository \
APP_VERSION=latest \
docker compose up -d --no-build knowledge-app

# 4. 观察启动（Spring Boot + Flyway 冷启动约 124s，health probe start_period=150s）
docker compose logs -f knowledge-app

# 5. 验证
curl -sf http://localhost:8091/actuator/health
# 期望：{"status":"UP",...}
```

**回滚 / 复现特定版本**

```bash
IMAGE_NAME=ghcr.io/mouhinu/knowledge_repository \
APP_VERSION=sha-eab52ab \
docker compose pull knowledge-app && \
IMAGE_NAME=ghcr.io/mouhinu/knowledge_repository \
APP_VERSION=sha-eab52ab \
docker compose up -d --no-build knowledge-app
```

**永久免前缀（可选）** —— 在 `.env` 里追加：

```bash
IMAGE_NAME=ghcr.io/mouhinu/knowledge_repository
APP_VERSION=latest
```

之后所有命令都能省掉两行 env 前缀，直接 `docker compose pull && docker compose up -d --no-build knowledge-app`。

### 3.4 精简版：一行部署 shell 别名

追加到 `~/.zshrc` 或 `~/.bashrc`：

```bash
kdeploy() {
  local ver="${1:-latest}"
  cd ~/CodeDir/Knowledge_Repository || return 1
  IMAGE_NAME=ghcr.io/mouhinu/knowledge_repository APP_VERSION="$ver" \
    docker compose pull knowledge-app && \
  IMAGE_NAME=ghcr.io/mouhinu/knowledge_repository APP_VERSION="$ver" \
    docker compose up -d --no-build knowledge-app
  docker compose logs --tail=50 knowledge-app
}
alias kd='kdeploy'                # 拉 latest 并重启
alias kr='kdeploy sha-'           # 用法: kr eab52ab  → 回滚到指定 sha
```

`kd` 一行完成 pull + up；`kr <短sha>` 一行回滚。

> 提示：`scripts/gh-deploy.sh` 已把上述逻辑落成带干跑/健康门禁/`sha→latest` 回退/生产预留通道的正式脚本，**并统一改走 crane 拉 tar → docker load**（不再依赖 `docker compose pull`，避开 Docker Desktop 系统代理对 ghcr.io 的拦截），推荐优先使用；上面 `kd`/`kr` 这两个基于 `docker compose pull` 的别名仅在 daemon 能直连 GHCR 的环境下作轻量兜底。

### 3.5 触发一次重新构建（不 push 新 commit）

Actions → **Build & Push Docker Image** → 右上 **Run workflow** → 选 `main` → Run workflow。

`workflow_dispatch` 触发器不受 paths 过滤限制，任何情况下都能手动跑一次；适合验证 Actions 环境或补推某个 commit 的镜像。

---

## 四、常见故障与处置

| 现象 | 根因 | 处置 |
| --- | --- | --- |
| `pull access denied` / `unauthorized` 或 `denied: requested access to the resource is denied` | PAT 过期 / 缺 `read:packages` / 未登录 | 重新执行 `docker login ghcr.io -u mouhinU --password-stdin`，若 PAT 过期到 GitHub 重新生成 |
| `no such image: ghcr.io/...:latest` | Actions 还没推上去，或这次 commit 被 paths 过滤跳过 | 查 https://github.com/mouhinU/Knowledge_Repository/actions/workflows/docker-image.yml 最新一条是否 success；用 workflow_dispatch 手动重跑（§3.5） |
| `docker compose up` 又跑了一次本地 build | 忘了 `--no-build`；compose 见到 `services.knowledge-app.build:` 就会重编 | 显式加 `--no-build`；或在 `.env` 里固定 `IMAGE_NAME` + `APP_VERSION` 并始终 `--no-build` |
| `unknown blob` / `manifest unknown` | 拉的是尚未推送的 tag（比如你手工拼了个未构建的 sha） | 确认该 commit 触发过 docker-image.yml 且 success；用 `latest` 或 `ghcr.io` 页面查看已有 tag |
| 容器起来了但 `/actuator/health` 502 / 空 | 冷启动 124s，health 探针 start_period 150s 内会显示 `starting` | 再等 30s 或看 `docker compose logs`；若持续 DOWN 查 DB/Milvus 端口连通性 |
| Actions `Log in to GHCR` 步骤失败 | 极少见，多为 GitHub 服务侧 | Actions 页面右上重跑失败 job；确认 workflow 顶层 `permissions.packages: write` 未被误删 |
| Actions 显示"pending / queued"很久 | 无可用 runner（并发占满 / 账户超额） | GitHub Settings → Actions → Runners 查；等队列清空即可 |
| Runner image 迁移公告（2026-10-19 latest → 26） | `ubuntu-latest` 会静默变基 | 本仓库已钉 `ubuntu-24.04`；观察 Ubuntu 26.04 GA 稳定后再单独 PR 迁移 |

---

## 五、方案 B（备用）：Actions 上传 jar，本地拉回构建

只在「GHCR 不可达 / 只需 jar 不用镜像」时启用。

### 5.1 在 `docker-image.yml` 或新增 workflow 里加一步 artifact 上传

```yaml
      - name: Package jar
        run: ./mvnw -B -DskipTests -pl knowledge-web -am package

      - name: Upload jar artifact
        uses: actions/upload-artifact@v4
        with:
          name: knowledge-web-jar
          path: knowledge-web/target/knowledge-web-*.jar
          retention-days: 30
```

### 5.2 本地拉取并部署

```bash
# 若装了 gh CLI（推荐）
brew install gh && gh auth login
gh run download --repo mouhinU/Knowledge_Repository --name knowledge-web-jar --dir ./dist

# 用 docker/Dockerfile.prebuilt 直接 COPY jar，秒级 build
docker build -f docker/Dockerfile.prebuilt \
  -t ghcr.io/mouhinu/knowledge_repository:local .

IMAGE_NAME=ghcr.io/mouhinu/knowledge_repository APP_VERSION=local \
  docker compose up -d --no-build knowledge-app
```

无 `gh` CLI 时走 REST API：

```bash
# 1. 查最近一次成功 run id
RUN_ID=$(curl -s "https://api.github.com/repos/mouhinU/Knowledge_Repository/actions/runs?status=success&per_page=1" \
  -H "Authorization: Bearer $GH_PAT" | python3 -c "import json,sys;print(json.load(sys.stdin)['workflow_runs'][0]['id'])")
# 2. 查该 run 的 artifact id
ART_ID=$(curl -s "https://api.github.com/repos/mouhinU/Knowledge_Repository/actions/runs/$RUN_ID/artifacts" \
  -H "Authorization: Bearer $GH_PAT" | python3 -c "
import json,sys
d=json.load(sys.stdin)
for a in d['artifacts']:
    if a['name']=='knowledge-web-jar': print(a['id']); break
")
# 3. 下载 zip 并解出 jar
curl -sL "https://api.github.com/repos/mouhinU/Knowledge_Repository/actions/artifacts/$ART_ID/zip" \
  -H "Authorization: Bearer $GH_PAT" -o jar.zip && unzip -o jar.zip -d ./dist
```

### 5.3 适用边界

- 优点：jar 体积（~80 MB）远小于镜像（~500 MB），网络差时传输快；不依赖任何镜像仓库。
- 缺点：本地仍需一次 `docker build`；JVM / 依赖版本以本地 `Dockerfile.prebuilt` 为准，理论上与 Actions 构建一致（同为 `eclipse-temurin:21-jre`），但主机 `mvn package` 用的是本地 `.m2` 缓存，需留意快照漂移。

---

## 六、方案 C（备用）：Actions 内 `docker save`，离线拉 tar 包

用于完全断网的内网 / U 盘交付场景。

### 6.1 workflow 片段（新增 job，与 `build-and-push` 并列）

```yaml
  build-offline-tar:
    if: github.event_name == 'workflow_dispatch'   # 只在手动触发时产大文件
    runs-on: ubuntu-24.04
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@v5
      - name: Build image (no push)
        run: docker build -t knowledge-repository:offline .
      - name: Export tar
        run: docker save knowledge-repository:offline | gzip > knowledge-repository.tar.gz
      - uses: actions/upload-artifact@v4
        with:
          name: knowledge-repository-image
          path: knowledge-repository.tar.gz
          retention-days: 14
```

### 6.2 本地/离线机加载

```bash
gh run download --name knowledge-repository-image
gunzip -c knowledge-repository.tar.gz | docker load
IMAGE_NAME=knowledge-repository APP_VERSION=offline \
  docker compose up -d --no-build knowledge-app
```

### 6.3 注意事项

- Actions 单 artifact 上限 10 GB，压缩后本项目镜像约 150 MB，可控。
- `retention-days` 建议 ≤14 天，避免长期占用私有仓库存储配额。
- 完全离线环境记得把 `docker-compose.infra.yml` 里的 MySQL / Milvus / etcd / MinIO 镜像也预先 `docker save` 打包过去。

---

## 七、与现有脚本的整合

- `scripts/deploy.sh`：本地编译 → 起容器（开发改代码时用）。
- **`scripts/gh-deploy.sh`（已落地）**：从 GHCR 拉取 Actions 预构建镜像 → 走 **crane 拉 tar → docker load → compose up --force-recreate --no-build** 通道（不再依赖 `docker pull`）。三种版本模式：

  | 模式 | 目标 tag | 用途 |
  |------|----------|------|
  | 默认 | `sha-<7>`（main 最新提交短号，API 失败回退本地 `origin/main`；快照缺失再回退 `latest`） | 本地日常，快照可复现 |
  | `--latest` | `latest` | 本地快速滚动 |
  | `--sha 1a2b3c4` | `sha-1a2b3c4` | 回滚到指定快照 |
  | `--prod v1.2.3` | `v1.2.3` | **生产预留通道**：由打 `v*` 标签触发 CI 产出；本脚本仅负责拉取 + 起容器，未接审批/回滚 |

  其它能力：`--infra` 连带拉起基础设施；`--dry-run` 只打印不执行；健康门禁 `curl /actuator/health` 直到 `status:UP`；`IMAGE_NAME` / `REGISTRY` / `GHCR_USER` / `GHCR_TOKEN` / `PLATFORM` / `CRANE_HOME` / `CRANE_BIN` / `CRANE_VERSION` 可用同名环境变量覆盖；未登录 GHCR 时脚本会自动 `git credential fill` 取 PAT 交给 `crane auth login`（并清理 `~/.docker/config.json` 里 ghcr.io 的空 auths 条目，避免 token 交换 DENIED）；本机若没有 `crane`，首次运行会自动从 GitHub Releases 下载 `v0.22.1`（`Darwin_x86_64` / `Darwin_arm64` / `Linux_x86_64` / `Linux_arm64`）到 `~/.local/bin/crane`；拉取全程 `unset HTTP(S)_PROXY; export NO_PROXY='*'`，避开 Docker Desktop 内置代理的 EOF 拦截。

- 保留 `scripts/docker-build.sh` 作为「无网 / GHCR 拉取失败」时的兜底通道（走 `docker/Dockerfile.prebuilt`）。
- 保留 `docker/Dockerfile.prebuilt` 单阶段镜像（主机 `mvn package` + 直接 COPY jar）：CI 挂或网络差时用；绕开容器内 `mvn dependency:go-offline` 挂 16min+ 的坑。

---

## 八、安全与运维清单

1. **GITHUB_TOKEN 权限最小化**：workflow 顶层 `permissions: { contents: read, packages: write }`，不给 `pull-requests: write` / `security-events: write` 等无关权限。
2. **不推 `:latest` 到不可逆环境**：生产用不可变 tag `sha-xxxxxxx` 或 `v1.2.3`；`latest` 只作开发便利。
3. **凭据不写进 workflow**：MySQL 口令、API Key 等通过 GitHub Secrets 注入 compose env，禁止在 yml 里明文；本地走 `.env`（已在 `.gitignore`）。
4. **PAT 最小 scope**：本地拉取用 **read:packages 只读** 即可；写权限只在 CI 内部由 `GITHUB_TOKEN` 承担，不需要额外 PAT。
5. **日志脱敏**：容器 stdout 中的 JWT / 令牌片段需在启动前由 logback pattern 屏蔽（详见 [docs/security-guideline.md](security-guideline.md) §日志脱敏）。
6. **回滚预案**：`docker compose pull --ignore-buildable` + 指定 `APP_VERSION=sha-<上一版>` 秒级回滚；GHCR 保留策略设 90 天自动清理（Settings → Packages → 版本保留策略）。
7. **镜像瘦身（可选）**：Dockerfile runtime 阶段加 `jlink` 或换 `eclipse-temurin:21-jre-alpine`，可减 60~80 MB；注意 alpine 的 musl 与 glibc 兼容问题，先测通再上。
8. **Runner 版本跟进**：`runs-on` 已钉 `ubuntu-24.04`；GitHub 官方 issue [#14748](https://github.com/actions/runner-images/issues/14748) 提到 2026-10-19 `ubuntu-latest` 会静默迁至 Ubuntu 26 —— 我们不受影响，但 24.04 支持周期结束时（预计 2029 年）需切至 26.04 或更新版本。

---

## 九、快速验证清单

按方案 A 落地后，逐项确认：

- [ ] 打开 `ghcr.io/mouhinu/knowledge_repository` 能看到 `latest` 与 `sha-xxxxxxx` 两类 tag（首次可能需要仓库所有者登录一次以确认包已创建）。
- [ ] `docker manifest inspect ghcr.io/mouhinu/knowledge_repository:latest` 能拉到 OCI index（401 表示 §3.1 步骤 ①/② 未做）。
- [ ] 本地 `docker pull ghcr.io/mouhinu/knowledge_repository:latest` 成功。
- [ ] `docker compose -f docker-compose.infra.yml ps` 显示 MySQL / Milvus 均 healthy。
- [ ] `IMAGE_NAME=ghcr.io/mouhinu/knowledge_repository APP_VERSION=latest docker compose up -d --no-build knowledge-app` 无报错。
- [ ] `curl -sf http://localhost:8091/actuator/health` 返回 `{"status":"UP",...}`（冷启动约 124s，前 150s 探针显示 starting 属正常）。
- [ ] 浏览器 `http://localhost:8091/admin/` 能登录后台，管理端首页正常渲染。
- [ ] `docker compose logs knowledge-app | grep "Started"` 显示 Spring Boot 启动成功。
