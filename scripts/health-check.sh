#!/bin/bash
# Knowledge Repository 健康检查脚本
# 检查服务及各依赖组件状态

PORT=8091
MILVUS_PORT=9091
OLLAMA_PORT=11434

echo "=== Knowledge Repository 健康检查 ==="
echo ""

# 1. 应用服务
PID=$(lsof -i :$PORT -t 2>/dev/null)
if [ -n "$PID" ]; then
    HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" "http://localhost:$PORT/admin.html" 2>/dev/null)
    if [ "$HTTP_CODE" = "200" ]; then
        echo "[OK] 应用服务    端口:$PORT  PID:$PID"
    else
        echo "[WARN] 应用服务    端口:$PORT  响应异常(HTTP $HTTP_CODE)"
    fi
else
    echo "[FAIL] 应用服务    端口:$PORT  未运行"
fi

# 2. Milvus
if curl -sf "http://localhost:$MILVUS_PORT/healthz" >/dev/null 2>&1; then
    echo "[OK] Milvus      端口:19530 (健康检查:$MILVUS_PORT)"
else
    echo "[FAIL] Milvus      端口:19530  未就绪"
fi

# 3. Ollama (Embedding 模型)
if curl -sf "http://localhost:$OLLAMA_PORT/api/tags" >/dev/null 2>&1; then
    MODEL=$(curl -sf "http://localhost:$OLLAMA_PORT/api/tags" 2>/dev/null | grep -o '"bge-m3"' | head -1)
    if [ -n "$MODEL" ]; then
        echo "[OK] Ollama      端口:$OLLAMA_PORT  模型:bge-m3"
    else
        echo "[WARN] Ollama      端口:$OLLAMA_PORT  运行中但缺少 bge-m3 模型"
    fi
else
    echo "[FAIL] Ollama      端口:$OLLAMA_PORT  未运行"
fi

# 4. H2 数据库文件
if [ -f "./data/knowledge-repository.mv.db" ]; then
    SIZE=$(du -h ./data/knowledge-repository.mv.db 2>/dev/null | cut -f1)
    echo "[OK] H2 数据库   大小:$SIZE"
else
    echo "[WARN] H2 数据库   文件不存在(首次启动时创建)"
fi

# 5. 文档存储目录
if [ -d "./data/documents" ]; then
    DOC_COUNT=$(find ./data/documents -type f 2>/dev/null | wc -l | tr -d ' ')
    echo "[OK] 文档存储   文件数:$DOC_COUNT"
else
    echo "[WARN] 文档存储   目录不存在"
fi

echo ""
echo "检查完成: $(date '+%Y-%m-%d %H:%M:%S')"
