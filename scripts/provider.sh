#!/bin/bash
# Knowledge Repository 提供商管理脚本
# 用法: ./scripts/provider.sh [list|config|test|switch]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 提供商管理"
echo "=========================================="
echo ""

ACTION=${1:-list}

case "$ACTION" in
    list)
        echo "=== 服务提供商 ==="
        echo ""
        
        echo "Embedding 模型提供商:"
        echo ""
        echo "  ● Ollama (当前)"
        echo "    模型: bge-m3"
        echo "    维度: 1024"
        echo "    端点: http://localhost:11434/v1"
        echo ""
        echo "  ○ DashScope"
        echo "    模型: text-embedding-v3"
        echo "    维度: 1024"
        echo "    端点: https://dashscope.aliyuncs.com"
        echo ""
        echo "  ○ OpenAI"
        echo "    模型: text-embedding-3-small"
        echo "    维度: 1536"
        echo "    端点: https://api.openai.com/v1"
        echo ""
        
        echo "向量数据库提供商:"
        echo ""
        echo "  ● Milvus (当前)"
        echo "    版本: 2.5.4"
        echo "    端点: localhost:19530"
        echo ""
        echo "  ○ Pinecone"
        echo "    云原生向量数据库"
        echo ""
        echo "  ○ Weaviate"
        echo "    开源向量数据库"
        ;;

    config)
        echo "=== 当前配置 ==="
        echo ""
        
        echo "Embedding 提供商:"
        grep -A10 "embedding:" ./knowledge-web/src/main/resources/application.yml 2>/dev/null | head -12
        echo ""
        
        echo "Milvus 配置:"
        grep -A5 "milvus:" ./knowledge-web/src/main/resources/application.yml 2>/dev/null | head -6
        ;;

    test)
        if [ -z "$2" ]; then
            echo "用法: $0 test <提供商名称>"
            exit 1
        fi
        
        PROVIDER=$2
        
        echo "测试提供商: $PROVIDER"
        echo ""
        
        case "$PROVIDER" in
            ollama)
                echo -n "连接测试: "
                if curl -sf "http://localhost:11434/api/tags" >/dev/null 2>&1; then
                    echo "● 成功"
                else
                    echo "○ 失败"
                fi
                
                echo -n "模型测试: "
                RESULT=$(curl -sf "http://localhost:11434/api/embeddings" \
                    -d '{"model":"bge-m3","prompt":"测试"}' 2>/dev/null)
                if [ -n "$RESULT" ]; then
                    echo "● bge-m3 可用"
                else
                    echo "○ bge-m3 不可用"
                fi
                ;;
            dashscope)
                echo "DashScope 需要配置 API Key"
                echo "  export DASHSCOPE_API_KEY=your-key"
                ;;
            milvus)
                echo -n "连接测试: "
                if curl -sf "http://localhost:9091/healthz" >/dev/null 2>&1; then
                    echo "● 成功"
                else
                    echo "○ 失败"
                fi
                ;;
            *)
                echo "未知提供商: $PROVIDER"
                ;;
        esac
        ;;

    switch)
        if [ -z "$2" ]; then
            echo "用法: $0 switch <提供商名称>"
            echo ""
            echo "可用: ollama, dashscope"
            exit 1
        fi
        
        PROVIDER=$2
        
        echo "切换 Embedding 提供商到: $PROVIDER"
        echo ""
        
        case "$PROVIDER" in
            ollama)
                echo "切换到 Ollama..."
                echo ""
                echo "请修改 application.yml:"
                echo "  llm:"
                echo "    embedding:"
                echo "      provider: ollama"
                echo "      base-url: http://localhost:11434/v1"
                echo "      model-name: bge-m3"
                echo "      api-key: ollama"
                ;;
            dashscope)
                echo "切换到 DashScope..."
                echo ""
                echo "请修改 application.yml:"
                echo "  llm:"
                echo "    embedding:"
                echo "      provider: dashscope"
                echo "      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1"
                echo "      model-name: text-embedding-v3"
                echo "      api-key: \${DASHSCOPE_API_KEY}"
                ;;
            *)
                echo "未知提供商: $PROVIDER"
                ;;
        esac
        
        echo ""
        echo "注意: 切换提供商后需要重建向量索引"
        ;;

    compare)
        echo "=== 提供商对比 ==="
        echo ""
        
        printf "%-15s %-15s %-15s %-15s\n" "提供商" "速度" "成本" "质量"
        echo "------------------------------------------------------"
        printf "%-15s %-15s %-15s %-15s\n" "Ollama" "快" "免费" "良好"
        printf "%-15s %-15s %-15s %-15s\n" "DashScope" "中" "低" "优秀"
        printf "%-15s %-15s %-15s %-15s\n" "OpenAI" "快" "中" "优秀"
        ;;

    *)
        echo "用法: $0 [list|config|test|switch|compare]"
        echo ""
        echo "  list              - 列出提供商"
        echo "  config            - 查看配置"
        echo "  test <名称>       - 测试提供商"
        echo "  switch <名称>     - 切换提供商"
        echo "  compare           - 对比提供商"
        exit 1
        ;;
esac
