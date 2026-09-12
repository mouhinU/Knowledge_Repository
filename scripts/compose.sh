#!/bin/bash
# Knowledge Repository Docker Compose 管理脚本
# 管理 Milvus 向量数据库依赖服务 (etcd + minio + milvus)
# 用法: ./compose.sh [up|down|status|logs|restart]

cd "$(dirname "$0")/.." || exit 1

ACTION=${1:-status}

case "$ACTION" in
    up)
        echo "启动 Milvus 服务栈..."
        docker-compose up -d
        echo ""
        echo "等待 Milvus 就绪..."
        for i in $(seq 1 30); do
            if curl -sf http://localhost:9091/healthz >/dev/null 2>&1; then
                echo "Milvus 已就绪"
                echo "  地址: localhost:19530"
                echo "  MinIO 控制台: http://localhost:9001"
                exit 0
            fi
            sleep 2
        done
        echo "启动超时，请检查: docker-compose logs milvus"
        ;;

    down)
        echo "停止 Milvus 服务栈..."
        docker-compose down
        echo "已停止"
        ;;

    restart)
        echo "重启 Milvus 服务栈..."
        docker-compose restart
        ;;

    status)
        echo "=== Milvus 服务状态 ==="
        docker-compose ps
        echo ""
        if curl -sf http://localhost:9091/healthz >/dev/null 2>&1; then
            echo "Milvus 健康检查: 正常"
        else
            echo "Milvus 健康检查: 未就绪"
        fi
        ;;

    logs)
        SERVICE=${2:-milvus}
        docker-compose logs -f --tail=100 "$SERVICE"
        ;;

    *)
        echo "用法: $0 [up|down|status|logs|restart]"
        echo ""
        echo "  up      - 启动 etcd + minio + milvus"
        echo "  down    - 停止所有服务"
        echo "  status  - 查看服务状态"
        echo "  logs    - 查看日志 (可选指定服务: logs milvus/minio/etcd)"
        echo "  restart - 重启所有服务"
        exit 1
        ;;
esac
