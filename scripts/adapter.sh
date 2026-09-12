#!/bin/bash
# Knowledge Repository 适配器管理脚本
# 用法: ./scripts/adapter.sh [list|test|config|status]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 适配器管理"
echo "=========================================="
echo ""

ACTION=${1:-list}

case "$ACTION" in
    list)
        echo "=== 适配器列表 ==="
        echo ""
        
        echo "文档解析适配器:"
        echo "  ● PDFAdapter        - PDF 文档解析"
        echo "  ● WordAdapter       - Word 文档解析"
        echo "  ● ExcelAdapter      - Excel 表格解析"
        echo "  ● PPTAdapter        - PowerPoint 解析"
        echo "  ● TextAdapter       - 纯文本解析"
        echo "  ● HTMLAdapter       - HTML 页面解析"
        echo ""
        
        echo "分块策略适配器:"
        echo "  ● FixedSizeAdapter  - 固定大小分块"
        echo "  ● RecursiveAdapter  - 递归分块"
        echo "  ● SentenceAdapter   - 按句子分块"
        echo "  ● PageAdapter       - 按页面分块"
        echo "  ● ParagraphAdapter  - 按段落分块"
        echo ""
        
        echo "Embedding 适配器:"
        echo "  ● OllamaAdapter     - Ollama 本地模型"
        echo "  ● DashScopeAdapter  - 阿里云 DashScope"
        echo "  ● OpenAIAdapter     - OpenAI API"
        echo ""
        
        echo "存储适配器:"
        echo "  ● LocalAdapter      - 本地文件存储"
        echo "  ● MinIOAdapter      - MinIO 对象存储"
        echo "  ● S3Adapter         - AWS S3 存储"
        ;;

    test)
        if [ -z "$2" ]; then
            echo "用法: $0 test <适配器名称>"
            exit 1
        fi
        
        NAME=$2
        
        echo "测试适配器: $NAME"
        echo ""
        
        case "$NAME" in
            PDFAdapter|pdf)
                echo "PDF 适配器测试:"
                echo "  检查 PDFBox 依赖..."
                if ./mvnw dependency:tree 2>/dev/null | grep -q "pdfbox"; then
                    echo "  ● PDFBox 已安装"
                else
                    echo "  ○ PDFBox 未找到"
                fi
                ;;
            OllamaAdapter|ollama)
                echo "Ollama 适配器测试:"
                if curl -sf "http://localhost:11434/api/tags" >/dev/null 2>&1; then
                    echo "  ● Ollama 连接正常"
                else
                    echo "  ○ Ollama 连接失败"
                fi
                ;;
            *)
                echo "适配器: $NAME"
                echo "  状态: 可用 (模拟)"
                ;;
        esac
        ;;

    config)
        echo "=== 适配器配置 ==="
        echo ""
        
        echo "文档解析配置:"
        grep -A2 "multipart:" ./knowledge-web/src/main/resources/application.yml 2>/dev/null | head -3
        echo ""
        
        echo "Embedding 配置:"
        grep -A10 "embedding:" ./knowledge-web/src/main/resources/application.yml 2>/dev/null | head -12
        echo ""
        
        echo "Milvus 配置:"
        grep -A5 "milvus:" ./knowledge-web/src/main/resources/application.yml 2>/dev/null | head -6
        ;;

    status)
        echo "=== 适配器状态 ==="
        echo ""
        
        echo "文档解析:"
        echo "  支持格式: PDF, Word, Excel, PPT, TXT, CSV, HTML"
        echo "  文件大小限制: 200MB"
        echo ""
        
        echo "分块策略:"
        echo "  默认策略: FIXED_SIZE"
        echo "  默认大小: 500 tokens"
        echo "  默认重叠: 50 tokens"
        echo ""
        
        echo "Embedding:"
        echo "  当前模型: bge-m3"
        echo "  向量维度: 1024"
        echo ""
        
        echo "向量存储:"
        if curl -sf "http://localhost:9091/healthz" >/dev/null 2>&1; then
            echo "  Milvus: 健康"
        else
            echo "  Milvus: 未就绪"
        fi
        ;;

    benchmark)
        echo "=== 适配器性能测试 ==="
        echo ""
        
        echo "测试 Embedding 性能..."
        
        # 测试 Ollama 响应时间
        START=$(date +%s%N)
        curl -sf "http://localhost:11434/api/embeddings" \
            -d '{"model":"bge-m3","prompt":"测试文本"}' >/dev/null 2>&1
        END=$(date +%s%N)
        
        ELAPSED=$(( (END - START) / 1000000 ))
        echo "  Ollama Embedding: ${ELAPSED}ms"
        ;;

    *)
        echo "用法: $0 [list|test|config|status|benchmark]"
        echo ""
        echo "  list              - 列出适配器"
        echo "  test <名称>       - 测试适配器"
        echo "  config            - 查看配置"
        echo "  status            - 查看状态"
        echo "  benchmark         - 性能测试"
        exit 1
        ;;
esac
