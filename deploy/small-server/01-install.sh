#!/usr/bin/env bash
# ============================================================================
# 01-install.sh —— 新机器初始化（阿里云 ECS + Debian 12 + 1~2 GB 内存）
#
# 装什么：JDK 21 / PostgreSQL 18 / nginx，外加 2 GB swap、东八区时区、基础加固
#
# 用法（文件在 deploy/small-server/ 下，从**仓库根目录**执行最省事）：
#     sudo bash deploy/small-server/01-install.sh
#     sudo bash -c 'USE_ALIYUN_MIRROR=0 bash deploy/small-server/01-install.sh'   # 不用阿里云内网源
#     sudo bash -c 'JDK_SOURCE=temurin bash deploy/small-server/01-install.sh'    # 直接下 Temurin
#     sudo bash -c 'NGINX_FROM_OFFICIAL=1 bash deploy/small-server/01-install.sh' # nginx.org 官方源
#
# 幂等：重复执行会跳过已装好的部分；被改动的系统文件会先备份成 *.vcp.bak
#
# 为什么这么选（细节在各步骤注释里）：
#   * apt 源换阿里云**内网**镜像 mirrors.cloud.aliyuncs.com：ECS 内网、免流量、快
#   * JDK 21：先试 bookworm-backports 的 openjdk-21-jdk-headless，装不上则回退
#     清华 TUNA 的 Temurin 21 压缩包解到 /opt/jdk-21
#     （2026-09-27 实测提醒：Debian 的 bookworm-backports **并没有** openjdk-21，
#      只有 trixie/forky/sid 有 —— 所以正常路径就是下面的 Temurin 回退分支）
#   * PostgreSQL 18：Debian 12 官方源里没有，必须走 PGDG 源；
#     默认用阿里云镜像的 PGDG（内网 mirrors.cloud.aliyuncs.com/postgresql/repos/apt）
#   * nginx：默认用 Debian 自带的 1.22（proxy / gzip_static / http2 都有，够用）；
#     NGINX_FROM_OFFICIAL=1 才走 nginx.org（版本新，但国内下载可能很慢）
# ============================================================================
set -euo pipefail

# ---------------------------------------------------------------------------
# 可调变量（默认值都能直接用；改这里或按上面的方式传环境变量）
# ---------------------------------------------------------------------------
USE_ALIYUN_MIRROR="${USE_ALIYUN_MIRROR:-1}"      # 1=apt 源换阿里云内网镜像
PGDG_MIRROR="${PGDG_MIRROR:-aliyun}"             # aliyun（默认，内网）| official（PGDG 官方脚本）| tuna
NGINX_FROM_OFFICIAL="${NGINX_FROM_OFFICIAL:-0}"  # 1=nginx.org 官方源；0=Debian 自带
JDK_SOURCE="${JDK_SOURCE:-auto}"                 # auto | backports | temurin
JDK_DIR="${JDK_DIR:-/opt/jdk-21}"                # Temurin 的解压目录
TEMURIN_MIRROR="${TEMURIN_MIRROR:-https://mirrors.tuna.tsinghua.edu.cn/Adoptium}"
TEMURIN_TARBALL="${TEMURIN_TARBALL:-}"           # 留空=自动取镜像里最新的；也可写死文件名
SWAP_SIZE_MB="${SWAP_SIZE_MB:-2048}"             # 0=不建 swap（不建议）
SWAP_FILE="${SWAP_FILE:-/swapfile}"
TIMEZONE="${TIMEZONE:-Asia/Shanghai}"
PG_MAJOR="${PG_MAJOR:-18}"
ALIYUN_HOST="mirrors.cloud.aliyuncs.com"         # 阿里云 ECS 内网镜像域名（经典网络用 mirrors.aliyuncs.com）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export DEBIAN_FRONTEND=noninteractive
TOTAL_STEPS=8

# ---------------------------------------------------------------------------
# 小工具
# ---------------------------------------------------------------------------
log()  { printf '\n\033[1;36m[%s/%s] %s\033[0m\n' "$STEP" "$TOTAL_STEPS" "$*"; }
info() { printf '      %s\n' "$*"; }
warn() { printf '\033[1;33m      [警告] %s\033[0m\n' "$*" >&2; }
die()  { printf '\033[1;31m      [失败] %s\033[0m\n' "$*" >&2; exit 1; }
STEP=0
step() { STEP=$((STEP + 1)); log "$*"; }

