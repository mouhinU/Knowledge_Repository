#!/bin/bash
# Knowledge Repository 服务启动脚本
# 端口: 8091

cd "$(dirname "$0")/.." || exit 1

PORT=8091
LOG_FILE=/tmp/kb-server.log

# 检查是否已在运行
PID=$(lsof -i :$PORT -t 2>/dev/null)
if [ -n "$PID" ]; then
    echo "服务已在运行中 (PID: $PID, 端口: $PORT)"
    exit 0
fi

echo "正在编译..."
./mvnw compile -DskipTests -q
if [ $? -ne 0 ]; then
    echo "编译失败，请检查代码"
    exit 1
fi

echo "正在启动服务..."
nohup ./mvnw spring-boot:run -pl knowledge-web -q > "$LOG_FILE" 2>&1 &
NEW_PID=$!

echo "服务启动中 (PID: $NEW_PID)，日志: $LOG_FILE"

# 等待服务就绪
for i in $(seq 1 30); do
    if lsof -i :$PORT -t >/dev/null 2>&1; then
        echo "服务已就绪 http://localhost:$PORT/admin.html"
        exit 0
    fi
    sleep 1
done

echo "服务启动超时，请检查日志: $LOG_FILE"
