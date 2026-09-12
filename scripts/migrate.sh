#!/bin/bash
# Knowledge Repository 数据迁移脚本
# 用法: ./scripts/migrate.sh [from|to|status]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 数据迁移工具"
echo "=========================================="
echo ""

ACTION=${1:-status}

case "$ACTION" in
    status)
        echo "=== 迁移状态 ==="
        echo ""
        
        # 检查 Flyway 迁移历史
        if [ -f "./data/knowledge-repository.mv.db" ]; then
            echo "数据库迁移历史:"
            # 这里可以通过 H2 Shell 查询 flyway_schema_history
            echo "  (需要通过 H2 Console 查看详细信息)"
            echo "  访问: http://localhost:8091/h2-console"
        else
            echo "数据库未初始化"
        fi
        
        echo ""
        echo "迁移脚本:"
        ls -la ./knowledge-web/src/main/resources/db/migration/ 2>/dev/null || echo "  无迁移脚本"
        ;;

    from)
        SOURCE=${2:-}
        
        if [ -z "$SOURCE" ]; then
            echo "用法: $0 from <源数据库路径>"
            echo ""
            echo "示例:"
            echo "  $0 from /backup/old-data/knowledge-repository.mv.db"
            exit 1
        fi
        
        if [ ! -f "$SOURCE" ]; then
            echo "源文件不存在: $SOURCE"
            exit 1
        fi
        
        echo ">>> 数据迁移"
        echo "源: $SOURCE"
        echo "目标: ./data/knowledge-repository.mv.db"
        echo ""
        
        # 检查服务状态
        PID=$(lsof -i :8091 -t 2>/dev/null)
        if [ -n "$PID" ]; then
            echo "请先停止服务: ./scripts/stop.sh"
            exit 1
        fi
        
        read -p "确认要覆盖当前数据吗? (y/N): " CONFIRM
        if [ "$CONFIRM" != "y" ]; then
            echo "已取消"
            exit 0
        fi
        
        # 备份当前数据
        if [ -f "./data/knowledge-repository.mv.db" ]; then
            echo "备份当前数据..."
            cp ./data/knowledge-repository.mv.db "./data/knowledge-repository.mv.db.backup.$(date +%Y%m%d%H%M%S)"
        fi
        
        # 复制源数据
        echo "复制数据..."
        cp "$SOURCE" ./data/knowledge-repository.mv.db
        
        echo "迁移完成"
        echo "启动服务后会自动执行必要的数据库迁移"
        ;;

    to)
        TARGET=${2:-}
        
        if [ -z "$TARGET" ]; then
            echo "用法: $0 to <目标路径>"
            echo ""
            echo "示例:"
            echo "  $0 to /backup/migration-export/"
            exit 1
        fi
        
        echo ">>> 导出数据到: $TARGET"
        
        mkdir -p "$TARGET"
        
        # 导出数据库
        if [ -f "./data/knowledge-repository.mv.db" ]; then
            cp ./data/knowledge-repository.mv.db "$TARGET/"
            echo "  数据库: 已导出"
        fi
        
        # 导出文档
        if [ -d "./data/documents" ]; then
            cp -r ./data/documents "$TARGET/"
            echo "  文档:   已导出"
        fi
        
        # 导出配置
        mkdir -p "$TARGET/config"
        cp ./knowledge-web/src/main/resources/application.yml "$TARGET/config/"
        echo "  配置:   已导出"
        
        echo ""
        echo "导出完成: $TARGET"
        ;;

    check)
        echo "=== 迁移兼容性检查 ==="
        echo ""
        
        # 检查版本
        echo "检查数据库版本..."
        if [ -f "./data/knowledge-repository.mv.db" ]; then
            echo "  数据库文件存在"
            SIZE=$(du -h ./data/knowledge-repository.mv.db | cut -f1)
            echo "  大小: $SIZE"
        else
            echo "  数据库文件不存在"
        fi
        
        echo ""
        echo "检查迁移脚本..."
        MIGRATION_COUNT=$(ls -1 ./knowledge-web/src/main/resources/db/migration/*.sql 2>/dev/null | wc -l | tr -d ' ')
        echo "  迁移脚本数量: $MIGRATION_COUNT"
        
        echo ""
        echo "检查表结构..."
        echo "  启动服务后访问 H2 Console 查看:"
        echo "  http://localhost:8091/h2-console"
        ;;

    *)
        echo "用法: $0 [status|from|to|check]"
        echo ""
        echo "  status          - 查看迁移状态"
        echo "  from <路径>     - 从外部数据库迁移"
        echo "  to <路径>       - 导出数据到外部"
        echo "  check           - 兼容性检查"
        exit 1
        ;;
esac