# 覆盖文件前先备份一次（不覆盖已存在的 .vcp.bak，保证最早的那份原始文件留着）
backup_once() {
    local f="$1"
    if [ -f "$f" ] && [ ! -f "$f.vcp.bak" ]; then
        cp -a "$f" "$f.vcp.bak"
        info "已备份 $f → $f.vcp.bak"
    fi
}

# ===========================================================================
step "环境检查"
# ===========================================================================
[ "$(id -u)" -eq 0 ] || die "需要 root：sudo bash $0"
[ -f /etc/os-release ] || die "读不到 /etc/os-release，这不是 Debian？"
# shellcheck disable=SC1091
. /etc/os-release
info "系统：${PRETTY_NAME:-unknown}"
case "${ID:-}" in
    debian) ;;
    *) die "本脚本只按 Debian 12（bookworm）写；当前 ID=${ID:-unknown}。Ubuntu 请改用仓库 docs/部署文档.md 的步骤。" ;;
esac
[ "${VERSION_CODENAME:-}" = "bookworm" ] || warn "版本代号是 ${VERSION_CODENAME:-unknown}，不是 bookworm；apt 源与包名可能对不上，请自行核对。"
case "$(uname -m)" in
    x86_64)  TEMURIN_ARCH="x64" ;;
    aarch64) TEMURIN_ARCH="aarch64" ;;
    *)       die "不支持的 CPU 架构：$(uname -m)（只写了 x86_64 / aarch64 两种情况）" ;;
esac
MEM_MB="$(awk '/^MemTotal:/{printf "%d", $2/1024}' /proc/meminfo)"
info "内存：${MEM_MB} MB（CPU：$(nproc) 核）"
[ "${MEM_MB:-0}" -ge 900 ] || warn "内存小于 1 GB，本脚本的参数是按 1~2 GB 定的，很可能不够用。"

# ===========================================================================
step "apt 源（阿里云内网镜像 + bookworm-backports）"
# ===========================================================================
if [ "$USE_ALIYUN_MIRROR" = "1" ]; then
    # Debian 12 有两种写法：老式 /etc/apt/sources.list 与新式 deb822 的 *.sources，
    # 云镜像两种都可能出现，所以两处都试着替换（只换域名，保留原有仓库与选项）。
    for f in /etc/apt/sources.list /etc/apt/sources.list.d/debian.sources; do
        [ -f "$f" ] || continue
        if grep -qE 'deb\.debian\.org|security\.debian\.org' "$f"; then
            backup_once "$f"
            sed -i "s|deb\.debian\.org|${ALIYUN_HOST}|g; s|security\.debian\.org|${ALIYUN_HOST}/debian-security|g" "$f"
            info "已换源：$f → ${ALIYUN_HOST}"
        fi
    done
    info "提示：这台机器不在阿里云内网时，内网域名解析不了，请改用"
    info "      sudo bash -c 'USE_ALIYUN_MIRROR=0 bash $0'"
else
    info "USE_ALIYUN_MIRROR=0，保持系统原有 apt 源不动"
fi

# backports：仅在 JDK_SOURCE=backports/auto 时需要（2026-09-27 实测 bookworm-backports 里
# 并没有 openjdk-21；加着它不占空间，apt update 也不会因此变慢多少 ——
# 留着是为了将来 backports 真收录了能直接装）。
if [ "$USE_ALIYUN_MIRROR" = "1" ]; then
    BP_MIRROR="http://${ALIYUN_HOST}/debian"
else
    BP_MIRROR="http://deb.debian.org/debian"
