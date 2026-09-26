#!/usr/bin/env bash
# ============================================================================
# 03-deploy.sh —— 放应用文件 + systemd + nginx + 启动 + 验收
#
# 前置：
#   ① 01-install.sh 与 02-database.sh 已跑完
#   ② 本地构建的 jar 与 dist 已经传到 /tmp/vcp-upload/（见 README 第二节）：
#        /tmp/vcp-upload/vcp-boot-1.0.0.jar
#        /tmp/vcp-upload/dist/index.html ...
#
# 用法（从仓库根目录执行最省事）：
#     sudo bash deploy/small-server/03-deploy.sh
#     sudo bash -c 'VCP_DB_PASSWORD=你的口令 bash deploy/small-server/03-deploy.sh'
#     sudo bash -c 'UPLOAD_DIR=/tmp/xxx bash deploy/small-server/03-deploy.sh'   # 换个上传目录
#
# 幂等：重复执行会覆盖 jar/dist（先备份成 .bak）并重启服务；vcp.env 已存在时默认沿用其中的口令。
#
# 落盘布局：
#   /opt/vcp/vcp-boot-1.0.0.jar   后端 fat jar（含上一版 .bak，回滚用）
#   /opt/vcp/vcp.env              环境变量（600、属主 vcp，含数据库口令，**不入库**）
#   /opt/vcp/precompress.py       deploy/precompress.py 的副本（前端发版预压缩用）
#   /opt/vcp/README-deploy.md     本目录 README 的副本（systemd 单元的 Documentation= 指向它）
#   /var/www/vcp/                 前端 dist（含预压缩 .gz），归 vcp 所有
# ============================================================================
set -euo pipefail

# ---------------------------------------------------------------------------
# 可调变量（与 vcp.service / 02-database.sh 保持一致）
# ---------------------------------------------------------------------------
APP_USER="${APP_USER:-vcp}"
APP_DIR="${APP_DIR:-/opt/vcp}"
WWW_DIR="${WWW_DIR:-/var/www/vcp}"
JAR_NAME="${JAR_NAME:-vcp-boot-1.0.0.jar}"
SERVICE_NAME="${SERVICE_NAME:-vcp}"
SITE_NAME="${SITE_NAME:-vcp}"
UPLOAD_DIR="${UPLOAD_DIR:-/tmp/vcp-upload}"
DB_NAME="${DB_NAME:-volunteer_cert_portrait}"
DB_USER="${DB_USER:-vcp}"
# DB_HOST/DB_PORT 在 02-database.sh 里也有；这里补上是因为 vcp.env 要写
# SPRING_DATASOURCE_URL（脚本开了 set -u，未定义变量会直接报 unbound variable）
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-5432}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$APP_DIR/vcp.env"
TOTAL_STEPS=8

# ---------------------------------------------------------------------------
# 小工具
# ---------------------------------------------------------------------------
STEP=0
log()  { printf '\n\033[1;36m[%s/%s] %s\033[0m\n' "$STEP" "$TOTAL_STEPS" "$*"; }
step() { STEP=$((STEP + 1)); log "$*"; }
info() { printf '      %s\n' "$*"; }
warn() { printf '\033[1;33m      [警告] %s\033[0m\n' "$*" >&2; }
die()  { printf '\033[1;31m      [失败] %s\033[0m\n' "$*" >&2; exit 1; }

backup_once() {
    local f="$1"
    if [ -e "$f" ] && [ ! -e "$f.vcp.bak" ]; then
        cp -a "$f" "$f.vcp.bak"
        info "已备份 $f → $f.vcp.bak"
    fi
}

# 验收用小计数器：ok/bad 只统计，最后统一决定退出码
PASS=0
FAIL=0
ok()  { PASS=$((PASS + 1)); printf '\033[1;32m      ✓ %s\033[0m\n' "$*"; }
bad() { FAIL=$((FAIL + 1)); printf '\033[1;31m      ✗ %s\033[0m\n' "$*"; }

# ===========================================================================
step "前提检查"
# ===========================================================================
[ "$(id -u)" -eq 0 ] || die "需要 root：sudo bash $0"
command -v java >/dev/null 2>&1 || die "没装 java，请先跑 01-install.sh"
command -v nginx >/dev/null 2>&1 || die "没装 nginx，请先跑 01-install.sh"
systemctl list-unit-files postgresql.service >/dev/null 2>&1 || warn "看不到 postgresql.service，确认数据库是另外起的？"
pg_isready -h 127.0.0.1 -p 5432 >/dev/null 2>&1 || die "PostgreSQL 没就绪，请先跑 02-database.sh"

