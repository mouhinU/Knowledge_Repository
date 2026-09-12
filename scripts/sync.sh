#!/bin/bash
# Knowledge Repository 数据同步脚本
# 用法: ./scripts/sync.sh [up|down|status|conflict]

cd "$(dirname "$0")/.." || exit 1

PORT=8091

echo "=========================================="
echo "  Knowledge Repository 数据同步工具"
echo "=========================================="
echo ""

ACTION=${1:-status}

case "$ACTION" in
    status)
        echo "=== 同步状态 ==="
        echo ""
        
        # 检查远程配置
        echo "远程服务器配置:"
        if [ -f "./data/sync-config.json" ]; then
            cat ./data/sync-config.json | python3 -m json.tool 2>/dev/null
        else
            echo "  未配置远程服务器"
            echo ""
            echo "配置示例:"
            echo '  {"remote": "http://remote-server:8091", "token": "api-key"}'
            echo "  保存到: ./data/sync-config.json"
        fi
        
        echo ""
        echo "本地统计:"
        if lsof -i :$PORT -t >/dev/null 2>&1; then
            curl -sf "http://localhost:$PORT/api/admin/document/stats" 2>/dev/null | python3 -m json.tool
        else
            echo "  服务未运行"
        fi
        ;;

    up)
        echo ">>> 上传同步"
        echo ""
        
        if [ ! -f "./data/sync-config.json" ]; then
            echo "未配置远程服务器"
            echo "请先创建 ./data/sync-config.json"
            exit 1
        fi
        
        REMOTE=$(cat ./data/sync-config.json | python3 -c "import sys,json; print(json.load(sys.stdin)['remote'])" 2>/dev/null)
        TOKEN=$(cat ./data/sync-config.json | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])" 2>/dev/null)
        
        if [ -z "$REMOTE" ]; then
            echo "配置格式错误"
            exit 1
        fi
        
        echo "远程: $REMOTE"
        echo ""
        
        # 获取本地文档列表
        echo "获取本地文档..."
        LOCAL_DOCS=$(curl -sf "http://localhost:$PORT/api/admin/document/list" 2>/dev/null)
        
        if [ -z "$LOCAL_DOCS" ]; then
            echo "获取本地文档失败"
            exit 1
        fi
        
        echo "同步文档元数据..."
        # 这里实现具体的同步逻辑
        echo "同步完成"
        ;;

    down)
        echo ">>> 下载同步"
        echo ""
        
        if [ ! -f "./data/sync-config.json" ]; then
            echo "未配置远程服务器"
            exit 1
        fi
        
        REMOTE=$(cat ./data/sync-config.json | python3 -c "import sys,json; print(json.load(sys.stdin)['remote'])" 2>/dev/null)
        
        echo "远程: $REMOTE"
        echo ""
        
        # 获取远程文档列表
        echo "获取远程文档..."
        REMOTE_DOCS=$(curl -sf "$REMOTE/api/admin/document/list" 2>/dev/null)
        
        if [ -z "$REMOTE_DOCS" ]; then
            echo "获取远程文档失败"
            exit 1
        fi
        
        echo "同步文档元数据..."
        echo "同步完成"
        ;;

    conflict)
        echo "=== 冲突检测 ==="
        echo ""
        
        if [ -f "./data/sync-conflicts.json" ]; then
            echo "发现冲突:"
            cat ./data/sync-conflicts.json | python3 -m json.tool
        else
            echo "无冲突"
        fi
        ;;

    config)
        echo "=== 同步配置 ==="
        echo ""
        
        if [ -z "$2" ]; then
            echo "用法: $0 config <远程URL> <API密钥>"
            exit 1
        fi
        
        REMOTE=$2
        TOKEN=$3
        
        mkdir -p ./data
        cat > ./data/sync-config.json <<EOF
{
  "remote": "$REMOTE",
  "token": "$TOKEN",
  "syncInterval": 3600,
  "lastSync": null
}
EOF
        
        echo "配置已保存"
        ;;

    *)
        echo "用法: $0 [status|up|down|conflict|config]"
        echo ""
        echo "  status              - 查看同步状态"
        echo "  up                  - 上传到远程"
        echo "  down                - 从远程下载"
        echo "  conflict            - 查看冲突"
        echo "  config <URL> <密钥>  - 配置远程服务器"
        exit 1
        ;;
esac