fi
if ! grep -qs "bookworm-backports" /etc/apt/sources.list.d/backports.list /etc/apt/sources.list /etc/apt/sources.list.d/*.sources 2>/dev/null; then
    printf 'deb %s bookworm-backports main\n' "$BP_MIRROR" > /etc/apt/sources.list.d/backports.list
    info "已添加 backports 源：deb $BP_MIRROR bookworm-backports main"
else
    info "backports 源已存在，跳过"
fi

if ! apt-get update -o Acquire::Retries=3; then
    warn "apt-get update 失败：如果这台机器不在阿里云内网，用下面这行重跑本脚本 ——"
    warn "  sudo bash -c 'USE_ALIYUN_MIRROR=0 bash $0'"
    die "apt 源不可用"
fi

# ===========================================================================
step "基础工具"
# ===========================================================================
# python3 是给 deploy/precompress.py（生成 gzip_static 用的 .gz）准备的；
# curl/gnupg 给加第三方源用；git 方便以后在服务器上直接 git pull 更新。
apt-get install -y --no-install-recommends \
    ca-certificates curl gnupg git python3 procps
info "已装：ca-certificates curl gnupg git python3 procps"

# ===========================================================================
step "时区与时钟同步"
# ===========================================================================
if command -v timedatectl >/dev/null 2>&1 && timedatectl set-timezone "$TIMEZONE" 2>/dev/null; then
    info "时区已设为 $TIMEZONE（timedatectl）"
else
    # 兜底：容器或极小镜像里没有 systemd 时用软链
    ln -sf "/usr/share/zoneinfo/$TIMEZONE" /etc/localtime
    echo "$TIMEZONE" > /etc/timezone
    info "时区已设为 $TIMEZONE（软链方式）"
fi
systemctl enable --now systemd-timesyncd >/dev/null 2>&1 || warn "systemd-timesyncd 启动失败；云主机一般自带时间同步，可忽略"
info "当前时间：$(date '+%F %T %Z')"

# ===========================================================================
step "swap（默认 2 GB）"
# ===========================================================================
# 为什么必须建：1 GB 内存跑 JVM(384m 堆) + PostgreSQL(192MB shared_buffers) + nginx，
# 峰值一定超过物理内存。没有 swap 时内核只能 OOM Kill（通常先杀 JVM），
# 表现为"服务半夜自己重启/挂了"；有 swap 则退化为变慢，而不是直接被杀。
# 注意：swap 是安全垫，不是内存 —— 如果 free -h 里 swap 长期用掉一半以上，
# 说明内存真不够，该升配到 2 GB 或调小 JVM 堆。
SWAP_ACTIVE="$(swapon --show --noheadings 2>/dev/null | wc -l || true)"
if [ "$SWAP_SIZE_MB" = "0" ]; then
    warn "SWAP_SIZE_MB=0：跳过 swap（1 GB 机器强烈不建议）"
elif [ "$SWAP_ACTIVE" -gt 0 ]; then
    info "已有 swap，跳过创建："
    swapon --show | sed 's/^/      /'
else
    info "创建 ${SWAP_SIZE_MB} MB swap 文件 $SWAP_FILE（云盘上 dd 大约要几十秒）..."
    rm -f "$SWAP_FILE"
    # 用 dd 而不是 fallocate：fallocate 生成的是"未写入数据的空洞文件"，
    # 老内核对这类文件 swapon 会报 "Invalid argument"，dd 最稳。
    dd if=/dev/zero of="$SWAP_FILE" bs=1M count="$SWAP_SIZE_MB" status=progress
    chmod 600 "$SWAP_FILE"
    mkswap "$SWAP_FILE" >/dev/null
    swapon "$SWAP_FILE"
    if ! grep -qE "^${SWAP_FILE}[[:space:]]" /etc/fstab; then
        backup_once /etc/fstab
        printf '%s none swap sw 0 0\n' "$SWAP_FILE" >> /etc/fstab
        info "已写入 /etc/fstab，重启后自动挂载"
    fi
    info "swap 已启用：$(swapon --show=SIZE --noheadings | tr -d ' ')"
fi

# swappiness=10：把热数据尽量留在 RAM，swap 只当防 OOM 的安全垫。
# （默认 60 会让内核过早换出，演示系统的响应会明显抖动。）
cat > /etc/sysctl.d/99-vcp.conf <<'EOF'
# 由 deploy/small-server/01-install.sh 写入
vm.swappiness = 10
EOF
sysctl --system >/dev/null 2>&1 || warn "sysctl --system 执行失败，vm.swappiness 可能没生效"

# ===========================================================================
step "JDK 21"
# ===========================================================================
has_jdk21() {
    command -v java >/dev/null 2>&1 && java -version 2>&1 | grep -qE 'version "21\.'
}

try_jdk_backports() {
    info "尝试 Debian 官方源：openjdk-21-jdk-headless（-t bookworm-backports）"
    # 这个包在 bookworm-backports 里**大概率不存在**（2026-09-27 实测 Debian 只有
    # trixie/forky/sid 收录了 openjdk-21），失败属预期，不要当错误处理。
    apt-get install -y -t bookworm-backports openjdk-21-jdk-headless >/dev/null 2>&1 || true
    has_jdk21
}

install_temurin_tarball() {
    local dir_url="${TEMURIN_MIRROR}/21/jdk/${TEMURIN_ARCH}/linux/"
    local tarball="$TEMURIN_TARBALL"
    if [ -z "$tarball" ]; then
        info "从 $dir_url 解析最新的 Temurin 21 包名..."
        tarball="$(curl -fsSL --retry 2 "$dir_url" 2>/dev/null \
            | grep -oE "OpenJDK21U-jdk_${TEMURIN_ARCH}_linux_hotspot_[0-9][0-9._]*\.tar\.gz" \
            | sort -V | tail -n 1 || true)"
    fi
    [ -n "$tarball" ] || die "解析不到 Temurin 包名。请手工下载后重跑：
        https://mirrors.tuna.tsinghua.edu.cn/Adoptium/21/jdk/${TEMURIN_ARCH}/linux/
        例：sudo bash -c 'TEMURIN_TARBALL=OpenJDK21U-jdk_${TEMURIN_ARCH}_linux_hotspot_21.0.12.1_1.tar.gz bash $0'"

    if [ -x "$JDK_DIR/bin/java" ] && "$JDK_DIR/bin/java" -version 2>&1 | grep -qE 'version "21\.'; then
        info "已存在可用的 $JDK_DIR，跳过下载"
    else
        info "下载 $tarball（约 190 MB，国内走清华镜像）..."
        curl -fL --retry 2 -o "/tmp/$tarball" "${dir_url}${tarball}"
        rm -rf "$JDK_DIR"
        install -d "$JDK_DIR"
        tar -xzf "/tmp/$tarball" -C "$JDK_DIR" --strip-components=1
        rm -f "/tmp/$tarball"
        info "已解压到 $JDK_DIR"
    fi
    # 用 update-alternatives 把它挂成系统默认 java/javac（优先级 2100 高于 Debian 的 17xx）
    update-alternatives --install /usr/bin/java  java  "$JDK_DIR/bin/java"  2100 >/dev/null
    update-alternatives --install /usr/bin/javac javac "$JDK_DIR/bin/javac" 2100 >/dev/null
    printf 'export JAVA_HOME=%s\nexport PATH="$JAVA_HOME/bin:$PATH"\n' "$JDK_DIR" > /etc/profile.d/jdk21.sh
    info "已写入 /etc/profile.d/jdk21.sh（JAVA_HOME=$JDK_DIR）"
    has_jdk21
}

case "$JDK_SOURCE" in
    backports)
        if ! has_jdk21; then try_jdk_backports || die "backports 里没有 openjdk-21；改用 JDK_SOURCE=temurin 重跑"; fi
        ;;
    temurin)
        has_jdk21 || install_temurin_tarball
        ;;
    auto)
        if has_jdk21; then
            info "系统里已经有 JDK 21，跳过安装"
        elif try_jdk_backports; then
            info "从 backports 装上了 openjdk-21"
        else
            warn "backports 没有 openjdk-21（符合预期），回退到清华 TUNA 的 Temurin 21 压缩包"
            install_temurin_tarball
        fi
        ;;
    *)
        die "JDK_SOURCE 只能是 auto / backports / temurin，当前是 $JDK_SOURCE"
        ;;
esac
hash -r 2>/dev/null || true
has_jdk21 || die "JDK 21 没有装成功。请检查网络后重跑，或手工安装后确认 java -version 输出 21。"
JAVA_BIN="$(command -v java)"
info "java：$JAVA_BIN"
java -version 2>&1 | sed 's/^/      /'
[ "$JAVA_BIN" = "/usr/bin/java" ] || warn "java 不在 /usr/bin/java（vcp.service 里写的是这个绝对路径）；请把 vcp.service 的 ExecStart 改成 $JAVA_BIN"

# ===========================================================================
step "PostgreSQL ${PG_MAJOR}（PGDG 源）"
# ===========================================================================
if command -v psql >/dev/null 2>&1 && psql --version | grep -q " ${PG_MAJOR}\."; then
    info "已装 PostgreSQL ${PG_MAJOR}，跳过安装"
else
    # postgresql-common 里带着 PGDG 官方的加源脚本 apt.postgresql.org.sh
    apt-get install -y postgresql-common

    case "$PGDG_MIRROR" in
        official)
            info "使用 PGDG 官方脚本加源（apt.postgresql.org，国内访问可能很慢）"
            [ -x /usr/share/postgresql-common/pgdg/apt.postgresql.org.sh ] \
                || die "找不到官方加源脚本 /usr/share/postgresql-common/pgdg/apt.postgresql.org.sh。
      它由 postgresql-common 提供；装不上就用 PGDG_MIRROR=aliyun 重跑。"
            /usr/share/postgresql-common/pgdg/apt.postgresql.org.sh -y
            ;;
        tuna|aliyun)
            if [ "$PGDG_MIRROR" = "tuna" ]; then
                # 诚实提醒：清华 TUNA **没有**镜像 PGDG 的 apt 源
                #（2026-09-27 实测 mirrors.tuna.tsinghua.edu.cn/postgresql/repos/apt/ 返回 404），
                # 所以这个取值自动改用阿里云源。国内其它可选：mirrors.ustc.edu.cn 同样没有。
                warn "TUNA 不镜像 PGDG apt 源（实测 404），自动改用阿里云镜像"
            fi
            # 阿里云镜像站的 PostgreSQL 仓库；ECS 内网用 mirrors.cloud.aliyuncs.com（免流量）,
            # 公网/非阿里云机器退回 mirrors.aliyun.com。
            PGDG_BASE="http://${ALIYUN_HOST}/postgresql/repos/apt"
            if ! curl -fsI --max-time 8 "${PGDG_BASE}/dists/bookworm-pgdg/Release" >/dev/null 2>&1; then
                warn "阿里云内网镜像不可达，退回公网 https://mirrors.aliyun.com/postgresql/repos/apt"
                PGDG_BASE="https://mirrors.aliyun.com/postgresql/repos/apt"
            fi
            curl -fsSL --retry 2 "${PGDG_BASE}/ACCC4CF8.asc" \
                | gpg --dearmor --yes -o /usr/share/keyrings/postgresql-pgdg.gpg \
                || die "拉取 / 导入 PGDG 公钥失败（$PGDG_BASE/ACCC4CF8.asc）；检查网络或改 PGDG_MIRROR=official"
            printf 'deb [signed-by=/usr/share/keyrings/postgresql-pgdg.gpg] %s bookworm-pgdg main\n' \
                "$PGDG_BASE" > /etc/apt/sources.list.d/pgdg.list
            info "已添加 PGDG 源：$PGDG_BASE bookworm-pgdg main"
            apt-get update -o Acquire::Retries=3
            ;;
        *)
            die "PGDG_MIRROR 只能是 aliyun / official / tuna，当前是 $PGDG_MIRROR"
            ;;
    esac

    apt-get install -y "postgresql-${PG_MAJOR}"
fi
systemctl enable --now postgresql >/dev/null 2>&1 || warn "postgresql 服务 enable 失败（容器里 systemd 可能受限）"
# 注：psql 由 postgresql-18 依赖的 postgresql-client-18 提供，不需要单独装
#（包名是 postgresql-client-18，不是 postgresql-18-client，别写反）。
pg_isready -h 127.0.0.1 -p 5432 >/dev/null 2>&1 \
    || die "PostgreSQL 没在 127.0.0.1:5432 就绪。排查：systemctl status postgresql / journalctl -u postgresql -n 50"
info "PostgreSQL 已就绪：$(psql --version)"

# ---- 小内存参数：从 postgres-tuning.conf 里按内存选一档写进 conf.d ----
PG_CONF_DIR="/etc/postgresql/${PG_MAJOR}/main/conf.d"
if [ -d "/etc/postgresql/${PG_MAJOR}/main" ]; then
    install -d "$PG_CONF_DIR"
    if [ "$MEM_MB" -lt 1536 ]; then PROFILE="1G"; else PROFILE="2G"; fi
    if [ -f "${SCRIPT_DIR}/postgres-tuning.conf" ]; then
        {
            printf '# 由 01-install.sh 生成（%s，内存 %s MB → PROFILE-%s）\n' "$(date '+%F %T')" "$MEM_MB" "$PROFILE"
            printf '# 源文件：deploy/small-server/postgres-tuning.conf（改参数请改源文件后重跑，或直接改本文件）\n'
            awk -v p="PROFILE-${PROFILE}" \
                '$0 ~ ("^#[[:space:]]*>>>" p ">>>[[:space:]]*$") {f=1; next}
                 $0 ~ ("^#[[:space:]]*<<<" p "<<<[[:space:]]*$") {f=0; next}
                 f' \
                "${SCRIPT_DIR}/postgres-tuning.conf"
        } > "${PG_CONF_DIR}/99-vcp-tuning.conf"
        info "已写入 ${PG_CONF_DIR}/99-vcp-tuning.conf（PROFILE-${PROFILE}）"
        systemctl restart postgresql
        pg_isready -h 127.0.0.1 -p 5432 >/dev/null || die "改完参数后 PostgreSQL 起不来，检查 ${PG_CONF_DIR}/99-vcp-tuning.conf"
        info "参数已生效：shared_buffers=$(runuser -u postgres -- psql -X -tAc 'SHOW shared_buffers')，max_connections=$(runuser -u postgres -- psql -X -tAc 'SHOW max_connections')"
    else
        warn "找不到 ${SCRIPT_DIR}/postgres-tuning.conf，跳过小内存参数（PG 会用默认值，演示库也能跑）"
    fi
else
    warn "找不到 /etc/postgresql/${PG_MAJOR}/main，跳过参数片段（集群名字可能不是 main）"
fi

# ===========================================================================
step "nginx 与基础加固"
# ===========================================================================
if [ "$NGINX_FROM_OFFICIAL" = "1" ]; then
    info "使用 nginx.org 官方源（版本更新，但国内下载可能很慢）"
    curl -fsSL --retry 2 https://nginx.org/keys/nginx_signing.key \
        | gpg --dearmor --yes -o /usr/share/keyrings/nginx-archive-keyring.gpg
    printf 'deb [signed-by=/usr/share/keyrings/nginx-archive-keyring.gpg] http://nginx.org/packages/debian bookworm nginx\n' \
        > /etc/apt/sources.list.d/nginx.list
    apt-get update -o Acquire::Retries=3
fi
if ! command -v nginx >/dev/null 2>&1; then
    apt-get install -y nginx
else
    info "nginx 已安装，跳过"
fi
systemctl enable --now nginx >/dev/null 2>&1 || warn "nginx enable 失败（容器里 systemd 可能受限）"
info "nginx：$(nginx -v 2>&1)"

# gzip_static / proxy / http2 都由 Debian 的 nginx 正常提供；但若本机装的是精简版
# （nginx-light 之类），缺了 gzip_static 模块会让站点配置里的 `gzip_static on;` 直接报错。
# 这里提前检查并补救，避免 03-deploy.sh 那一步才炸。
if nginx -V 2>&1 | grep -q -- '--with-http_gzip_static_module'; then
    info "nginx 带 http_gzip_static_module（站点配置里的 gzip_static on 可用）"
else
    warn "本机 nginx 不带 http_gzip_static_module，尝试改装 nginx-full ..."
    apt-get install -y nginx-full >/dev/null 2>&1 || true
    systemctl restart nginx >/dev/null 2>&1 || true
    if nginx -V 2>&1 | grep -q -- '--with-http_gzip_static_module'; then
        info "nginx-full 已带 gzip_static"
    else
        warn "仍无 gzip_static：03-deploy.sh 会自动把站点配置里的 gzip_static on 注释掉（功能不受影响，只是少了预压缩收益）"
    fi
fi

# ---- 基础加固（都很轻，但能省掉后来踩的坑） ----
# 1) 只自动装**安全更新**：小服务器最怕的是"上线三个月没打过补丁"
apt-get install -y unattended-upgrades >/dev/null 2>&1 || warn "unattended-upgrades 安装失败，可忽略"
cat > /etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF
info "已开启自动安全更新（unattended-upgrades）"

# 2) journald 日志限额 200 MB：30 GB 系统盘虽然不小，但 journal 无上限会慢慢吃满，
#    磁盘满时 PostgreSQL 会直接拒绝写入，排查起来很费劲。日志排查看 journalctl 就够。
install -d /etc/systemd/journald.conf.d
cat > /etc/systemd/journald.conf.d/99-vcp.conf <<'EOF'
[Journal]
SystemMaxUse=200M
EOF
systemctl restart systemd-journald >/dev/null 2>&1 || true
info "journald 日志上限已设为 200 MB"

# 3) **故意不动** sshd：改 SSH 配置最容易把自己锁在门外，
#    22 端口的来源限制交给阿里云安全组（见 README 第一节）即可。

# ===========================================================================
printf '\n\033[1;32m==================== 01-install.sh 完成 ====================\033[0m\n'
cat <<EOF
  Java    : $(java -version 2>&1 | head -n 1)
  psql    : $(psql --version 2>/dev/null || echo '未安装')
  nginx   : $(nginx -v 2>&1)
  内存    : $(free -h | awk '/^Mem:/{print $2" 总量 / "$7" 可用"}')
  swap    : $(free -h | awk '/^Swap:/{print $2" 总量 / "$4" 已用"}')

下一步：跑数据库初始化（会交互询问数据库口令）
  sudo bash deploy/small-server/02-database.sh
EOF
