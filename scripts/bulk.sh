#!/bin/bash
# Knowledge Repository 批量操作脚本
# 用法: ./scripts/bulk.sh [index|delete|export|reindex|status]

cd "$(dirname "$0")/.." || exit 1

PORT=8091

echo "=========================================="
echo "  Knowledge Repository 批量操作"
echo "=========================================="
echo ""

ACTION=${1:-status}

case "$ACTION" in
    status)
        echo "=== 批量操作状态 ==="
        echo ""
        
        if ! lsof -i :$PORT -t >/dev/null 2>&1; then
            echo "服务未运行"
            exit 1
        fi
        
        echo "文档统计:"
        curl -sf "http://localhost:$PORT/api/admin/document/stats" 2>/dev/null | python3 -m json.tool
        ;;

    index)
        echo "=== 批量索引 ==="
        echo ""
        
        if [ -z "$2" ]; then
            echo "用法: $0 index <目录路径>"
            echo ""
            echo "批量索引目录中的所有文档"
            exit 1
        fi
        
        DIR=$2
        
        if [ ! -d "$DIR" ]; then
            echo "目录不存在: $DIR"
            exit 1
        fi
        
        # 查找所有支持的文件
        FILES=$(find "$DIR" -type f \( -name "*.pdf" -o -name "*.docx" -o -name "*.xlsx" -o -name "*.pptx" -o -name "*.txt" \))
        COUNT=$(echo "$FILES" | wc -l | tr -d ' ')
        
        echo "发现 $COUNT 个文档"
        echo ""
        
        read -p "确认批量索引? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        SUCCESS=0
        FAILED=0
        
        for FILE in $FILES; do
            echo -n "索引: $(basename "$FILE") ... "
            
            RESULT=$(curl -sf -X POST "http://localhost:$PORT/api/document/upload" \
                -F "file=@$FILE" \
                -F "ownerId=admin" \
                -F "departmentId=default" 2>/dev/null)
            
            if [ -n "$RESULT" ]; then
                echo "成功"
                ((SUCCESS++))
            else
                echo "失败"
                ((FAILED++))
            fi
        done
        
        echo ""
        echo "批量索引完成: 成功 $SUCCESS, 失败 $FAILED"
        ;;

    delete)
        echo "=== 批量删除 ==="
        echo ""
        
        if [ -z "$2" ]; then
            echo "用法: $0 delete <状态|all>"
            echo ""
            echo "状态: FAILED, UPLOADED, INDEXED"
            exit 1
        fi
        
        STATUS=$2
        
        if [ "$STATUS" = "all" ]; then
            read -p "确认删除所有文档? 此操作不可恢复! (输入 YES 确认): " CONFIRM
            if [ "$CONFIRM" != "YES" ]; then
                echo "已取消"
                exit 0
            fi
            
            DOCS=$(curl -sf "http://localhost:$PORT/api/admin/document/list" 2>/dev/null)
        else
            read -p "确认删除所有 $STATUS 状态的文档? (y/N): " CONFIRM
            if [ "$CONFIRM" != "y" ]; then
                echo "已取消"
                exit 0
            fi
            
            DOCS=$(curl -sf "http://localhost:$PORT/api/admin/document/status/$STATUS" 2>/dev/null)
        fi
        
        if [ -z "$DOCS" ]; then
            echo "无文档可删除"
            exit 0
        fi
        
        # 提取 documentKey 并删除
        KEYS=$(echo "$DOCS" | grep -o '"documentKey":"[^"]*"' | cut -d'"' -f4)
        COUNT=$(echo "$KEYS" | wc -l | tr -d ' ')
        
        echo "将删除 $COUNT 个文档"
        
        SUCCESS=0
        for KEY in $KEYS; do
            curl -sf -X DELETE "http://localhost:$PORT/api/admin/document/$KEY" >/dev/null 2>&1
            ((SUCCESS++))
        done
        
        echo "删除完成: $SUCCESS 个文档"
        ;;

    export)
        echo "=== 批量导出 ==="
        echo ""
        
        if [ -z "$2" ]; then
            echo "用法: $0 export <输出目录>"
            exit 1
        fi
        
        OUTPUT_DIR=$2
        mkdir -p "$OUTPUT_DIR"
        
        echo "导出文档元数据..."
        
        # 导出文档列表
        curl -sf "http://localhost:$PORT/api/admin/document/list" 2>/dev/null > "$OUTPUT_DIR/documents.json"
        
        # 导出统计
        curl -sf "http://localhost:$PORT/api/admin/document/stats" 2>/dev/null > "$OUTPUT_DIR/stats.json"
        
        echo "导出完成: $OUTPUT_DIR"
        ;;

    reindex)
        echo "=== 批量重新索引 ==="
        echo ""
        
        read -p "确认重新索引所有文档? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        DOCS=$(curl -sf "http://localhost:$PORT/api/admin/document/list" 2>/dev/null)
        
        if [ -z "$DOCS" ]; then
            echo "无文档"
            exit 0
        fi
        
        KEYS=$(echo "$DOCS" | grep -o '"documentKey":"[^"]*"' | cut -d'"' -f4)
        COUNT=$(echo "$KEYS" | wc -l | tr -d ' ')
        
        echo "将重新索引 $COUNT 个文档"
        
        SUCCESS=0
        for KEY in $KEYS; do
            echo -n "重新索引: $KEY ... "
            RESULT=$(curl -sf -X POST "http://localhost:$PORT/api/admin/document/$KEY/reindex" 2>/dev/null)
            if [ -n "$RESULT" ]; then
                echo "成功"
                ((SUCCESS++))
            else
                echo "失败"
            fi
        done
        
        echo ""
        echo "批量重新索引完成: $SUCCESS 个文档"
        ;;

    *)
        echo "用法: $0 [status|index|delete|export|reindex]"
        echo ""
        echo "  status              - 查看状态"
        echo "  index <目录>        - 批量索引目录中的文件"
        echo "  delete <状态|all>   - 批量删除文档"
        echo "  export <目录>       - 批量导出元数据"
        echo "  reindex             - 批量重新索引"
        exit 1
        ;;
esac
