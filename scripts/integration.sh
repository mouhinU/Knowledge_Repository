#!/bin/bash
# Knowledge Repository 集成管理脚本
# 用法: ./scripts/integration.sh [list|test|config|status]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 集成管理"
echo "=========================================="
echo ""

ACTION=${1:-list}

case "$ACTION" in
    list)
        echo "=== 可用集成 ==="
        echo ""
        
        echo "向量数据库:"
        echo "  ● Milvus 2.5.4        - 已配置"
        echo ""
        
        echo "Embedding 模型:"
        echo "  ● Ollama bge-m3       - 本地模型"
        echo "  ○ DashScope           - 阿里云 (需配置)"
        echo ""
        
        echo "文档解析:"
        echo "  ● PDFBox              - PDF 解析"
        echo "  ● Apache POI          - Office 文档"
        echo "  ● Apache Tika         - 通用解析"
        echo ""
        
        echo "通知渠道:"
        echo "  ○ Email               - 需配置"
        echo "  ○ DingTalk            - 需配置"
        echo "  ○ Feishu              - 需配置"
        echo "  ○ Webhook             - 可配置"
        echo ""
        
        echo "存储:"
        echo "  ● 本地文件系统         - 默认"
        echo "  ○ MinIO               - 对象存储"
        echo "  ○ AWS S3              - 云存储"
        ;;

    test)
        echo "=== 集成测试 ==="
        echo ""
        
        # Milvus
        echo -n "Milvus: "
        if curl -sf "http://localhost:9091/healthz" >/dev/null 2>&1; then
            echo "● 连接正常"
        else
            echo "○ 连接失败"
        fi
        
        # Ollama
        echo -n "Ollama: "
        if curl -sf "http://localhost:11434/api/tags" >/dev/null 2>&1; then
            echo "● 连接正常"
            # 检查模型
            MODEL=$(curl -sf "http://localhost:11434/api/tags" 2>/dev/null | grep -o '"bge-m3"' | head -1)
            if [ -n "$MODEL" ]; then
                echo "       模型 bge-m3: 已安装"
            else
                echo "       模型 bge-m3: 未安装"
            fi
        else
            echo "○ 连接失败"
        fi
        
        # 应用服务
        echo -n "应用服务: "
        if lsof -i :8091 -t >/dev/null 2>&1; then
            echo "● 运行中"
        else
            echo "○ 未运行"
        fi
        
        # H2 数据库
        echo -n "H2 数据库: "
        if [ -f "./data/knowledge-repository.mv.db" ]; then
            SIZE=$(du -h ./data/knowledge-repository.mv.db | cut -f1)
            echo "● 存在 ($SIZE)"
        else
            echo "○ 不存在"
        fi
        ;;

    config)
        echo "=== 集成配置 ==="
        echo ""
        
        echo "当前配置 (application.yml):"
        echo ""
        
        # Milvus 配置
        echo "--- Milvus ---"
        grep -A3 "milvus:" ./knowledge-web/src/main/resources/application.yml 2>/dev/null | head -5
        echo ""
        
        # Embedding 配置
        echo "--- Embedding ---"
        grep -A5 "embedding:" ./knowledge-web/src/main/resources/application.yml 2>/dev/null | head -7
        echo ""
        
        # 存储配置
        echo "--- 存储 ---"
        grep -A2 "storage:" ./knowledge-web/src/main/resources/application.yml 2>/dev/null | head -3
        ;;

    status)
        echo "=== 集成状态 ==="
        echo ""
        
        # 统计信息
        echo "向量数据库统计:"
        if curl -sf "http://localhost:9091/healthz" >/dev/null 2>&1; then
            echo "  Milvus: 健康"
            # 这里可以添加更多 Milvus 统计
        else
            echo "  Milvus: 未就绪"
        fi
        
        echo ""
        echo "文档统计:"
        if lsof -i :8091 -t >/dev/null 2>&1; then
            curl -sf "http://localhost:8091/api/admin/document/stats" 2>/dev/null | python3 -m json.tool
        else
            echo "  服务未运行"
        fi
        
        echo ""
        echo "Embedding 模型:"
        if curl -sf "http://localhost:11434/api/tags" >/dev/null 2>&1; then
            echo "  Ollama: 运行中"
            echo "  模型: bge-m3 (1024维)"
        else
            echo "  Ollama: 未运行"
        fi
        ;;

    setup)
        echo "=== 集成设置向导 ==="
        echo ""
        
        echo "1. 启动 Milvus"
        echo "   ./scripts/compose.sh up"
        echo ""
        
        echo "2. 启动 Ollama 并拉取模型"
        echo "   ollama serve"
        echo "   ollama pull bge-m3"
        echo ""
        
        echo "3. 启动应用服务"
        echo "   ./scripts/start.sh"
        echo ""
        
        echo "4. 验证集成"
        echo "   ./scripts/integration.sh test"
        ;;

    *)
        echo "用法: $0 [list|test|config|status|setup]"
        echo ""
        echo "  list      - 列出可用集成"
        echo "  test      - 测试集成连接"
        echo "  config    - 查看配置"
        echo "  status    - 集成状态"
        echo "  setup     - 设置向导"
        exit 1
        ;;
esac
