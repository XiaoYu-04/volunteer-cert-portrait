#!/usr/bin/env bash
# ============================================================================
# 02-database.sh —— 建库建角色 + 按顺序灌 SQL 脚本 + 只读自检
#
# 用法（必须先跑过 01-install.sh；从仓库根目录执行最省事）：
#     sudo bash deploy/small-server/02-database.sh
#     sudo bash -c 'VCP_DB_PASSWORD=你的口令 bash deploy/small-server/02-database.sh'   # 非交互
#     sudo bash -c 'RESET_DB=1 bash deploy/small-server/02-database.sh'                 # 库已有数据也强制重建
#
# 做了什么：
#   1. 以 postgres 超级用户（本机 peer 认证）建角色 vcp、建库 volunteer_cert_portrait
#   2. 以 **vcp 身份**经 TCP 127.0.0.1 按硬顺序跑 sql/ 下的脚本
#        （PG 15+ 的 public schema 归库主所有，库主建表不需要额外授权）
#   3. 跑只读自检 sql/09_consistency_check.sql，并**断言**输出等于
#      「检查项总数 20，违规合计 0」，不等就退出码 1
#
# 幂等与安全：
#   角色/库用 IF NOT EXISTS 式判断，重复执行只重置角色口令；
#   若库里已经有业务表（sys_user 存在），默认**跳过** SQL 链 ——
#   因为 02_schema.sql 开头是 DROP TABLE IF EXISTS（会清空数据），
#   需要重建时显式传 RESET_DB=1（见 README 第七节「备份与恢复」）。
#
# 口令来源（两选一，脚本里不写死任何口令）：
#   * 环境变量 VCP_DB_PASSWORD
#   * 交互输入（read -s，不回显）
# ============================================================================
set -euo pipefail

# ---------------------------------------------------------------------------
# 可调变量
# ---------------------------------------------------------------------------
DB_NAME="${DB_NAME:-volunteer_cert_portrait}"
DB_USER="${DB_USER:-vcp}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-5432}"
PG_MAJOR="${PG_MAJOR:-18}"
RESET_DB="${RESET_DB:-0}"                 # 1=库里已有数据也重建（02_schema.sql 会 DROP TABLE）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SQL_DIR="$REPO_ROOT/sql"
TOTAL_STEPS=7

# ---------------------------------------------------------------------------
# 小工具
# ---------------------------------------------------------------------------
STEP=0
log()  { printf '\n\033[1;36m[%s/%s] %s\033[0m\n' "$STEP" "$TOTAL_STEPS" "$*"; }
step() { STEP=$((STEP + 1)); log "$*"; }
info() { printf '      %s\n' "$*"; }
warn() { printf '\033[1;33m      [警告] %s\033[0m\n' "$*" >&2; }
die()  { printf '\033[1;31m      [失败] %s\033[0m\n' "$*" >&2; exit 1; }

# 以 postgres 超级用户执行 SQL（Debian 默认 local peer 认证，root 可以直接 runuser 过去）
super_sql() { runuser -u postgres -- psql -X -q -A -t -v ON_ERROR_STOP=1 "$@"; }

# 以应用角色 vcp 经 TCP 执行 SQL 文件
run_sql_file() {
    local file="$1" label="$2"
    [ -f "$file" ] || die "缺少 SQL 文件：$file（sql/ 目录要和 deploy/ 同级，见 README 第三节）"
    info "→ ${label}  ($(basename "$file"))"
    PGPASSWORD="$VCP_DB_PASSWORD" psql -X -q \
        -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
        -v ON_ERROR_STOP=1 -f "$file"
}