[ -d "$UPLOAD_DIR" ] || die "找不到上传目录 $UPLOAD_DIR。
      先在自己的电脑上：
        ssh <用户>@<ECS公网IP> 'mkdir -p /tmp/vcp-upload'
        scp volunteer-cert-portrait-server/vcp-boot/target/vcp-boot-1.0.0.jar <用户>@<ECS公网IP>:/tmp/vcp-upload/
        scp -r volunteer-cert-portrait-web/dist <用户>@<ECS公网IP>:/tmp/vcp-upload/dist"

# ---- 找 jar：优先固定名，其次目录里最新的 vcp-boot-*.jar ----
UPLOAD_JAR="$UPLOAD_DIR/$JAR_NAME"
if [ ! -f "$UPLOAD_JAR" ]; then
    UPLOAD_JAR="$(find "$UPLOAD_DIR" -maxdepth 1 -name 'vcp-boot-*.jar' -printf '%T@ %p\n' 2>/dev/null | sort -rn | head -n 1 | cut -d' ' -f2-)"
fi
[ -n "${UPLOAD_JAR:-}" ] && [ -f "$UPLOAD_JAR" ] || die "$UPLOAD_DIR 里没有 vcp-boot-*.jar"
JAR_BYTES="$(stat -c%s "$UPLOAD_JAR")"
[ "$JAR_BYTES" -gt 1000000 ] || die "$UPLOAD_JAR 只有 ${JAR_BYTES} 字节，上传不完整（fat jar 约 40 MB）"
if [ "$(head -c 2 "$UPLOAD_JAR")" != "PK" ]; then
    warn "jar 不是以 PK 开头（可能是 executable 模式的 jar 或上传损坏）；继续，但若启动失败先重传一次"
fi
info "jar：$UPLOAD_JAR（$(du -h "$UPLOAD_JAR" | cut -f1)）"

# ---- 找 dist：支持 /tmp/vcp-upload/dist 或去掉一层目录的 .../volunteer-cert-portrait-web/dist ----
UPLOAD_DIST="$UPLOAD_DIR/dist"
[ -f "$UPLOAD_DIST/index.html" ] || UPLOAD_DIST="$UPLOAD_DIR/volunteer-cert-portrait-web/dist"
[ -f "$UPLOAD_DIST/index.html" ] || die "$UPLOAD_DIR 下找不到 dist/index.html。dist 必须是「本地 npm run build 的产物目录」整体上传。"
info "dist：$UPLOAD_DIST（$(du -sh "$UPLOAD_DIST" | cut -f1)，$(find "$UPLOAD_DIST" -type f | wc -l) 个文件）"

# ---- 数据库口令：环境变量 > 已有 vcp.env > 交互输入 ----
if [ -z "${VCP_DB_PASSWORD:-}" ]; then
    if [ -f "$ENV_FILE" ] && grep -q '^VCP_DB_PASSWORD=' "$ENV_FILE"; then
        VCP_DB_PASSWORD="$(sed -n 's/^VCP_DB_PASSWORD=//p' "$ENV_FILE" | head -n 1 || true)"
        VCP_DB_PASSWORD="${VCP_DB_PASSWORD%\"}"; VCP_DB_PASSWORD="${VCP_DB_PASSWORD#\"}"
        info "沿用 $ENV_FILE 里已有的数据库口令（要换：VCP_DB_PASSWORD=xxx 重跑本脚本）"
    else
        read -r -s -p "      请输入 02-database.sh 里给 $DB_USER 设置的口令: " VCP_DB_PASSWORD
        echo
    fi
fi
[ -n "${VCP_DB_PASSWORD:-}" ] || die "数据库口令不能为空"
case "$VCP_DB_PASSWORD" in
    *'"'*) die '口令里含双引号，EnvironmentFile 解析不了；请在数据库里换一个口令（02-database.sh 会重置）' ;;
esac

