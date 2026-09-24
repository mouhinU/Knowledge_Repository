# Knowledge Repository 脚本工具集

本项目包含一系列 Shell 脚本，用于简化 Knowledge Repository 知识库系统的运维管理。

## 快速开始

```bash
# 查看所有可用脚本
./scripts/help.sh

# 查看版本信息
./scripts/version.sh

# 一键部署完整环境
./scripts/deploy.sh
```

## 脚本分类

### 服务管理

| 脚本           | 说明   | 示例                      |
|--------------|------|-------------------------|
| `start.sh`   | 启动服务 | `./scripts/start.sh`    |
| `stop.sh`    | 停止服务 | `./scripts/stop.sh`     |
| `restart.sh` | 重启服务 | `./scripts/restart.sh`  |
| `status.sh`  | 查看状态 | `./scripts/status.sh`   |
| `logs.sh`    | 查看日志 | `./scripts/logs.sh 200` |

### 部署运维

| 脚本                | 说明        | 示例                          |
|-------------------|-----------|-----------------------------|
| `deploy.sh`       | 本地构建部署    | `./scripts/deploy.sh`       |
| `gh-deploy.sh`    | GHCR 拉取部署   | `./scripts/gh-deploy.sh`    |
| `env-check.sh`    | 环境检查      | `./scripts/env-check.sh`    |
| `health-check.sh` | 健康检查      | `./scripts/health-check.sh` |
| `docker-build.sh` | 构建镜像      | `./scripts/docker-build.sh` |
| `compose.sh`      | Compose管理 | `./scripts/compose.sh up`   |

### 数据管理

| 脚本           | 说明     | 示例                         |
|--------------|--------|----------------------------|
| `backup.sh`  | 数据备份   | `./scripts/backup.sh`      |
| `export.sh`  | 导出数据   | `./scripts/export.sh`      |
| `import.sh`  | 导入数据   | `./scripts/import.sh <文件>` |
| `init-db.sh` | 初始化数据库 | `./scripts/init-db.sh`     |
| `clean.sh`   | 清理文件   | `./scripts/clean.sh`       |

### 业务管理

| 脚本          | 说明   | 示例                                |
|-------------|------|-----------------------------------|
| `doc.sh`    | 文档管理 | `./scripts/doc.sh list`           |
| `user.sh`   | 用户管理 | `./scripts/user.sh list`          |
| `dept.sh`   | 部门管理 | `./scripts/dept.sh tree`          |
| `role.sh`   | 角色权限 | `./scripts/role.sh list`          |
| `config.sh` | 系统配置 | `./scripts/config.sh list`        |
| `search.sh` | 搜索管理 | `./scripts/search.sh query '关键词'` |

### 监控诊断

| 脚本              | 说明   | 示例                          |
|-----------------|------|-----------------------------|
| `monitor.sh`    | 服务监控 | `./scripts/monitor.sh`      |
| `metrics.sh`    | 监控指标 | `./scripts/metrics.sh jvm`  |
| `perf.sh`       | 性能分析 | `./scripts/perf.sh memory`  |
| `disk-usage.sh` | 磁盘分析 | `./scripts/disk-usage.sh`   |
| `network.sh`    | 网络诊断 | `./scripts/network.sh ping` |
| `report.sh`     | 系统报告 | `./scripts/report.sh`       |

### 安全审计

| 脚本            | 说明   | 示例                                  |
|---------------|------|-------------------------------------|
| `security.sh` | 安全管理 | `./scripts/security.sh scan`        |
| `audit.sh`    | 审计日志 | `./scripts/audit.sh search 'admin'` |
| `session.sh`  | 会话管理 | `./scripts/session.sh list`         |

### 高级功能

| 脚本             | 说明      | 示例                                    |
|----------------|---------|---------------------------------------|
| `apikey.sh`    | API Key | `./scripts/apikey.sh create '应用名'`    |
| `webhook.sh`   | Webhook | `./scripts/webhook.sh list`           |
| `schedule.sh`  | 定时任务    | `./scripts/schedule.sh list`          |
| `notify.sh`    | 通知管理    | `./scripts/notify.sh send email '消息'` |
| `plugin.sh`    | 插件管理    | `./scripts/plugin.sh list`            |
| `theme.sh`     | 主题管理    | `./scripts/theme.sh list`             |
| `cache.sh`     | 缓存管理    | `./scripts/cache.sh clear all`        |
| `ratelimit.sh` | 限流配置    | `./scripts/ratelimit.sh status`       |
| `task.sh`      | 异步任务    | `./scripts/task.sh list`              |

