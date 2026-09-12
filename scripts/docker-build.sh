#!/bin/bash
# Knowledge Repository Docker 构建脚本

cd "$(dirname "$0")/.." || exit 1

IMAGE_NAME="knowledge-repository"
IMAGE_TAG="latest"

echo ">>> 编译项目..."
./mvnw clean package -DskipTests -q
if [ $? -ne 0 ]; then
    echo "编译失败"
    exit 1
fi

echo ">>> 构建 Docker 镜像: $IMAGE_NAME:$IMAGE_TAG"
docker build -t "$IMAGE_NAME:$IMAGE_TAG" -f docker/Dockerfile .

if [ $? -eq 0 ]; then
    echo "构建成功: $IMAGE_NAME:$IMAGE_TAG"
    echo ""
    echo "运行命令:"
    echo "  docker run -d -p 8091:8091 --name kb $IMAGE_NAME:$IMAGE_TAG"
else
    echo "构建失败"
    exit 1
fi