# ===========================================================================
step "前提检查"
# ===========================================================================
[ "$(id -u)" -eq 0 ] || die "需要 root：sudo bash $0"
command -v psql >/dev/null 2>&1 || die "没装 psql。请先跑 01-install.sh（它会装 PostgreSQL ${PG_MAJOR}）"
[ -d "$SQL_DIR" ] || die "找不到 SQL 目录：$SQL_DIR。本脚本要求 deploy/ 与 sql/ 在同一个仓库根下（README 第三节有两种传文件方式）"
for f in 02_schema.sql 03_init_data.sql 05_backend_gap_fix.sql 06_backend_gap_fix2.sql \
         04_demo_data.sql 10_base_and_test_accounts.sql 11_activity_images.sql \
         12_activity_images_demo.sql 13_attachment_binary.sql 09_consistency_check.sql; do
    [ -f "$SQL_DIR/$f" ] || die "缺少 $SQL_DIR/$f"
done
info "SQL 目录：$SQL_DIR"
pg_isready -h "$DB_HOST" -p "$DB_PORT" >/dev/null 2>&1 \
    || die "PostgreSQL 没在 ${DB_HOST}:${DB_PORT} 就绪。排查：systemctl status postgresql / journalctl -u postgresql -n 50"
info "PostgreSQL 已就绪（$(psql --version)）"

# ===========================================================================
step "取数据库口令"
# ===========================================================================
if [ -z "${VCP_DB_PASSWORD:-}" ]; then
    read -r -s -p "      请为应用角色 $DB_USER 设置数据库口令（输入不回显）: " VCP_DB_PASSWORD
    echo
    read -r -s -p "      再输一遍确认: " VCP_DB_PASSWORD2
    echo
    [ "$VCP_DB_PASSWORD" = "${VCP_DB_PASSWORD2:-}" ] || die "两次输入不一致"
    unset VCP_DB_PASSWORD2
else
    info "使用环境变量 VCP_DB_PASSWORD 提供的口令"
fi
[ -n "$VCP_DB_PASSWORD" ] || die "口令不能为空"
case "$VCP_DB_PASSWORD" in
    *'"'*) die '口令里不要包含双引号 —— /opt/vcp/vcp.env 是 systemd EnvironmentFile，引号会被解析掉。换一个口令。' ;;
esac
case "$VCP_DB_PASSWORD" in
    *[![:print:]]*) die "口令里含不可打印字符（换行/Tab 等），EnvironmentFile 里会有歧义，换一个" ;;
esac
info "口令长度 ${#VCP_DB_PASSWORD} 个字符（脚本不会打印内容）"

# ===========================================================================
step "建角色 + 建库"
# ===========================================================================
# SQL 里的单引号转义（口令里含 ' 时不出错）
ESC_PWD="${VCP_DB_PASSWORD//\'/\'\'}"
if [ "$(super_sql -tAc "SELECT 1 FROM pg_roles WHERE rolname='$DB_USER'")" = "1" ]; then
    super_sql -c "ALTER ROLE \"$DB_USER\" WITH LOGIN PASSWORD '$ESC_PWD'"
    info "角色 $DB_USER 已存在：口令已按本次输入重置"
else
    super_sql -c "CREATE ROLE \"$DB_USER\" LOGIN PASSWORD '$ESC_PWD'"
    info "已创建角色 $DB_USER（LOGIN）"
fi

if [ "$(super_sql -tAc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'")" = "1" ]; then
    super_sql -c "ALTER DATABASE \"$DB_NAME\" OWNER TO \"$DB_USER\""
    info "库 $DB_NAME 已存在：属主已确认为 $DB_USER"
else
    # OWNER=vcp：库主即 public schema 的 owner（PG 15+ 语义），建表无需额外授权
    super_sql -c "CREATE DATABASE \"$DB_NAME\" WITH OWNER \"$DB_USER\" ENCODING 'UTF8' TEMPLATE template0"
    info "已创建库 $DB_NAME（OWNER=$DB_USER，ENCODING=UTF8）"
fi

# 兜底授权：PG 15+ 里 public schema 归库主所有，理论上不用这一步；
# 但若有人手工把库主改成别人，这一句能省掉一次「建表权限不足」的排查。
super_sql -d "$DB_NAME" -c "GRANT ALL ON SCHEMA public TO \"$DB_USER\""

