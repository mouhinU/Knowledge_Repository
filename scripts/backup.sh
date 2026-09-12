#!/bin/bash
# Knowledge Repository 数据备份脚本
# 备份内容: H2数据库文件 + 上传文档

cd "$(dirname "$0")/.." || exit 1

BACKUP_DIR="./data/backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_NAME="kb_backup_$TIMESTAMP"
BACKUP_PATH="$BACKUP_DIR/$BACKUP_NAME"

mkdir -p "$BACKUP_PATH"

echo "开始备份..."

# 1. 备份 H2 数据库文件
if [ -d "./data" ]; then
    H2_FILES=$(find ./data -name "knowledge-repository*.db*" 2>/dev/null)
    if [ -n "$H2_FILES" ]; then
        mkdir -p "$BACKUP_PATH/database"
        cp ./data/knowledge-repository*.db* "$BACKUP_PATH/database/" 2>/dev/null
        echo "  数据库: 已备份"
    else
        echo "  数据库: 未找到H2文件，跳过"
    fi
fi

# 2. 备份上传文档
if [ -d "./data/documents" ]; then
    mkdir -p "$BACKUP_PATH/documents"
    cp -r ./data/documents/* "$BACKUP_PATH/documents/" 2>/dev/null
    DOC_COUNT=$(find "$BACKUP_PATH/documents" -type f | wc -l | tr -d ' ')
    echo "  文档:   $DOC_COUNT 个文件"
else
    echo "  文档:   目录不存在，跳过"
fi

# 3. 备份配置文件
if [ -f "./knowledge-web/src/main/resources/application.yml" ]; then
    mkdir -p "$BACKUP_PATH/config"
    cp ./knowledge-web/src/main/resources/application.yml "$BACKUP_PATH/config/"
    echo "  配置:   已备份"
fi

# 4. 记录备份信息
cat > "$BACKUP_PATH/backup_info.txt" <<EOF
备份时间: $(date '+%Y-%m-%d %H:%M:%S')
服务端口: 8091
备份内容:
  - H2 数据库文件
  - 上传文档
  - 配置文件
EOF

# 5. 压缩备份
cd "$BACKUP_DIR" && tar -czf "${BACKUP_NAME}.tar.gz" "$BACKUP_NAME" && rm -rf "$BACKUP_NAME"
echo ""
echo "备份完成: $BACKUP_DIR/${BACKUP_NAME}.tar.gz"

# 保留最近10个备份，清理旧的
BACKUP_COUNT=$(ls -1 kb_backup_*.tar.gz 2>/dev/null | wc -l | tr -d ' ')
if [ "$BACKUP_COUNT" -gt 10 ]; then
    ls -1t kb_backup_*.tar.gz | tail -n +11 | xargs rm -f
    echo "已清理旧备份，保留最近 10 个"
fi
