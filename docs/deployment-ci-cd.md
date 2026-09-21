# CI-CD 部署方案（GitHub Actions → 本地 Docker）

> 目标：把 GitHub Actions 上编译 / 打包的产物顺利落到本地 Docker 中运行、复用现有 `docker-compose.yml` 与 `scripts/deploy.sh`。
>
> 适用场景：本地开发机自测、演示环境、离线内网部署。
>
> 关联文件：[Dockerfile](../Dockerfile)、[docker-compose.yml](../docker-compose.yml)、[.github/workflows/ci.yml](../.github/workflows/ci.yml)、[scripts/deploy.sh](../scripts/deploy.sh)、[scripts/docker-build.sh](../scripts/docker-build.sh)。

---

## 一、现状盘点

仓库内已具备的部署要素：

- **多阶段 Dockerfile**：`maven:3.9-eclipse-temurin-21` 编译 → `eclipse-temurin` 运行时镜像。
- **docker-compose.yml**：定义 `knowledge-app` 单容器，依赖宿主机 MySQL（3307）与 Milvus（19530）；镜像名 `${IMAGE_NAME}:${APP_VERSION}`。
- **docker-compose.infra.yml**：MySQL / Milvus / etcd / MinIO 等共用基础设施。
- **scripts/deploy.sh**：一键起环境（env-check → Milvus → Ollama 提示 → 应用）。
- **scripts/docker-build.sh**：本地直接 `mvn package` + `docker build` 的兜底脚本。
- **.github/workflows/ci.yml**：目前只做 `mvn clean verify -DskipTests`，**未产出可部署物**。

**结论**：Dockerfile 与 compose 已就绪，缺的是「Actions 编译完成后产物如何流转至本地」这一段。以下三方案按需选一即可。

---

## 二、方案对比与选型建议

| 方案 | 传输媒介 | 本地一步命令 | 依赖外部服务 | 离线可用 | 推荐度 |
| --- | --- | --- | --- | --- | --- |
| A. 推镜像到 GHCR | OCI 镜像层 | `docker compose pull && up` | GitHub Container Registry（免费） | ❌ | **★★★★★（首选）** |
| B. 下载 jar → 本地 docker build | jar 文件 | `gh run download && docker build && up` | Actions Artifacts 存储（90 天） | ⚠️ 需本地有 Dockerfile | ★★★☆☆ |
| C. Actions 内 docker save → 拉 tar 包 | 镜像 tar | `gunzip \| docker load && up` | Actions Artifacts 存储 | ✅ 完全离线可用 | ★★☆☆☆（内网专用） |

**选型规则**：
- 日常开发机自测 / 团队协作 → **方案 A**，一行 pull 完事。
- 公司网络屏蔽 GHCR / 只想拿 jar → **方案 B**。
- 内网离线环境、需要 U 盘搬环境 → **方案 C**。

---

## 三、方案 A（推荐）：推镜像到 GHCR

### 3.1 前置准备（一次性）

1. GitHub 仓库 Settings → Actions → General → Workflow permissions，选 **Read and write**（否则 `GITHUB_TOKEN` 无权推 GHCR）。
2. 本地首次登录 GHCR：

   ```bash
   # 用 PAT（scope: write:packages）或 gh 生成的短期 token
   echo "$GH_PAT" | docker login ghcr.io -u <你的GitHub用户名> --password-stdin
   ```

### 3.2 新增/扩展 workflow

已落地：[`.github/workflows/docker-image.yml`](../.github/workflows/docker-image.yml)（与现有 `ci.yml` 并存，避免影响 CI 校验门禁）。相较原始模板额外加了：`pull_request` 触发时只做 build 不 push（dry-run 校验 Dockerfile 可构建），main push / tag push 才真正推 GHCR；`APP_VERSION` build-arg 注入 `github.run_number`。

参考 YAML 骨架（以仓库中实际文件为准）：

```yaml
name: Build & Push Docker Image

on:
  push:
    branches: [main]
    tags: ["v*"]
  pull_request:
    branches: [main]
  workflow_dispatch: {}   # 允许手动触发

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}   # 例如 yourname/Knowledge_Repository（自动小写）

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write

    steps:
      - uses: actions/checkout@v4

      - name: Compute image tag
        id: meta
        run: |
          SHORT_SHA="${GITHUB_SHA::7}"
          # GHCR 要求 repository 段全小写；github.repository 会保留大小写。
          IMAGE="$(echo "${REGISTRY}/${IMAGE_NAME}" | tr '[:upper:]' '[:lower:]')"
          if [[ "${GITHUB_REF}" == refs/tags/* ]]; then
            echo "tags=${IMAGE}:${GITHUB_REF_NAME},${IMAGE}:latest" >> "$GITHUB_OUTPUT"
          elif [[ "${GITHUB_REF}" == refs/heads/main ]]; then
            echo "tags=${IMAGE}:sha-${SHORT_SHA},${IMAGE}:latest" >> "$GITHUB_OUTPUT"
          else
            # PR / 其他分支：只计算标签用于本地构建，不推送
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

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

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

**要点说明**：

- `docker/build-push-action` 直接复用你现有的多阶段 Dockerfile，无需二次封装。
- `cache-from: type=gha` 打开 Actions 层缓存，第二次跑构建时间从 ~5min 缩到 <1min。
- tag 策略：每次 main 推一个 `sha-<短哈希>`，永远保留一个 `latest`；打 release tag 时额外挂版本号，便于回滚。

### 3.3 本地拉取与运行

配合项目根目录 `.env`（compose 会读取 `IMAGE_NAME` / `APP_VERSION`）：

```bash
# .env 示例
IMAGE_NAME=ghcr.io/<你的GitHub用户名>/Knowledge_Repository
APP_VERSION=latest
MYSQL_PASSWORD=<你的MySQL口令>
```

然后一条命令：

```bash
docker compose pull && docker compose up -d
# 或复用现有脚本
./scripts/deploy.sh
```

验证：`curl http://localhost:8091/actuator/health` 或直接打开 `http://localhost:8091/admin.html`。