# 用应用角色走一遍 TCP：这一步同时验证了角色口令、pg_hba.conf 的 127.0.0.1 规则
if ! PGPASSWORD="$VCP_DB_PASSWORD" psql -X -q -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -tAc 'SELECT 1' >/dev/null 2>&1; then
    die "以 $DB_USER 经 ${DB_HOST}:${DB_PORT} 连不上 $DB_NAME。
      排查 ① 口令是否与刚才输入的一致；② /etc/postgresql/${PG_MAJOR}/main/pg_hba.conf 里应有：
        host  all  all  127.0.0.1/32  scram-sha-256
      ③ 改过 pg_hba.conf 后要 systemctl reload postgresql"
fi
info "验证通过：$DB_USER 可以经 TCP 连上 $DB_NAME"

# ===========================================================================
step "判断是否需要重建"
# ===========================================================================
HAS_TABLE="$(PGPASSWORD="$VCP_DB_PASSWORD" psql -X -q -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
    -tAc "SELECT to_regclass('public.sys_user') IS NOT NULL" || echo UNKNOWN)"
case "$HAS_TABLE" in
    t|f) ;;
    # 查不出来时**不能**当作空库继续跑 —— 那等于在数据还在的库上重跑 02_schema.sql（DROP TABLE）
    *) die "无法判断库里是否已有业务表（返回值：$HAS_TABLE）；为防误清库，已中止。请先手工确认：
      psql -h $DB_HOST -U $DB_USER -d $DB_NAME -c \"SELECT to_regclass('public.sys_user')\"" ;;
esac
if [ "$HAS_TABLE" = "t" ] && [ "$RESET_DB" != "1" ]; then
    warn "库里已经有业务表（sys_user 存在），**跳过**建库 SQL 链。"
    warn "原因：02_schema.sql 开头是 DROP TABLE IF EXISTS，重跑会清空所有数据。"
    warn "确实要重建（会丢数据，先做 pg_dump 备份！）："
    warn "  sudo bash -c 'RESET_DB=1 bash deploy/small-server/02-database.sh'"
    SKIP_CHAIN=1
else
    SKIP_CHAIN=0
fi

# ===========================================================================
step "按顺序执行 SQL 脚本"
# ===========================================================================
# 顺序不可颠倒，理由（详见 sql/README.md）：
#   02 建表 → 03 基础数据 → 05/06 补列（必须在 04 之前，04 会写这两步补的列）
#   → 04 演示数据（10 学院/1000 学生/10 活动/2349 报名/40 张图元数据）
#   → 10 学院字典与测试管理员（新环境必需）→ 11 图片字段 → 12 图片元数据
#   → 13 图片二进制入库（约 10 MB 的 base64，最慢的一步）
#   → 14 性能索引 + ANALYZE（可重复执行）→ 09 只读自检
CHAIN=(
    "02_schema.sql|02_schema：建 16 张表 + 索引"
    "03_init_data.sql|03_init_data：角色/测试账号/字典基础数据"
    "05_backend_gap_fix.sql|05_backend_gap_fix：后端补列（第一批）"
    "06_backend_gap_fix2.sql|06_backend_gap_fix2：后端补列（第二批）"
    "04_demo_data.sql|04_demo_data：演示数据（1000 学生 / 10 活动 / 2349 报名）"
    "10_base_and_test_accounts.sql|10_base_and_test_accounts：学院字典 + 测试管理员"
    "11_activity_images.sql|11_activity_images：附件 content_type/caption/sort_order"
    "12_activity_images_demo.sql|12_activity_images_demo：40 张活动图片元数据"
    "13_attachment_binary.sql|13_attachment_binary：图片二进制入库（约 10 MB，稍等）"
    "14_performance_indexes.sql|14_performance_indexes：性能索引 + ANALYZE"
)

if [ "$SKIP_CHAIN" = "0" ]; then
    CHAIN_START=$SECONDS
    for item in "${CHAIN[@]}"; do
        run_sql_file "$SQL_DIR/${item%%|*}" "${item#*|}"
    done
    info "SQL 链执行完毕，用时 $((SECONDS - CHAIN_START)) 秒"
else
    info "已跳过 SQL 链（库非空且 RESET_DB != 1）"
fi