### 调试排错

| 脚本                | 说明    | 示例                                |
|-------------------|-------|-----------------------------------|
| `debug.sh`        | 调试工具  | `./scripts/debug.sh heap`         |
| `troubleshoot.sh` | 故障排查  | `./scripts/troubleshoot.sh check` |
| `log.sh`          | 日志管理  | `./scripts/log.sh level DEBUG`    |
| `api-test.sh`     | API测试 | `./scripts/api-test.sh`           |

## 常用场景

### 场景1: 首次部署

```bash
# 1. 检查环境
./scripts/env-check.sh

# 2. 一键部署
./scripts/deploy.sh

# 3. 验证部署
./scripts/health-check.sh
```

### 场景1b: 从 GHCR 拉取部署（免本地编译）

由 GitHub Actions 预构建镜像推到 GHCR，本地只做「crane 拉取 + docker load + compose up」，比 `deploy.sh` 更快、可复现。

**为什么用 crane 而不是 `docker pull`**：macOS 上 Docker Desktop 会强制走内置代理 `http.docker.internal:3128`，常年把 `ghcr.io` / `registry-1.docker.io` 直接 EOF 掉；`curl` 同一 URL 却 HTTP 200。`crane`（[google/go-containerregistry](https://github.com/google/go-containerregistry)）是纯用户态 CLI，读 shell 侧代理与凭据，脚本会显式 `unset HTTP(S)_PROXY; export NO_PROXY='*'`，把 manifest/blob 拉下来后再 `docker load` 交给 daemon。

```bash
# 本地：部署 main 最新提交快照 sha-<7>（API 取不到时回退本地 origin/main；快照缺失再回退 latest）
./scripts/gh-deploy.sh

# 本地：部署滚动标签 latest
./scripts/gh-deploy.sh --latest

# 本地：部署指定 7 位提交号
./scripts/gh-deploy.sh --sha 1a2b3c4

# 生产（预留通道）：部署发布标签 v<semver>（由打 v*.*.* tag 触发 CI 产出）
./scripts/gh-deploy.sh --prod v1.2.3

# 只预览将要执行的命令，不实际部署
./scripts/gh-deploy.sh --dry-run
```

> 前置：GHCR 包默认私有，脚本会自动用 `git credential fill` 拿 PAT 完成 `crane auth login ghcr.io`（PAT 需 `read:packages`）；也可显式 `export GHCR_TOKEN=ghp_xxx GHCR_USER=yourname` 覆盖。首次运行若无 `crane`，脚本会下载 `v0.22.1` 到 `~/.local/bin/crane`（可通过 `CRANE_HOME` / `CRANE_BIN` / `CRANE_VERSION` 覆盖）。若目标镜像与共享 MySQL 的 Flyway 迁移历史不一致（如某迁移被原地改过），容器会因校验失败而起不来——此为迁移纪律问题，非脚本缺陷。

### 场景2: 日常运维

```bash
# 查看服务状态
./scripts/status.sh

# 查看实时日志
./scripts/logs.sh

# 查看性能指标
./scripts/perf.sh status
```

### 场景3: 故障排查

```bash
# 1. 运行检查
./scripts/troubleshoot.sh check

# 2. 查看详细日志
./scripts/log.sh error

# 3. 收集诊断信息
./scripts/troubleshoot.sh collect
```

### 场景4: 数据备份恢复

```bash
# 备份数据
./scripts/backup.sh

# 导出数据
./scripts/export.sh

# 查看磁盘使用
./scripts/disk-usage.sh
```

## 注意事项

1. 所有脚本需要在项目根目录执行
2. 部分脚本需要服务运行中才能正常工作
3. 危险操作（如 `init-db.sh`）会有确认提示
4. 脚本会自动检测服务状态并给出提示

## 端口说明

| 服务     | 端口    |
|--------|-------|
| 应用服务   | 8091  |
| Milvus | 19530 |
| MinIO  | 9001  |
| Ollama | 11434 |

## 相关文件

- 应用日志: `/tmp/kb-server.log`
- 数据目录: `./data/`
- 备份目录: `./data/backups/`
- 导出目录: `./data/exports/`
