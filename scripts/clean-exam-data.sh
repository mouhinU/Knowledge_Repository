#!/usr/bin/env bash
#
# clean_exam_data.sh — 清理 AI试卷 / 考试记录 / 错题本 数据（Knowledge_Repository）
#
# 清理对象（4 张表，子表→父表顺序）：
#   kb_exam_answer    答题明细（错题本由此表派生：is_correct=false / review_score<max_score）
#   kb_exam_session   学生考试场次记录（含成绩）
#   kb_exam_question  试卷拆分题目（按 session_key 关联试卷）
#   kb_exam_history   AI 出卷主表（试卷本体）
# 保留：kb_student（学生账号，除非 --with-students）
#
# 目标库默认为运行中容器 knowledge-mysql 的 knowledge_repository（即应用实际连接的线上库）。
# 连接凭据取自容器内 MYSQL_ROOT_PASSWORD，不落盘、不硬编码。
#
# 安全策略：
#   1) 执行前先 mysqldump 备份这 4 张表（可用 --no-backup 跳过），备份为空则中止。
#   2) 非交互执行必须显式传 --yes，否则进入交互确认。
#   3) 关闭外键检查后 TRUNCATE（会重置自增），并打印清理前后行数。
#
# 用法：
#   bash clean_exam_data.sh                 # 交互确认
#   bash clean_exam_data.sh --yes           # 直接执行（CI/自动化）
#   bash clean_exam_data.sh --dry-run       # 只统计不清理
#   bash clean_exam_data.sh --with-students # 连 kb_student 一并清空
#   bash clean_exam_data.sh --no-backup     # 跳过备份（不推荐）
#   bash clean_exam_data.sh --container xxx --database yyy   # 指定其它容器/库
#
# @author Knowledge-Repository
# @date 2026-09-19

set -euo pipefail

# ---------- 可配置项 ----------
CONTAINER="${KR_MYSQL_CONTAINER:-knowledge-mysql}"
DATABASE="${KR_MYSQL_DB:-knowledge_repository}"
TABLES=(kb_exam_answer kb_exam_session kb_exam_question kb_exam_history)

YES=0
DRY_RUN=0
BACKUP=1
WITH_STUDENTS=0
BACKUP_DIR="${KR_BACKUP_DIR:-$HOME/.local/share/knowledge-repo/exam_backups}"

# ---------- 解析参数 ----------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --yes) YES=1 ;;
        --dry-run) DRY_RUN=1 ;;
        --no-backup) BACKUP=0 ;;
        --with-students) WITH_STUDENTS=1 ;;
        --container) CONTAINER="$2"; shift ;;
        --database) DATABASE="$2"; shift ;;
        --backup-dir) BACKUP_DIR="$2"; shift ;;
        -h|--help) grep -E '^#( |$)' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "未知参数: $1（用 --help 查看用法）" >&2; exit 2 ;;
    esac
    shift
done

if [[ "$WITH_STUDENTS" -eq 1 ]]; then
    TABLES+=(kb_student)
fi

# ---------- 辅助函数 ----------
# 在 mysql 容器内以 root 执行 SQL（凭据取自容器环境变量）
sql() {
    docker exec -i "$CONTAINER" sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B "$1" 2>/dev/null' _ "$DATABASE"
}

# 统计单表行数
count_rows() {
    local t="$1"
    printf 'SELECT COUNT(*) FROM %s;' "$t" | sql
}

log() { printf '\033[1;36m[clean]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*"; }
die() { printf '\033[1;31m[error]\033[0m %s\n' "$*" >&2; exit 1; }

# ---------- 前置校验 ----------
docker inspect "$CONTAINER" >/dev/null 2>&1 \
    || die "找不到容器 '$CONTAINER'。用 --container 指定，或确认 infra 已启动。"

TABLES_EXIST=()
for t in "${TABLES[@]}"; do
    exists=$(printf "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='%s' AND table_name='%s';" "$DATABASE" "$t" | sql)
    [[ "$exists" == "1" ]] || die "表 $t 在库 $DATABASE 中不存在，终止。"
    TABLES_EXIST+=("$t")
done

# ---------- 打印将清理的数据 ----------
log "目标库：容器 '$CONTAINER' / 库 '$DATABASE'"
log "将清理的表及当前行数："
TOTAL=0
for t in "${TABLES[@]}"; do
    n=$(count_rows "$t")
    printf '    %-18s %s 行\n' "$t" "$n"
    TOTAL=$((TOTAL + n))
done
log "合计待删除 $TOTAL 行。"

[[ "$DRY_RUN" -eq 1 ]] && { log "dry-run 模式，未做任何修改。"; exit 0; }

# ---------- 交互确认 ----------
if [[ "$YES" -ne 1 ]]; then
    printf '\n\033[1;31m⚠ 该操作不可逆。输入 YES 确认清空上述 %d 张表：%s\033[0m ' "${#TABLES[@]}" "→ "
    read -r ans
    [[ "$ans" == "YES" ]] || die "已取消（未输入 YES）。"
fi

# ---------- 备份 ----------
if [[ "$BACKUP" -eq 1 ]]; then
    mkdir -p "$BACKUP_DIR"
    STAMP="$(date +%Y%m%d_%H%M%S)"
    BACKUP_FILE="$BACKUP_DIR/exam_backup_${STAMP}.sql"
    log "备份中 → $BACKUP_FILE"
    docker exec "$CONTAINER" sh -c \
        'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --quick --set-gtid-purged=OFF "$1" $2 2>/dev/null' \
        _ "$DATABASE" "${TABLES[*]}" > "$BACKUP_FILE"
    [[ -s "$BACKUP_FILE" ]] || die "备份文件为空，为安全起见中止清理。"
    # 粗略校验：至少含建表/插入语句之一
    if ! grep -qiE "CREATE TABLE|INSERT INTO|Dump completed" "$BACKUP_FILE"; then
        die "备份内容异常（未检出有效 dump 标记），中止清理。"
    fi
    log "备份完成（$(wc -c < "$BACKUP_FILE" | tr -d ' ') 字节）。恢复示例："
    echo "    cat '$BACKUP_FILE' | docker exec -i $CONTAINER sh -c 'mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" $DATABASE'"
else
    warn "--no-backup 已跳过备份！"
fi

# ---------- 执行清理 ----------
log "开始 TRUNCATE（子表→父表，已关闭外键检查）..."
LIST=$(printf '%s, ' "${TABLES[@]}"); LIST=${LIST%, }
{
    echo "SET FOREIGN_KEY_CHECKS=0;"
    for t in "${TABLES[@]}"; do echo "TRUNCATE TABLE $t;"; done
    echo "SET FOREIGN_KEY_CHECKS=1;"
} | sql

# ---------- 结果核对 ----------
log "清理后行数："
LEFT=0
for t in "${TABLES[@]}"; do
    n=$(count_rows "$t")
    printf '    %-18s %s 行\n' "$t" "$n"
    LEFT=$((LEFT + n))
done

if [[ "$LEFT" -eq 0 ]]; then
    log "✅ 全部清空完成。KB 学生账号：$([[ "$WITH_STUDENTS" -eq 1 ]] && echo '已一并清理' || echo '已保留')。"
else
    warn "仍有 $LEFT 行残留，请检查（可能存在并发写入或 TRUNCATE 失败）。"
fi