# ===========================================================================
step "关键数字（与 README 的演示口径对照）"
# ===========================================================================
PGPASSWORD="$VCP_DB_PASSWORD" psql -X -q -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" <<'SQL' || warn "关键数字查询失败（不中断流程，后面的 09 自检才是硬标准）"
SELECT '学院字典' AS 项目, COUNT(*)::text AS 数量 FROM sys_dict WHERE dict_type = 'college'
UNION ALL SELECT '学生档案', COUNT(*)::text FROM student_info WHERE deleted = 0
UNION ALL SELECT '活动',     COUNT(*)::text FROM volunteer_activity WHERE deleted = 0
UNION ALL SELECT '报名',     COUNT(*)::text FROM activity_signup
UNION ALL SELECT '服务时长', COUNT(*)::text FROM service_duration
UNION ALL SELECT '活动图片', COUNT(*)::text FROM attachment WHERE biz_type = 'ACTIVITY'
UNION ALL SELECT '累计时长', ROUND(COALESCE(SUM(total_duration), 0), 1)::text || ' 小时' FROM student_info
UNION ALL SELECT '图片字节', COALESCE(SUM(octet_length(file_data)), 0)::text || ' 字节' FROM attachment
SQL

# ===========================================================================
step "只读自检 09（断言：检查项总数 20，违规合计 0）"
# ===========================================================================
CHECK_OUT="$(PGPASSWORD="$VCP_DB_PASSWORD" psql -X -A -t -F'|' \
    -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
    -v ON_ERROR_STOP=1 -f "$SQL_DIR/09_consistency_check.sql")" \
    || die "09 自检执行失败（报错见上）。常见原因：01~06 号脚本没跑全（09 用到 05 补的列）。"

# 明细逐行打印（check_no | category | check_name | violations），最后一行是汇总（99）
printf '%s\n' "$CHECK_OUT" | sed 's/^/      /'

SUMMARY="$(printf '%s\n' "$CHECK_OUT" | awk -F'|' '$1=="99"{print $3}')"
VIOLATIONS="$(printf '%s\n' "$CHECK_OUT" | awk -F'|' '$1=="99"{print $4}')"
[ -n "$SUMMARY" ] || die "09 自检没有输出汇总行，脚本可能没跑完；请单独执行：
      psql -h $DB_HOST -U $DB_USER -d $DB_NAME -f $SQL_DIR/09_consistency_check.sql"

EXPECT="检查项总数 20，违规合计 0"
if [ "$SUMMARY" = "$EXPECT" ] && [ "$VIOLATIONS" = "0" ]; then
    printf '\n\033[1;32m      ✓ 自检通过：%s\033[0m\n' "$SUMMARY"
else
    printf '\n\033[1;31m      ✗ 自检未通过\033[0m\n' >&2
    printf '      实际：%s（违规合计 %s）\n' "$SUMMARY" "$VIOLATIONS" >&2
    printf '      期望：%s\n' "$EXPECT" >&2
    printf '      违规明细（前 20 条）：\n' >&2
    printf '%s\n' "$CHECK_OUT" | awk -F'|' '$1!="99" && $4+0 > 0 {printf "        [%s] %s / %s = %s\n", $1, $2, $3, $4}' >&2
    printf '      排查提示：常见原因是 04/12/13 号脚本没跑（演示数据与图片不完整），\n' >&2
    printf '      或跑了 10 号脚本后没补跑 05 → 06（10 会 TRUNCATE 掉补列回填的值）。\n' >&2
    exit 1
fi

printf '\n\033[1;32m==================== 02-database.sh 完成 ====================\033[0m\n'
cat <<EOF
  库        : $DB_NAME（角色 $DB_USER，127.0.0.1:$DB_PORT）
  自检      : $SUMMARY
  备份示例  : pg_dump -h 127.0.0.1 -U $DB_USER -Fc $DB_NAME > vcp-\$(date +%F).dump

下一步：部署应用（jar 与 dist 先按 README 第二节传到 /tmp/vcp-upload/）
  sudo bash ${SCRIPT_DIR}/03-deploy.sh
EOF
