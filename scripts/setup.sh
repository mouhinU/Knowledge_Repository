#!/bin/bash
# Knowledge Repository 初始化设置脚本
# 用法: ./scripts/setup.sh

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 初始化设置"
echo "=========================================="
echo ""

# 1. 设置脚本执行权限
echo ">>> 设置脚本权限..."
chmod +x scripts/*.sh
echo "    完成"

# 2. 创建必要目录
echo ""
echo ">>> 创建数据目录..."
mkdir -p data/backups
mkdir -p data/exports
mkdir -p data/reports
mkdir -p data/dumps
mkdir -p data/diagnostics
mkdir -p data/documents
echo "    完成"

# 3. 检查环境
echo ""
echo ">>> 检查运行环境..."
./scripts/env-check.sh

# 4. 配置别名（可选）
echo ""
read -p "是否配置快捷命令别名? (y/N): " SETUP_ALIAS
if [ "$SETUP_ALIAS" = "y" ] || [ "$SETUP_ALIAS" = "Y" ]; then
    SHELL_RC=""
    if [ -f "$HOME/.bashrc" ]; then
        SHELL_RC="$HOME/.bashrc"
    elif [ -f "$HOME/.zshrc" ]; then
        SHELL_RC="$HOME/.zshrc"
    fi
    
    if [ -n "$SHELL_RC" ]; then
        ALIAS_LINE="source $(pwd)/scripts/alias.sh"
        if ! grep -q "scripts/alias.sh" "$SHELL_RC" 2>/dev/null; then
            echo "" >> "$SHELL_RC"
            echo "# Knowledge Repository 快捷命令" >> "$SHELL_RC"
            echo "$ALIAS_LINE" >> "$SHELL_RC"
            echo "    已添加到 $SHELL_RC"
        else
            echo "    别名已存在"
        fi
    else
        echo "    未找到 shell 配置文件，请手动添加:"
        echo "    source $(pwd)/scripts/alias.sh"
    fi
fi

# 5. 配置自动补全（可选）
echo ""
read -p "是否配置命令自动补全? (y/N): " SETUP_COMPLETE
if [ "$SETUP_COMPLETE" = "y" ] || [ "$SETUP_COMPLETE" = "Y" ]; then
    SHELL_RC=""
    if [ -f "$HOME/.bashrc" ]; then
        SHELL_RC="$HOME/.bashrc"
    elif [ -f "$HOME/.zshrc" ]; then
        SHELL_RC="$HOME/.zshrc"
    fi
    
    if [ -n "$SHELL_RC" ]; then
        COMPLETE_LINE="source $(pwd)/scripts/completion.sh"
        if ! grep -q "scripts/completion.sh" "$SHELL_RC" 2>/dev/null; then
            echo "" >> "$SHELL_RC"
            echo "# Knowledge Repository 自动补全" >> "$SHELL_RC"
            echo "$COMPLETE_LINE" >> "$SHELL_RC"
            echo "    已添加到 $SHELL_RC"
        else
            echo "    自动补全已存在"
        fi
    fi
fi

# 6. 完成
echo ""
echo "=========================================="
echo "  设置完成"
echo "=========================================="
echo ""
echo "下一步:"
echo "  1. 启动 Milvus:  ./scripts/compose.sh up"
echo "  2. 启动服务:     ./scripts/start.sh"
echo "  3. 访问控制台:   http://localhost:8091/admin.html"
echo ""
echo "或使用一键部署:    ./scripts/deploy.sh"
echo ""

if [ "$SETUP_ALIAS" = "y" ] || [ "$SETUP_COMPLETE" = "y" ]; then
    echo "请重新加载 shell 配置:"
    echo "  source ~/.bashrc  或  source ~/.zshrc"
    echo ""
fi
