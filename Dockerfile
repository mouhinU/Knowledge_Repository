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
# Stage 2: jlink 定制 JRE
# ============================================================
# 用 jdeps 分析 fat jar 实际用到的 Java 模块，jlink 打包精简 JRE。
# 预期收益：JRE 从 ~75MB 降到 ~20-30MB（再省 40-50MB）。
# 风险：漏模块会导致 NoClassDefFoundError，需完整回归测试。
# ------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS jlink

# 从 builder 阶段复制 fat jar 用于分析
COPY --from=builder /build/knowledge-web/target/*.jar /app/

# jdeps 分析 fat jar 依赖的模块
# --ignore-missing-deps: 忽略第三方 jar 的缺失依赖（常见于可选依赖）
# --multi-release 21: 支持多版本 jar
# --print-module-deps: 输出逗号分隔的模块列表
RUN jdeps \
    --ignore-missing-deps \
    -q \
    --recursive \
    --multi-release 21 \
    --print-module-deps \
    /app/*.jar > /tmp/jdeps-modules.txt || true

# 手动补充 Spring Boot + 文档处理 + HTTP 客户端常用模块（jdeps 可能漏掉反射/动态加载的）
# java.base: 基础（必选）
# java.logging: SLF4J/Logback 日志
# java.sql: JDBC/JPA
# java.naming: JNDI/LDAP
# java.management: JMX
# java.instrument: Java agents
# java.desktop: AWT/Swing（PDFBox 字体渲染必需）
# java.xml: XML 解析
# java.security.jgss: Kerberos
# jdk.unsupported: sun.misc.Unsafe（很多库用）
# java.compiler: 动态编译
# java.scripting: JSR-223 脚本引擎
# java.net.http: HttpClient（LangChain4j/Spring WebClient 用）
# jdk.crypto.ec: 椭圆曲线加密（TLS 必需）
# java.security.sasl: SASL 认证
# java.rmi: RMI（JMX 可能用）
RUN echo "java.base,java.logging,java.sql,java.naming,java.management,java.instrument,java.desktop,java.xml,java.security.jgss,jdk.unsupported,java.compiler,java.scripting,java.net.http,jdk.crypto.ec,java.security.sasl,java.rmi" >> /tmp/jdeps-modules.txt

# jlink 打包精简 JRE
# --strip-debug: 去调试信息
# --no-man-pages: 去 man 手册
# --no-header-files: 去 JNI 头文件
# --compress=2: 最大压缩（ZIP 压缩）
RUN MODULES=$(cat /tmp/jdeps-modules.txt | tr '\n' ',' | sed 's/,$//') && \
    echo "=== jlink 模块列表: $MODULES ===" && \
    jlink \
    --add-modules "$MODULES" \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=2 \
    --output /custom-jre && \
    echo "=== 自定义 JRE 大小 ===" && \
    du -sh /custom-jre

# ============================================================
# Stage 3: Runtime
# ============================================================
# 基础镜像：alpine + 自定义 JRE（~20-30MB vs eclipse-temurin 的 ~75MB）
# ------------------------------------------------------------
FROM alpine:3.20

# 版本号（构建时注入，用于运行时识别镜像版本）
ARG APP_VERSION=unknown
LABEL maintainer="Knowledge-Repository"
LABEL description="Knowledge Repository - RAG 知识库管理系统"
LABEL version="${APP_VERSION}"

# 从 jlink 阶段复制自定义 JRE
COPY --from=jlink /custom-jre /opt/java/openjdk

# 设置 Java 环境变量
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=$JAVA_HOME/bin:$PATH

# 创建非 root 用户（alpine 用 addgroup/adduser，无需 groupadd/useradd）
RUN addgroup -S appuser && adduser -S -G appuser -h /app appuser

# 运行时依赖：fontconfig（PDF/Office 中文渲染）+ tzdata + ca-certificates + curl（健康检查）
RUN apk add --no-cache fontconfig ttf-dejavu tzdata ca-certificates curl

WORKDIR /app

# 从构建阶段复制 fat JAR
# ⚠ 关键：COPY 时直接 --chown，避免后续 chown -R 触发 Overlay2 copy-up
#   把 122MB 的 jar 再存一遍到新层（这是之前 latest 比 flat 大 122MB 的元凶）。
COPY --chown=appuser:appuser --from=builder /build/knowledge-web/target/*.jar app.jar

# 创建数据目录（只建小目录，不 chown -R /app）
RUN install -d -o appuser -g appuser /app/data/documents

# 切换到非 root 用户
USER appuser

# JVM 参数（可通过 JAVA_OPTS 环境变量覆盖）
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"
ENV APP_VERSION=${APP_VERSION}

EXPOSE 8091

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