# ---- 先把口令验证一遍，再往下走 ----
# 为什么必须有这一步：vcp.env 一旦写错口令，应用能起来但每个请求都会
# "password authentication failed"（对外表现是 code=10000 系统繁忙），
# 而部署脚本要到第 8 步验收才发现，中间白等几分钟（真机实测踩到）。
# 这里提前用 psql 连一次，错了立刻重问/中止。
verify_db_password() {
    PGPASSWORD="$VCP_DB_PASSWORD" psql -X -q -tA -h 127.0.0.1 -p 5432 -U "$DB_USER" -d "$DB_NAME" \
        -c 'SELECT 1' >/dev/null 2>&1
}
if ! command -v psql >/dev/null 2>&1; then
    warn "本机没有 psql，跳过口令预验证（请确认口令与 02-database.sh 输入的一致）"
elif verify_db_password; then
    info "口令验证通过：$DB_USER 能连上 $DB_NAME"
else
    warn "口令验证失败：$DB_USER 连不上 $DB_NAME"
    if [ -t 0 ]; then
        read -r -s -p "      请重新输入（留空则中止）: " VCP_DB_PASSWORD
        echo
        [ -n "${VCP_DB_PASSWORD:-}" ] || die "已中止：口令不对，别继续（否则应用会一直报 10000 系统繁忙）"
        verify_db_password || die "还是连不上。排查：① 口令是否与 02-database.sh 一致 ② 02 是否跑完（角色/库在不在）
      手工验证：sudo bash -c 'set -a; . /opt/vcp/vcp.env; set +a; PGPASSWORD=\"\$VCP_DB_PASSWORD\" psql -h 127.0.0.1 -U $DB_USER -d $DB_NAME -c \"select 1\"'"
    else
        die "非交互执行且口令不对，已中止"
    fi
fi

# ===========================================================================
step "系统用户与目录"
# ===========================================================================
if id -u "$APP_USER" >/dev/null 2>&1; then
    info "用户 $APP_USER 已存在，跳过创建"
else
    # -r 系统用户 / -M 不建家目录 / -s nologin 禁止登录（这个账号只用来跑服务）
    useradd -r -M -s /usr/sbin/nologin -d "$APP_DIR" "$APP_USER"
    info "已创建系统用户 $APP_USER（无登录 shell）"
fi
install -d -o "$APP_USER" -g "$APP_USER" -m 755 "$APP_DIR" "$APP_DIR/logs" "$WWW_DIR"
info "目录就绪：$APP_DIR、$WWW_DIR（归 $APP_USER 所有）"

# ===========================================================================
step "放置 jar 与前端 dist"
# ===========================================================================
if [ -f "$APP_DIR/$JAR_NAME" ]; then
    backup_once "$APP_DIR/$JAR_NAME"
fi
install -o "$APP_USER" -g "$APP_USER" -m 644 "$UPLOAD_JAR" "$APP_DIR/$JAR_NAME"
info "已放置 $APP_DIR/$JAR_NAME（回滚：cp $APP_DIR/$JAR_NAME.vcp.bak $APP_DIR/$JAR_NAME && systemctl restart $SERVICE_NAME）"

# dist 只有约 1 MB，直接清空重放；.gz 会由下一步重压（precompress 会清掉孤儿 .gz）
rm -rf "${WWW_DIR:?}/"*
cp -a "$UPLOAD_DIST/." "$WWW_DIR/"
chown -R "$APP_USER:$APP_USER" "$WWW_DIR"
# 目录 755 / 文件 644：nginx（www-data）只要读权限就够
find "$WWW_DIR" -type d -exec chmod 755 {} +
find "$WWW_DIR" -type f -exec chmod 644 {} +
info "已放置 dist 到 $WWW_DIR"

# README 副本：systemd 单元的 Documentation=file:/opt/vcp/README-deploy.md 指向它
if [ -f "$SCRIPT_DIR/README.md" ]; then
    install -o "$APP_USER" -g "$APP_USER" -m 644 "$SCRIPT_DIR/README.md" "$APP_DIR/README-deploy.md"
fi

# ===========================================================================
step "生成 $ENV_FILE"
# ===========================================================================
MEM_MB="$(awk '/^MemTotal:/{printf "%d", $2/1024}' /proc/meminfo)"
# 三档（2026-09-27 补 TINY 档）：阿里云的「1 GB」规格实测 MemTotal 只有 ~700 MB，
# 按 1G 档给 -Xmx384m 会连 PG 一起挤进 swap，所以 < 900 MB 单独降一档。
# 想强制指定堆大小：sudo bash -c 'XMX=256m bash 03-deploy.sh'
if [ -n "${XMX:-}" ]; then
    MEM_TIER="手工指定（-Xmx${XMX}）"
