# Knowledge Repository 项目总结

## 项目概述

Knowledge Repository 是一个基于 LangChain4j 和 Milvus 构建的 RAG（检索增强生成）知识库系统，支持多格式文档摄入、向量化存储与语义检索，并提供完整的系统管理界面。

**技术栈：** Spring Boot 3.4.4 / Java 21 / MyBatis-Plus 3.5.17 / LangChain4j 1.0.1 / Milvus 2.5.4 / PDFBox 3.0.4 / Apache
Tika 3.1.0 / Apache POI 5.3.0 / H2 + MySQL / Flyway

---

## 架构设计

项目采用 DDD（领域驱动设计）四层架构，Maven 多模块组织：

```
knowledge-web            表现层    Controller、安全配置、全局异常处理
knowledge-application    应用层    用例编排、事务管理、DTO 转换
knowledge-domain         领域层    实体、值对象、领域服务、仓储接口、领域事件
knowledge-infrastructure 基础设施层  Repository 实现、DO、Mapper、Milvus 向量存储、文档解析
```

依赖方向：Web → Application → Domain ← Infrastructure

---

## 模块统计

| 模块                       | Java 文件数 | 核心职责               |
|--------------------------|----------|--------------------|
| knowledge-domain         | 20       | 业务规则、状态机、权限过滤、分块策略 |
| knowledge-infrastructure | 22       | 持久化、向量存储、多格式文档解析   |
| knowledge-application    | 6        | 用例编排（摄入、查询、管理）     |
| knowledge-web            | 9        | REST API、安全配置、管理界面 |
| **合计**                   | **57**   |                    |

---

## 核心领域模型

**聚合根：** Document — 文档生命周期状态机（UPLOADED → PROCESSING → INDEXED → ARCHIVED / FAILED）

**实体：** User、Department、Role、DocumentChunk

**值对象：** Permission（权限上下文）、ChunkingConfig（分块配置）、SearchResult（检索结果）、DocumentStatusEnum、DocumentVisibilityEnum

**领域服务：**

- DocumentIngestionDomainService — 文本分块（支持 Token 上限、段落/页面边界、重叠）
- PermissionDomainService — 构建 Milvus 过滤表达式，实现 RBAC + 文档级 ACL 权限隔离

---

## 文档处理流水线

```
上传文件 → 格式检测（Tika） → 文本提取 → MD5 去重 → 创建文档记录
    → 文本分块 → 批量向量化（Embedding） → 存入 Milvus + 数据库
```

**支持格式：** PDF、Word(.docx)、Excel(.xlsx)、PowerPoint(.pptx)、TXT、CSV、HTML、RTF

**解析策略：**

- PDF：PDFBox 按页提取，检测扫描型 PDF（低文本密度页面）并记录警告
- Word：Apache POI 按段落提取，每 30 段切分一个 section
- Excel：Apache POI 按工作表提取，保留行列结构
- PowerPoint：Apache POI 按幻灯片提取，遍历文本形状
- 其他格式：Tika AutoDetectParser 通用解析

---

## 权限模型

采用 RBAC + 文档级 ACL 双重控制，四个可见性级别：

| 级别         | 可见范围     |
|------------|----------|
| PUBLIC     | 所有用户可见   |
| INTERNAL   | 同部门可见    |
| RESTRICTED | 指定角色可见   |
| PRIVATE    | 仅文档所有者可见 |

检索时通过 PermissionDomainService 构建 Milvus 元数据过滤表达式，超级管理员不受限制。

---

## Embedding 配置

通过 `@ConditionalOnProperty` 切换提供商，均使用 OpenAI 兼容协议：

| 提供商           | 模型                | Base URL                                  |
|---------------|-------------------|-------------------------------------------|
| DashScope（默认） | text-embedding-v3 | dashscope.aliyuncs.com/compatible-mode/v1 |
| Ollama        | bge-m3            | localhost:11434/v1                        |

---

## REST API

| 方法                  | 路径                                | 功能          |
|---------------------|-----------------------------------|-------------|
| POST                | /api/document/upload              | 上传并处理文档     |
| POST                | /api/knowledge/search             | 语义检索（带权限过滤） |
| GET                 | /api/admin/document/{key}         | 文档详情        |
| GET                 | /api/admin/document/stats         | 知识库统计       |
| PUT                 | /api/admin/document/{key}/archive | 归档文档        |
| DELETE              | /api/admin/document/{key}         | 删除文档（级联清理）  |
| GET/POST/PUT/DELETE | /api/admin/user/**                | 用户管理        |
| GET/POST/PUT        | /api/admin/department/**          | 部门管理（含树形结构） |
| GET                 | /api/admin/system/status          | 系统状态        |

---

## 数据库设计

6 张表，Flyway 管理迁移：

- sys_department — 部门层级
- sys_user — 用户
- sys_role — 角色
- sys_user_role — 用户角色关联
- kb_document — 文档（28 字段）
- kb_document_chunk — 文档分块（14 字段，含权限元数据）

开发环境使用 H2 内嵌数据库，生产环境切换 MySQL。

---

## 基础设施

**Docker Compose：** etcd + MinIO + Milvus Standalone（v2.5.4），端口 19530

**服务端口：** 8091

**管理界面：** http://localhost:8091/admin.html（单页应用，含仪表盘、文档管理、知识检索、用户权限、系统配置五个模块）

---

## 启动前置条件

1. 安装 Docker 并启动 Milvus：`docker compose up -d`
2. 配置 Embedding API Key（环境变量 DASHSCOPE_API_KEY）或切换为 Ollama
3. 运行服务：`./mvnw -pl knowledge-web spring-boot:run -DskipTests`
