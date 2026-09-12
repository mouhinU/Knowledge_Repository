#!/bin/bash
# Knowledge Repository 磁盘使用分析脚本

cd "$(dirname "$0")/.." || exit 1

echo "=== Knowledge Repository 磁盘使用分析 ==="
echo ""

# 1. 总体使用
echo "--- 总体使用 ---"
if [ -d "./data" ]; then
    TOTAL_SIZE=$(du -sh ./data 2>/dev/null | cut -f1)
    echo "data/ 目录: $TOTAL_SIZE"
else
    echo "data/ 目录不存在"
fi
echo ""

# 2. 分类统计
echo "--- 分类统计 ---"

# H2 数据库
if [ -f "./data/knowledge-repository.mv.db" ]; then
    DB_SIZE=$(du -sh ./data/knowledge-repository.mv.db 2>/dev/null | cut -f1)
    echo "H2 数据库:   $DB_SIZE"
else
    echo "H2 数据库:   0"
fi

# 上传文档
if [ -d "./data/documents" ]; then
    DOC_SIZE=$(du -sh ./data/documents 2>/dev/null | cut -f1)
    DOC_COUNT=$(find ./data/documents -type f 2>/dev/null | wc -l | tr -d ' ')
    echo "上传文档:    $DOC_SIZE ($DOC_COUNT 个文件)"
else
    echo "上传文档:    0"
fi

# 备份文件
if [ -d "./data/backups" ]; then
    BACKUP_SIZE=$(du -sh ./data/backups 2>/dev/null | cut -f1)
    BACKUP_COUNT=$(ls -1 ./data/backups/*.tar.gz 2>/dev/null | wc -l | tr -d ' ')
    echo "备份文件:    $BACKUP_SIZE ($BACKUP_COUNT 个)"
else
    echo "备份文件:    0"
fi

# 导出文件
if [ -d "./data/exports" ]; then
    EXPORT_SIZE=$(du -sh ./data/exports 2>/dev/null | cut -f1)
    echo "导出文件:    $EXPORT_SIZE"
else
    echo "导出文件:    0"
fi

echo ""

# 3. 大文件列表
echo "--- 最大的 10 个文件 ---"
find ./data -type f -exec du -h {} + 2>/dev/null | sort -rh | head -10

echo ""

# 4. 清理建议
echo "--- 清理建议 ---"
if [ -d "./data/backups" ]; then
    OLD_BACKUPS=$(find ./data/backups -name "*.tar.gz" -mtime +30 2>/dev/null | wc -l | tr -d ' ')
    if [ "$OLD_BACKUPS" -gt 0 ]; then
        echo "发现 $OLD_BACKUPS 个超过 30 天的旧备份，可考虑清理"
        echo "  手动清理: rm ./data/backups/*.tar.gz"
    fi
fi

if [ -f "/tmp/kb-server.log" ]; then
    LOG_SIZE=$(du -sh /tmp/kb-server.log 2>/dev/null | cut -f1)
    echo "日志文件: $LOG_SIZE (/tmp/kb-server.log)"
fi