elif [ "$MEM_MB" -lt 900 ]; then
    XMX="288m"; MEM_TIER="极小内存档（<900 MB，-Xmx288m）"
elif [ "$MEM_MB" -lt 1536 ]; then
    XMX="384m"; MEM_TIER="1 GB 档（-Xmx384m）"
else
    XMX="512m"; MEM_TIER="2 GB 档（-Xmx512m）"
fi
if [ -f "$ENV_FILE" ]; then
    backup_once "$ENV_FILE"
fi
cat > "$ENV_FILE" <<EOF
# $ENV_FILE —— 由 03-deploy.sh 生成（$(date '+%F %T')，内存 ${MEM_MB} MB → ${MEM_TIER}）
# 权限 600、属主 $APP_USER；**含数据库口令，不要入库、不要贴给别人**。
# 改动后生效方式：sudo systemctl restart $SERVICE_NAME
#
# 只监听本机：配合安全组不开 8080 + vcp.service 的 --server.address=127.0.0.1 三道收口，
# 让后端只能被 nginx 反代访问，公网扫不到 8080。
SERVER_ADDRESS=127.0.0.1
SPRING_PROFILES_ACTIVE=prod
# ⚠️ 必须显式覆盖数据源地址：application.yml 里的 spring.datasource.url 指向的是
# **云演示库**（103.40.14.100:19476），不覆盖的话应用会去连云库 —— 而云库里没有本机的
# $DB_USER 角色，于是每个请求都报 code=10000「系统繁忙」，日志里是
#   FATAL: password authentication failed for user "$DB_USER"
# （真机实测踩到：psql 用同一口令能连本机库，应用却连不上，就是这个原因）。
SPRING_DATASOURCE_URL=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}?currentSchema=public&stringtype=unspecified
VCP_DB_USERNAME=$DB_USER
VCP_DB_PASSWORD="$VCP_DB_PASSWORD"
# JVM 参数（小内存档位见上）；-XX:+ExitOnOutOfMemoryError 让 OOM 时进程退出被 systemd 拉起
JAVA_OPTS=-Xms256m -Xmx$XMX -XX:MaxMetaspaceSize=128m -XX:ThreadStackSize=512k -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ExitOnOutOfMemoryError -Duser.timezone=GMT+8 -Dfile.encoding=UTF-8
EOF
chown "$APP_USER:$APP_USER" "$ENV_FILE"
chmod 600 "$ENV_FILE"
info "已写入 $ENV_FILE（600）：SPRING_PROFILES_ACTIVE=prod、SERVER_ADDRESS=127.0.0.1、$MEM_TIER"
info "数据源指向本机库：jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}（覆盖 application.yml 里的云库地址）"
info "提示：prod profile 会把 CORS 收紧、关掉文档页、把 SQL 日志降回 info（见 application-prod.yml）"

# ===========================================================================
step "安装 systemd 单元并启动"
# ===========================================================================
[ -f "$SCRIPT_DIR/vcp.service" ] || die "找不到 $SCRIPT_DIR/vcp.service"
backup_once /etc/systemd/system/vcp.service
install -m 644 "$SCRIPT_DIR/vcp.service" /etc/systemd/system/vcp.service
systemctl daemon-reload
systemctl enable "$SERVICE_NAME" >/dev/null 2>&1 || warn "enable 失败，可稍后手工 enable"
if ! systemctl restart "$SERVICE_NAME"; then
    systemctl status "$SERVICE_NAME" --no-pager -l | head -n 30 || true
    journalctl -u "$SERVICE_NAME" -n 60 --no-pager || true
    die "$SERVICE_NAME 启动失败，日志见上（常见原因：vcp.env 里的口令不对、jar 路径不对）"
fi
info "已启动，等待后端就绪（最多 90 秒）..."
READY=0
for _ in $(seq 1 45); do
    if curl -fsS -o /dev/null --max-time 2 "http://127.0.0.1:8080/api/v1/auth/colleges"; then
        READY=1; break
    fi
    sleep 2
done
if [ "$READY" != "1" ]; then
    journalctl -u "$SERVICE_NAME" -n 60 --no-pager || true
    die "后端 90 秒内没有响应 http://127.0.0.1:8080/api/v1/auth/colleges，日志见上"
