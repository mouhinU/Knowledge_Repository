# ============================================================
# Stage 1: Build
# ============================================================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# 先复制 pom 文件，利用 Docker 层缓存加速依赖下载
COPY pom.xml .
COPY knowledge-client/pom.xml knowledge-client/
COPY knowledge-domain/pom.xml knowledge-domain/
COPY knowledge-infrastructure/pom.xml knowledge-infrastructure/
COPY knowledge-application/pom.xml knowledge-application/
COPY knowledge-web/pom.xml knowledge-web/

# 下载依赖（仅 pom 变化时重新执行）
RUN mvn dependency:go-offline -B -q 2>/dev/null || true

# 复制源码并构建
COPY knowledge-client/src knowledge-client/src
COPY knowledge-domain/src knowledge-domain/src
COPY knowledge-infrastructure/src knowledge-infrastructure/src
COPY knowledge-application/src knowledge-application/src
COPY knowledge-web/src knowledge-web/src

# 默认在镜像构建阶段跑一遍测试（体检 HIGH H4：生产镜像跳过测试 → 回归漏检）。
# 单命令 clean package：编译/测试/打包一次跑完，避免 test + clean package 两遍全量编译拖慢构建。
# SKIP_TESTS 默认 false（跑测试）；本地应急/CI 已单独跑过测试时用 --build-arg SKIP_TESTS=true 跳过。
ARG SKIP_TESTS=false
RUN if [ "$SKIP_TESTS" = "true" ]; then \
        mvn clean package -B -q -DskipTests ; \
    else \
        mvn clean package -B -q ; \
    fi

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:21-jre

# 版本号（构建时注入，用于运行时识别镜像版本）
ARG APP_VERSION=unknown
LABEL maintainer="Knowledge-Repository"
LABEL description="Knowledge Repository - RAG 知识库管理系统"
LABEL version="${APP_VERSION}"

# 创建非 root 用户
RUN groupadd -r appuser && useradd -r -g appuser -d /app appuser

WORKDIR /app

# 从构建阶段复制 fat JAR
COPY --from=builder /build/knowledge-web/target/*.jar app.jar

# 创建数据目录
RUN mkdir -p /app/data/documents && chown -R appuser:appuser /app

# 切换到非 root 用户
USER appuser

# JVM 参数（可通过 JAVA_OPTS 环境变量覆盖）
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"
ENV APP_VERSION=${APP_VERSION}

EXPOSE 8091

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
