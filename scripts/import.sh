#!/bin/bash
# Knowledge Repository 数据导入脚本
# 从导出的 JSON 文件恢复文档元数据

cd "$(dirname "$0")/.." || exit 1

if [ -z "$1" ]; then
    echo "用法: $0 <导出文件路径>"
    echo ""
    echo "示例: $0 ./data/exports/kb_export_20260910_120000.json"
    exit 1
fi

IMPORT_FILE="$1"

if [ ! -f "$IMPORT_FILE" ]; then
    echo "文件不存在: $IMPORT_FILE"
    exit 1
fi

PORT=8091

# 检查服务是否运行
if ! lsof -i :$PORT -t >/dev/null 2>&1; then
    echo "服务未运行，请先启动服务"
    exit 1
fi

echo "=========================================="
echo "  数据导入工具"
echo "=========================================="
echo ""
echo "导入文件: $IMPORT_FILE"

# 读取文件信息
EXPORT_TIME=$(grep -o '"exportTime":"[^"]*"' "$IMPORT_FILE" | cut -d'"' -f4)
DOC_COUNT=$(grep -o '"documentKey"' "$IMPORT_FILE" | wc -l | tr -d ' ')

echo "导出时间: $EXPORT_TIME"
echo "文档数量: $DOC_COUNT"
echo ""

read -p "确认要导入这些数据吗? (y/N): " CONFIRM
if [ "$CONFIRM" != "y" ] && [ "$CONFIRM" != "Y" ]; then
    echo "已取消"
    exit 0
fi

echo ""
echo "注意: 此脚本仅恢复文档元数据，不包含文件内容和向量数据"
echo "如需完整恢复，请配合 backup 备份的文件一起使用"
echo ""

# 这里可以根据需要实现具体的导入逻辑
# 目前仅作为框架，实际导入需要后端 API 支持

echo "导入功能需要后端 API 支持，当前版本仅提供框架"
echo "建议: 使用 backup.sh 进行完整数据备份和恢复"