fi
info "后端已就绪：$(systemctl show "$SERVICE_NAME" -p ActiveState --value) / $(systemctl show "$SERVICE_NAME" -p SubState --value)"

# 确认真的只监听 127.0.0.1（SERVER_ADDRESS 与 --server.address 双保险是否都生效）
if ss -ltn 2>/dev/null | awk '{print $4}' | grep -qx '127.0.0.1:8080'; then
    info "监听确认：127.0.0.1:8080"
else
    warn "没看到 127.0.0.1:8080 的监听（ss 输出可能不完整），稍后验收会再查一次"
fi

# ===========================================================================
step "前端预压缩（生成 gzip_static 用的 .gz）"
# ===========================================================================
PRECOMPRESS_SRC="$SCRIPT_DIR/../precompress.py"
if [ -f "$PRECOMPRESS_SRC" ]; then
    install -o "$APP_USER" -g "$APP_USER" -m 644 "$PRECOMPRESS_SRC" "$APP_DIR/precompress.py"
    if runuser -u "$APP_USER" -- python3 "$APP_DIR/precompress.py" --dist "$WWW_DIR"; then
        info "预压缩完成（.gz 与原文件同目录，nginx 开 gzip_static 后优先发 .gz）"
    else
        warn "预压缩失败，不影响功能：nginx 的 gzip on 仍会做运行时压缩"
    fi
    # 服务器上没有仓库目录树，precompress.py 默认的 dist 路径不成立，
    # 所以包一层固定 --dist /var/www/vcp 的快捷命令，前端发版时直接用它。
    cat > /usr/local/bin/vcp-precompress <<EOF
#!/usr/bin/env bash
# 前端发版：把新 dist 传到 $WWW_DIR 后执行 vcp-precompress（可选参数如 --force）
set -euo pipefail
if [ "\$(id -u)" -eq 0 ]; then
    exec runuser -u $APP_USER -- python3 $APP_DIR/precompress.py --dist $WWW_DIR "\$@"
fi
exec python3 $APP_DIR/precompress.py --dist $WWW_DIR "\$@"
EOF
    chmod 755 /usr/local/bin/vcp-precompress
    info "已安装快捷命令：vcp-precompress"
else
    warn "找不到 $PRECOMPRESS_SRC，跳过预压缩（把 deploy/precompress.py 一起传到服务器即可补上）"
fi

# ===========================================================================
step "安装 nginx 站点配置"
# ===========================================================================
[ -f "$SCRIPT_DIR/nginx-vcp.conf" ] || die "找不到 $SCRIPT_DIR/nginx-vcp.conf"
backup_once "/etc/nginx/sites-available/$SITE_NAME"
install -m 644 "$SCRIPT_DIR/nginx-vcp.conf" "/etc/nginx/sites-available/$SITE_NAME"

# gzip_static 模块检查：缺了就注释掉那一行（否则 nginx -t 直接报 unknown directive）
if ! nginx -V 2>&1 | grep -q -- '--with-http_gzip_static_module'; then
    sed -i 's|^\([[:space:]]*\)gzip_static[[:space:]]\+on;|\1# gzip_static on;  # 本机 nginx 未编译该模块，03-deploy.sh 自动注释|' \
        "/etc/nginx/sites-available/$SITE_NAME"
    warn "本机 nginx 不支持 gzip_static：已自动注释该行（运行时 gzip 仍生效，只是少了预压缩收益）"
    warn "想用预压缩：sudo apt-get install -y nginx-full && sudo systemctl restart nginx，然后恢复那一行"
fi

ln -sf "../sites-available/$SITE_NAME" "/etc/nginx/sites-enabled/$SITE_NAME"
if [ -e /etc/nginx/sites-enabled/default ]; then
    rm -f /etc/nginx/sites-enabled/default
    info "已禁用 Debian 默认站点（sites-enabled/default）"
fi
nginx -t || die "nginx 配置语法检查失败，先别 reload；上面一行是报错原因"
systemctl reload nginx || systemctl restart nginx
info "nginx 已加载站点 $SITE_NAME（root=$(awk '/^[[:space:]]*root/{print $2}' "/etc/nginx/sites-available/$SITE_NAME" | tr -d ';' | head -n 1)）"

