#!/bin/bash
# Knowledge Repository 数据导出脚本
# 导出文档列表和元数据为 JSON

cd "$(dirname "$0")/.." || exit 1

OUTPUT_DIR="./data/exports"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
OUTPUT_FILE="$OUTPUT_DIR/kb_export_$TIMESTAMP.json"

mkdir -p "$OUTPUT_DIR"

PORT=8091

# 检查服务是否运行
if ! lsof -i :$PORT -t >/dev/null 2>&1; then
    echo "服务未运行，请先启动服务"
    exit 1
fi

echo "正在导出文档数据..."

# 获取文档列表
DOCS=$(curl -sf "http://localhost:$PORT/api/admin/document/list" 2>/dev/null)

if [ -z "$DOCS" ]; then
    echo "获取文档列表失败"
    exit 1
fi

# 获取统计信息
STATS=$(curl -sf "http://localhost:$PORT/api/admin/document/stats" 2>/dev/null)

# 组合导出
cat > "$OUTPUT_FILE" <<EOF
{
  "exportTime": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "stats": $STATS,
  "documents": $DOCS
}
EOF

DOC_COUNT=$(echo "$DOCS" | grep -o '"documentKey"' | wc -l | tr -d ' ')

echo "导出完成"
echo "  文件: $OUTPUT_FILE"
echo "  文档: $DOC_COUNT 条"
echo "  大小: $(du -h "$OUTPUT_FILE" | cut -f1)"