### 3.4 更新流水线（可选增强）

- 加一个 `deploy-staging` job，通过 SSH 到目标机执行 `docker compose pull && up -d`（用 `appleboy/ssh-action`）。
- 或加 `webhook` 触发 n8n / Portainer 完成灰度发布。

---

## 四、方案 B：Actions 上传 jar，本地拉回构建

### 4.1 workflow 里加一步 artifact 上传

在现有 `.github/workflows/ci.yml` 的 `clean verify` 之后追加：

```yaml
      - name: Package jar
        run: mvn -B -DskipTests package

      - name: Upload jar artifact
        uses: actions/upload-artifact@v4
        with:
          name: knowledge-web-jar
          path: knowledge-web/target/knowledge-web-*.jar
          retention-days: 30
```

### 4.2 本地拉取并部署

```bash
# 安装 gh CLI: https://cli.github.com/
gh auth login

# 下载最近一次成功 run 的 artifact 到 ./dist/
gh run download --repo <owner>/<repo> --name knowledge-web-jar --dir ./dist

# 用现有 Dockerfile 的 runtime 阶段（若只想覆盖 jar，可写一个 Dockerfile.local）
docker build -f - -t knowledge-repository:local . <<EOF
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY dist/knowledge-web-*.jar app.jar
EXPOSE 8091
ENTRYPOINT ["java","-jar","app.jar"]
EOF

IMAGE_NAME=knowledge-repository APP_VERSION=local docker compose up -d
```

### 4.3 适用边界

- 优点：jar 体积（~80 MB）远小于镜像（~500 MB），网络差时传输快；不依赖任何镜像仓库。
- 缺点：本地仍需一次 docker build；JVM / 依赖版本以本地 Dockerfile 为准，可能与 Actions 环境有偏差。

---

## 五、方案 C：Actions 内 `docker save`，离线拉 tar 包

### 5.1 workflow 片段

```yaml
  build-offline-tar:
    if: github.event_name == 'workflow_dispatch'   # 手动触发，避免每次 push 都产大文件
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
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

### 5.2 本地/离线机加载

```bash
gh run download --name knowledge-repository-image
gunzip -c knowledge-repository.tar.gz | docker load
IMAGE_NAME=knowledge-repository APP_VERSION=offline docker compose up -d
```

### 5.3 注意事项

- Actions 单 artifact 上限 10 GB，压缩后本项目镜像约 150 MB，可控。
- `retention-days` 建议 ≤14 天，避免长期占用私有仓库存储配额。
- 完全离线环境记得把 `docker-compose.infra.yml`（MySQL/Milvus）也预先 load 好。

---

## 六、与现有脚本的整合

- `scripts/deploy.sh` 目前是本地编译 → 起容器。方案 A 落地后，建议新增一条 `pull-and-run.sh`：

  ```bash
  #!/bin/bash
  cd "$(dirname "$0")/.."
  docker compose pull
  docker compose up -d knowledge-app
  docker compose logs -f --tail=50 knowledge-app
  ```

- 保留 `scripts/docker-build.sh` 作为「无网 / GHCR 拉取失败」时的兜底通道，两者互补。

---

## 七、安全与运维清单

1. **GITHUB_TOKEN 权限最小化**：只在 build-and-push job 声明 `packages: write`，不要给全局。
2. **不推 :latest 到不可逆环境**：生产用不可变 tag `sha-xxxxxxx` 或 `v1.2.3`；`latest` 只作开发便利。
3. **凭据不写进 workflow**：MySQL 口令、API Key 等通过 GitHub Secrets 注入 compose env，禁止在 yml 里明文。
4. **日志脱敏**：容器 stdout 中的 JWT / 令牌片段需在启动前由 logback pattern 屏蔽（现有 [docs/security-guideline.md](security-guideline.md) §日志脱敏）。
5. **回滚预案**：`docker compose pull --ignore-buildable` + 指定 `APP_VERSION=sha-<上一版>` 秒级回滚；GHCR 保留策略设 90 天自动清理。
6. **镜像瘦身（可选）**：Dockerfile runtime 阶段加 `jlink` 或换 `eclipse-temurin:21-jre-alpine`，可减 60~80 MB；注意 alpine 的 musl 与 glibc 兼容问题，先测通再上。

---

## 八、快速验证清单

按方案 A 落地后，逐项确认：

- [ ] GHCR 上出现 `ghcr.io/<owner>/<repo>:latest` 与 `sha-xxxxxxx` 两个 tag
- [ ] `docker manifest inspect ghcr.io/<owner>/<repo>:latest` 能拉到元信息
- [ ] `curl http://localhost:8091/actuator/health` 返回 `{"status":"UP"}`
- [ ] 前端 `http://localhost:8091/admin.html` 能登录
- [ ] `docker compose logs knowledge-app | grep "Started"` 显示 Spring Boot 启动成功
- [ ] 手动改一行代码 push，5 分钟内本地 `docker compose pull` 拿到新镜像并 up 生效

任一失败排查顺序：workflow 日志 → `docker login ghcr.io` 状态 → `.env` 中 IMAGE_NAME/APP_VERSION → 容器 `logs` 与端口占用。