# ===========================================================================
step "验收（逐条 curl）"
# ===========================================================================
BASE="http://127.0.0.1"
info "以下检查都在服务器本机执行；从你的电脑再验证一遍外网访问（见 README 第四节）"

# 1) 首页
CODE="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$BASE/" || true)"
[ "$CODE" = "200" ] && ok "首页 200" || bad "首页返回 $CODE（期望 200；检查 $WWW_DIR/index.html 是否存在、nginx 是否 reload）"

# 2) 健康检查
HEALTH="$(curl -s --max-time 5 "$BASE/nginx-health" || true)"
[ "$HEALTH" = "ok" ] && ok "nginx-health 返回 ok" || bad "nginx-health 返回 '$HEALTH'（期望 ok）"

# 3) SPA 深层路由回退（history 路由命门）
CODE="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$BASE/student/profile" || true)"
[ "$CODE" = "200" ] && ok "深层路由 /student/profile 回退 200" || bad "深层路由返回 $CODE（期望 200；检查 location / 的 try_files）"

# 4) 学院下拉：应返回 10 个学院（免登录接口）
COLLEGES="$(curl -s --max-time 10 "$BASE/api/v1/auth/colleges" || true)"
if printf '%s' "$COLLEGES" | grep -q '"code":0'; then
    N_COLLEGES="$(printf '%s' "$COLLEGES" | grep -o '"label"' | wc -l | tr -d ' ')"
    [ "$N_COLLEGES" = "10" ] && ok "GET /api/v1/auth/colleges：code=0，学院 10 个" \
                            || bad "学院数量是 $N_COLLEGES（期望 10；少说明 04/10 号脚本没跑全）"
else
    bad "GET /api/v1/auth/colleges 返回异常：$(printf '%s' "$COLLEGES" | head -c 200)"
    # code=10000（系统繁忙）几乎都是后端连不上库（最常见是口令不对）——
    # 直接把日志尾部打出来，别让人再去猜（真机实测：这里附日志能一眼看到
    # "password authentication failed for user"）。
    printf '        后端日志尾部（journalctl -u %s -n 25）：\n' "$SERVICE_NAME"
    journalctl -u "$SERVICE_NAME" -n 25 --no-pager 2>/dev/null | sed 's/^/          /' || true
    printf '        自查口令：sudo bash -c '"'"'set -a; . %s; set +a; PGPASSWORD="$VCP_DB_PASSWORD" psql -h 127.0.0.1 -U %s -d %s -c "select count(*) from student_info;"'"'"'\n' \
        "$ENV_FILE" "$DB_USER" "$DB_NAME"
fi

# 5) 三个测试账号登录 code=0，并用 admin 的 token 取看板
TOKEN=""
for u in student admin org_admin; do
    RESP="$(curl -s --max-time 15 -X POST "$BASE/api/v1/auth/login" \
        -H 'Content-Type: application/json' \
        -d "{\"username\":\"$u\",\"password\":\"123456\"}" || true)"
    if printf '%s' "$RESP" | grep -q '"code":0'; then
        ok "登录 $u：code=0"
        if [ "$u" = "admin" ]; then
            TOKEN="$(printf '%s' "$RESP" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
        fi
    else
        bad "登录 $u 失败：$(printf '%s' "$RESP" | head -c 200)"
    fi
done

# 6) 看板关键数字（演示口径：2349 条报名 / 9163.0 小时）
if [ -n "$TOKEN" ]; then
    DASH="$(curl -s --max-time 20 "$BASE/api/v1/analytics/dashboard" -H "Authorization: Bearer $TOKEN" || true)"
    printf '%s' "$DASH" | grep -q '2349' && ok "看板报名数 2349" || bad "看板里没找到 2349（报名数）"
    printf '%s' "$DASH" | grep -q '9163\.0' && ok "看板累计时长 9163.0" || bad "看板里没找到 9163.0（累计时长）"
else
    bad "拿不到 admin 的 token，跳过看板检查"
fi

# 7) 活动图片：内容接口（图片存数据库，不经任何静态目录）
HDRS="$(curl -s -D - -o /dev/null --max-time 15 "$BASE/api/v1/attachments/1/content" || true)"
ETAG="$(printf '%s' "$HDRS" | sed -n 's/^[Ee][Tt][Aa][Gg]: *//p' | tr -d '\r')"
if printf '%s' "$HDRS" | grep -qi '^content-type: *image/jpeg'; then
    ok "图片接口 /api/v1/attachments/1/content 返回 image/jpeg"
else
    bad "图片接口 Content-Type 不是 image/jpeg（摘要：$(printf '%s' "$HDRS" | head -n 1)）—— 12/13 号脚本跑了吗？"
fi
[ -n "$ETAG" ] && ok "图片接口带 ETag：$ETAG" || bad "图片接口没有 ETag 响应头"
if [ -n "$ETAG" ]; then
    CODE="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 -H "If-None-Match: $ETAG" "$BASE/api/v1/attachments/1/content" || true)"
    [ "$CODE" = "304" ] && ok "带 If-None-Match 复请求返回 304（浏览器可长缓存）" || bad "If-None-Match 复请求返回 $CODE（期望 304）"
fi

# 8) 8080 只监听本机（外网访问不到）
# 判定顺序（真机实测两处误报后定的）：
#   ① 先用 curl 证明「本机可达」—— 这是硬标准；ss 查不到不代表没在听
#      （Tomcat 可能绑在 [::1] 或 ss 输出格式有差异，实测都遇到过）；
#   ② 再用 ss 查「有没有绑到 0.0.0.0 / ::」—— 这才是真正要防的"对外暴露"。
LOCAL_CODE="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:8080/api/v1/auth/colleges || true)"
if [ -z "$LOCAL_CODE" ] || [ "$LOCAL_CODE" = "000" ]; then
    bad "8080 本机连不上（后端没起？看 journalctl -u $SERVICE_NAME -n 60）"
elif ! command -v ss >/dev/null 2>&1; then
    ok "8080 本机可达（HTTP $LOCAL_CODE；未装 ss，跳过"是否绑到全网卡"的检查 —— 记得安全组不要放行 8080）"
else
    LISTEN_ALL="$(ss -ltn 2>/dev/null | awk '{print $4}' | grep -E '^(0\.0\.0\.0|\*|\[::\]):8080$' || true)"
    LISTEN_LOCAL="$(ss -ltn 2>/dev/null | awk '{print $4}' | grep -xE '(127\.0\.0\.1|\[::1\]):8080' || true)"
    if [ -z "$LISTEN_ALL" ] && [ -n "$LISTEN_LOCAL" ]; then
        ok "8080 只监听本机（$LISTEN_LOCAL），未绑到全网卡（安全组里也不要放行 8080）"
    elif [ -z "$LISTEN_ALL" ] && [ -z "$LISTEN_LOCAL" ]; then
        # curl 已经证明可达，这里只是 ss 没解析出来（格式差异），不当失败
        ok "8080 本机可达（HTTP $LOCAL_CODE；ss 未列出该端口，可能绑在 ::1 或输出格式不同）"
    else
        bad "8080 绑到了全网卡：$LISTEN_ALL —— 去 $ENV_FILE 确认 SERVER_ADDRESS=127.0.0.1，"
        printf '        vcp.service 的 ExecStart 带 --server.address=127.0.0.1，并检查阿里云安全组\n'
    fi
fi

# ===========================================================================
printf '\n\033[1;32m==================== 03-deploy.sh 完成 ====================\033[0m\n'
printf '  验收：%s 项通过，%s 项失败\n' "$PASS" "$FAIL"
cat <<EOF

  服务   : systemctl status $SERVICE_NAME / journalctl -u $SERVICE_NAME -n 100
  首页   : 在浏览器打开 http://<ECS 公网 IP>/          （本机自测 curl -I http://127.0.0.1/）
  重启   : systemctl restart $SERVICE_NAME
  发版   : 换 jar → systemctl restart $SERVICE_NAME；换前端 → 传 dist 到 $WWW_DIR → vcp-precompress → systemctl reload nginx

  再验一遍外网（在你自己的电脑上）：
    curl -s -o /dev/null -w '%{http_code}\n' http://<ECS 公网 IP>/            # 期望 200
    curl -s http://<ECS 公网 IP>/nginx-health                                  # 期望 ok
    curl -s -o /dev/null -w '%{http_code}\n' http://<ECS 公网 IP>:8080/        # 期望连不上（超时/拒绝）
EOF

if [ "$FAIL" -gt 0 ]; then
    printf '\n\033[1;31m有 %s 项验收失败，请按上面的提示逐条排查；把输出贴回给开发者即可。\033[0m\n' "$FAIL"
    exit 1
fi
