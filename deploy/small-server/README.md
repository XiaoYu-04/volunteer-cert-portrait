# 小内存服务器部署包（阿里云 ECS + Debian 12 + 1~2 GB 内存）

一套「照抄即可」的部署包：JDK 21 + PostgreSQL 18 + nginx 反代，把本仓库的后端 jar 与前端 dist
部署到一台 1~2 GB 内存的阿里云 ECS 上。

```text
浏览器 ──► nginx（80/443，安全组只开这两个）
              ├─ /       → /var/www/vcp 静态文件（Vue3 dist + 预压缩 .gz）
              └─ /api/   → 127.0.0.1:8080 后端（仅本机监听，公网扫不到）
                                └─► PostgreSQL 18（127.0.0.1:5432，含活动图片二进制）
```

本目录 7 个文件：

| 文件 | 作用 | 你在哪一步用 |
|---|---|---|
| `README.md` | 本手册 | 全程 |
| `01-install.sh` | JDK 21 / PostgreSQL 18 / nginx + 2 GB swap + 时区 + 基础加固 | 第三节 第 1 步 |
| `02-database.sh` | 建角色建库 + 按顺序跑 `sql/` + 自检断言 | 第三节 第 2 步 |
| `03-deploy.sh` | 放 jar/dist、systemd、nginx、启动、验收 | 第三节 第 3 步 |
| `vcp.service` | 后端 systemd 单元（小内存 JVM 参数 + 覆盖项） | 03 自动装 |
| `postgres-tuning.conf` | 小内存 PostgreSQL 参数片段（1G / 2G 两档） | 01 自动装 |
| `nginx-vcp.conf` | 站点配置（反代 + SPA 回退 + gzip_static + TLS 模板） | 03 自动装 |

> ⚠️ **先读第十节的「诚实声明」**：这套脚本是照 Debian 12 写的，但**没有在真实 ECS 上实跑过**。

---

## 一、机器与系统：为什么这么选

| 项 | 建议 | 理由 |
|---|---|---|
| 规格 | 2 vCPU / **2 GB** 内存 / 30 GB 系统盘 / 200 Mbps | 1 GB 能跑（本包已按 1 GB 调参并强制 2 GB swap），但 2 GB 明显更省心 |
| 系统 | Debian 12 (bookworm) | 默认不装 snapd、不带成套云原生组件，systemd 与内存占用比 Ubuntu 小一截；软件包版本保守但稳定 |
| 网络 | 1 个公网 IPv4 | 演示够用；域名可选（备案另说） |

**安全组（只开 80/443，这是第一道也是最有效的一道门）：**

| 端口 | 授权对象 | 说明 |
|---|---|---|
| 80/tcp | 0.0.0.0/0 | HTTP |
| 443/tcp | 0.0.0.0/0 | HTTPS（上 TLS 后才有服务，先开着不影响） |
| 22/tcp | **你的办公/家庭公网 IP** | 不要 0.0.0.0/0；服务器上**不要**改 sshd 配置（改错会把自己锁在门外） |
| 8080 | **不开放** | 后端只监听 127.0.0.1，安全组再挡一层，双保险 |

**为什么必须建 2 GB swap**：1 GB 内存要同时塞下 JVM（堆 384 MB + 元空间 + 代码缓存）、
PostgreSQL（shared_buffers 192 MB）、nginx 和系统本身，峰值一定会超。没有 swap 时内核只能
OOM Kill（通常先杀 JVM），表现是「服务半夜自己重启」；有 swap 则退化成变慢，而不是直接被杀。
`01-install.sh` 会自动建 swap 并把 `vm.swappiness` 设成 10（swap 当安全垫，不当内存用）。

**30 GB 系统盘的占用预算**（够用，但要知道钱花在哪）：

| 项目 | 大约占用 |
|---|---|
| 系统 + apt 已装软件 | 2~3 GB |
| JDK（Temurin 解压后） | 300~400 MB |
| PostgreSQL 程序 + 数据库（含 40 张图片二进制约 7 MB） | 500 MB 以内 |
| 后端 jar（另留一份 `.bak` 回滚） | 2 × 40 MB |
| 前端 dist + `.gz` | 5 MB 以内 |
| **swap 文件** | **2 GB**（固定占用，别删） |
| journal 日志 | 上限 200 MB（01 里已限） |

> 也就是说：不要把 `pg_dump` 备份长期堆在系统盘上（第七节），盘满时 PostgreSQL 会拒绝写入。


---

## 二、本地构建与上传（在你自己的电脑上做）

> 服务器只有 1~2 GB 内存，**不要在服务器上 `mvn package` / `npm install`**（会 OOM）。
> 构建与上传都在本地完成。

```bash
# 1) 后端打包（Windows 上打包前先停掉正在运行的实例，否则文件被锁）
cd volunteer-cert-portrait-server
mvn -B package -DskipTests
# 产物：vcp-boot/target/vcp-boot-1.0.0.jar（fat jar，约 40 MB）

# 2) 前端构建
cd ../volunteer-cert-portrait-web
npm run build          # 产物在 dist/；.env.production 里 VITE_USE_MOCK=false，走真实后端

# 3) 本地预压缩（可选：服务器上 03-deploy.sh 还会再压一次，本地压好可少占服务器 CPU）
cd ..
python deploy/precompress.py
```

上传（`<user>` 是 ECS 登录用户，`<IP>` 是公网 IP；Windows 自带的 OpenSSH 客户端就有 scp）：

```bash
ssh <user>@<IP> 'mkdir -p /tmp/vcp-upload && rm -rf /tmp/vcp-upload/dist'
scp volunteer-cert-portrait-server/vcp-boot/target/vcp-boot-1.0.0.jar <user>@<IP>:/tmp/vcp-upload/
scp -r volunteer-cert-portrait-web/dist <user>@<IP>:/tmp/vcp-upload/
#   落地结果：/tmp/vcp-upload/vcp-boot-1.0.0.jar 与 /tmp/vcp-upload/dist/index.html
```

再把「脚本 + SQL」放到服务器（两种方式，任选；关键是保持
`<仓库根>/sql/` 与 `<仓库根>/deploy/small-server/` 的相对位置）：

```bash
# 方式 A（推荐）：服务器上直接 clone，以后 git pull 就能更新
ssh <user>@<IP>
git clone <你的仓库地址> ~/vcp && cd ~/vcp/deploy/small-server

# 方式 B：只传需要的目录（本机执行）
scp -r sql deploy/small-server deploy/precompress.py <user>@<IP>:~/vcp/
#   服务器上的布局：~/vcp/sql/、~/vcp/deploy/small-server/、~/vcp/deploy/precompress.py
```

> 传参说明：脚本里的环境变量要用
> `sudo bash -c 'VCP_DB_PASSWORD=xxx bash 02-database.sh'` 这种写法传。
> 直接 `sudo VAR=1 bash xx.sh` 在 Debian 默认 sudoers（`env_reset`）下可能被拦。

---

## 三、三步部署

以下三条命令都在 `deploy/small-server/` 目录下执行（路径按你自己的实际位置改）：

```bash
# 第 1 步：装 JDK 21 / PostgreSQL 18 / nginx、建 swap、设时区（约 3~8 分钟，JDK 下载约 190 MB）
sudo bash 01-install.sh
#   非阿里云机器或内网源不可达时：
#   sudo bash -c 'USE_ALIYUN_MIRROR=0 bash 01-install.sh'

# 第 2 步：建库建角色 + 灌数据（会交互询问数据库口令；全流程约 1~3 分钟）
sudo bash 02-database.sh
#   非交互：sudo bash -c 'VCP_DB_PASSWORD=你的口令 bash 02-database.sh'
#   库已有数据、确实要重建（会清空）：sudo bash -c 'RESET_DB=1 bash 02-database.sh'

# 第 3 步：放 jar/dist、装 systemd 与 nginx、启动、验收（约 1~2 分钟）
sudo bash 03-deploy.sh
```

每一步在做什么、会问什么、大概多久：

| 步骤 | 关键动作 | 交互 | 耗时 |
|---|---|---|---|
| `01-install.sh` | 换 apt 源、建 swap（dd 2 GB 较慢）、装 JDK 21（Temurin 下载约 190 MB）、装 PG 18 与 nginx、写 PG 参数并重启 | 无 | 3~8 分钟 |
| `02-database.sh` | 建角色/库，跑 10 个 SQL（`13` 号约 10 MB 最慢），跑 `09` 自检并断言 | **问数据库口令**（`read -s`，输入两遍） | 1~3 分钟 |
| `03-deploy.sh` | 建 `vcp` 用户、放 jar/dist、生成 `vcp.env`、装并启动 systemd、预压缩、装 nginx、跑 8 项验收 | 无（口令默认沿用 `vcp.env`） | 1~2 分钟 |

常用开关（都是环境变量，写在脚本头部的默认值也可以直接改）：

| 变量 | 默认 | 说明 |
|---|---|---|
| `USE_ALIYUN_MIRROR` | `1` | apt 换阿里云内网镜像；非阿里云机器设 `0` |
| `PGDG_MIRROR` | `aliyun` | PostgreSQL 源：`aliyun`（内网，不可达自动退公网）/ `official`（PGDG 官方脚本）/ `tuna`（TUNA 不镜像 PGDG，会自动转 aliyun） |
| `JDK_SOURCE` | `auto` | `auto`=先试 bookworm-backports，失败回退 Temurin 压缩包；`temurin`=直接下压缩包；`backports`=只用官方包 |
| `NGINX_FROM_OFFICIAL` | `0` | `1`=用 nginx.org 官方源（版本新，国内下载慢） |
| `SWAP_SIZE_MB` | `2048` | `0`=不建 swap（1 GB 机器强烈不建议） |
| `VCP_DB_PASSWORD` | 无 | 数据库口令；不传就交互输入（脚本里不含任何口令） |
| `RESET_DB` | `0` | 库非空时是否强制重建（`02` 里的 `02_schema.sql` 会 DROP TABLE） |

---

## 四、验收清单（逐条 curl + 期望结果）

在服务器上执行（`<IP>` 换成公网 IP 时即是从本地验证外网访问）：

```bash
# 1) 首页
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1/                    # 200
# 2) 健康检查
curl -s http://127.0.0.1/nginx-health                                        # ok
# 3) SPA 深层路由应回退成 index.html
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1/student/profile    # 200
# 4) 学院下拉：10 个学院
curl -s http://127.0.0.1/api/v1/auth/colleges | grep -o '"label"' | wc -l    # 10
# 5) 三个测试账号登录，均 code=0
for u in student admin org_admin; do
  curl -s -X POST http://127.0.0.1/api/v1/auth/login -H 'Content-Type: application/json' \
       -d "{\"username\":\"$u\",\"password\":\"123456\"}" | grep -o '"code":0' | head -1
done
# 6) 看板数字：2349 条报名 / 9163.0 小时（admin 的 token 换成上一步返回的）
curl -s http://127.0.0.1/api/v1/analytics/dashboard -H 'Authorization: Bearer <token>' | grep -o '2349\|9163.0'
# 7) 图片接口（活动图片存在数据库里，不经静态目录）
curl -s -D - -o /dev/null http://127.0.0.1/api/v1/attachments/1/content | grep -iE '^(HTTP|content-type|etag)'
#    HTTP/1.1 200 OK / Content-Type: image/jpeg / ETag: "...."，带 If-None-Match 复请求应 304
# 8) 8080 只在本机（从外网 curl <IP>:8080 应当连不上）
ss -ltn | grep 8080                                                           # 只有 127.0.0.1:8080
# 9) 数据库自检（只读）：期望「检查项总数 20，违规合计 0」
cd ~/vcp        # 02-database.sh 就是从这里跑的，sql/ 在仓库根下
PGPASSWORD='<口令>' psql -h 127.0.0.1 -U vcp -d volunteer_cert_portrait \
    -f sql/09_consistency_check.sql | tail -3
```

期望的演示数据口径：10 学院 / 1000 学生 / 10 场活动 / 2349 条报名 / 9163.0 小时 /
40 张活动图片（attachment id 1~40，`GET /api/v1/attachments/{id}/content`）。
测试账号：`student`（林书瑶，五星 41.0 小时）、`admin`（高志远）、`org_admin`（赵启明），
口令均 `123456`；**学生用户名=学号**（如 `202201010001`）。
`03-deploy.sh` 会把上面 1~8 条自动跑一遍并统计通过/失败数（失败会退出码 1）。

---

## 五、内存与调参

两档参数对照（`01`/`03` 会按 `/proc/meminfo` 自动选择，**不需要手工改**）：

| 参数 | 1 GB 机器 | 2 GB 机器 | 位置 |
|---|---|---|---|
| JVM 堆 `-Xmx` | **384m** | 512m | `/opt/vcp/vcp.env` 的 `JAVA_OPTS` |
| JVM `-Xms` / 元空间 | 256m / 128m | 256m / 128m | 同上 |
| JVM 线程栈 | 512k | 512k | 同上 |
| Tomcat 线程 / 排队 / 连接 | 60 / 100 / 300 | 60 / 100 / 300 | `vcp.service`（可提到 100 线程） |
| Hikari 连接池 | 8（最小空闲 2） | 8（最小空闲 2） | `vcp.service`（可提到 12） |
| PG `shared_buffers` | 192MB | 320MB | `postgres-tuning.conf` 两档 |
| PG `max_connections` | 40 | 50 | 同上 |
| PG `work_mem` | 4MB | 6MB | 同上 |
| swap / swappiness | 2 GB / 10 | 2 GB / 10 | `/etc/sysctl.d/99-vcp.conf` |

> 为什么必须覆盖 Tomcat 与 Hikari 的默认值：`application.yml` 里的 300 线程 / 1024 连接 /
> 池 20 是**按开发机 32 GB 内存**调的（那份注释写明了），照抄到 1 GB 上会多吃掉几百 MB。

**如果确实要放宽**（换成 2 GB 机器之后，或压测发现排队明显）：改这三处并重启服务 ——

| 想要的效果 | 改哪里 | 建议值 |
|---|---|---|
| 提高并发（更多浏览器会话） | `vcp.service` 的 `SERVER_TOMCAT_THREADS_MAX` | 60 → 100（0.5 MB 栈 × 40 条 ≈ 20 MB） |
| 提高数据库并发 | `vcp.service` 的 `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | 8 → 12（PG 每个连接一个进程，别超过 20） |
| 加大 JVM 堆 | `/opt/vcp/vcp.env` 的 `JAVA_OPTS` 里 `-Xmx` | 512m → 768m（2 GB 机器且 `free -h` 的 available 长期 > 700 MB 时） |

改完记得 `sudo systemctl daemon-reload && sudo systemctl restart vcp`（只改 vcp.env 则不用 daemon-reload），
并用 `systemctl show vcp -p ExecStart` 确认参数真的进了启动命令行。

怎么看内存与进程：

```bash
free -h                 # 看 available 那一列（不是 free）；swap 用量长期 > 一半 = 内存真不够
systemctl status vcp    # Active: active (running)；RSS 会显示主进程占用
ps -eo pid,rss,cmd --sort=-rss | grep -E 'vcp-boot|postgres' | head   # 谁最占内存
journalctl -u vcp -n 100    # 看后端日志（-f 实时跟随；OOM 会在 dmesg 里）
systemctl show vcp -p ExecStart    # 核对 JAVA_OPTS 是否被展开进了启动命令
```

---

## 六、日常运维

```bash
# 服务
sudo systemctl restart vcp            # 重启后端
sudo systemctl status vcp             # 状态
sudo journalctl -u vcp -n 100         # 日志（-f 跟随）

# 改了 vcp.service 或 .env 之后
sudo systemctl daemon-reload && sudo systemctl restart vcp
# 只改了 /opt/vcp/vcp.env → 不需要 daemon-reload，restart 即可

# 后端发版：本地打包 → 传 jar 到 /tmp/vcp-upload/ → 重跑第三条命令即可
sudo bash 03-deploy.sh                # 会备份旧 jar 为 .bak 并重启
# 或用 .bak 回滚：
sudo cp /opt/vcp/vcp-boot-1.0.0.jar.vcp.bak /opt/vcp/vcp-boot-1.0.0.jar && sudo systemctl restart vcp

# 前端发版：本地 npm run build → 传 dist → 覆盖 /var/www/vcp → 预压缩 → reload
ssh <user>@<IP> 'rm -rf /tmp/vcp-upload/dist'        # 先删掉旧的，避免 scp 套成 dist/dist
scp -r volunteer-cert-portrait-web/dist <user>@<IP>:/tmp/vcp-upload/
sudo cp -a /tmp/vcp-upload/dist/. /var/www/vcp/ && sudo chown -R vcp:vcp /var/www/vcp
sudo vcp-precompress                   # = python3 /opt/vcp/precompress.py --dist /var/www/vcp
sudo systemctl reload nginx

# nginx
sudo nginx -t && sudo systemctl reload nginx
```

> `index.html` 刻意不缓存、带 hash 的 chunk 长缓存一年；如果发版后页面白屏，
> 先比对 `index.html` 里引用的 chunk 文件名与 `/var/www/vcp/assets/` 下的文件是否一致
> （不一致 = dist 没传全）。

---

## 七、备份与恢复

数据库里包含活动图片二进制（`attachment.file_data`），所以**备份数据库就是备份全部数据**，
服务器上没有 uploads 目录需要单独备份。

```bash
# 备份（-Fc = 自定义压缩格式，恢复时用 pg_restore；整库约几 MB~几十 MB）
PGPASSWORD='<口令>' pg_dump -h 127.0.0.1 -U vcp -Fc volunteer_cert_portrait \
    -f /root/vcp-$(date +%F).dump
# 或本机 peer 认证（免口令）：sudo -u postgres pg_dump -Fc volunteer_cert_portrait > /root/vcp.dump

# 恢复（以应用角色恢复，对象属主才是 vcp，应用才写得进去）
PGPASSWORD='<口令>' pg_restore -h 127.0.0.1 -U vcp -d volunteer_cert_portrait --clean --if-exists /root/vcp-2026-09-27.dump
# 库被删了的话先重建：CREATE DATABASE volunteer_cert_portrait OWNER vcp;
```

**异地备份一句话**：把 dump 文件用 `ossutil cp` 传到阿里云 OSS（或 `scp` 拉回本地），
否则机器一挂/系统盘一坏，备份和数据库一起没了；OSS 走内网端点不花流量费。

---

## 八、故障排查表

| 症状 | 排查 | 处置 |
|---|---|---|
| 服务半夜自己重启/挂了 | `dmesg -T \| grep -iE 'killed process\|oom'`、`free -h` | 有 OOM 记录就说明内存不够：确认 2 GB swap 在、`-Xmx` 是 384m/512m、Tomcat 线程 60；仍频繁就升配 2 GB |
| 外网能直连 `<IP>:8080` | `ss -ltnp \| grep 8080`、`grep SERVER_ADDRESS /opt/vcp/vcp.env` | 应为 `127.0.0.1:8080`；否则改 vcp.env + 确认 `vcp.service` 带 `--server.address=127.0.0.1`，再查安全组是否放行了 8080 |
| 访问 `/api/xxx` 返回 200 但内容是 index.html | 说明 `/api/` 被 SPA 回退吃了，反代没生效 | `nginx -t`、`ls -l /etc/nginx/sites-enabled/`（应有 vcp、无 default）、检查 `proxy_pass http://vcp_backend;` **结尾没有斜杠** |
| 图片接口 404 | `psql ... -c "SELECT count(*) FROM attachment"` | 12/13 号脚本没跑全（要 40 条且有 `file_data`）。只缺图片时不用重建库，单独补跑即可：<br>`PGPASSWORD='<口令>' psql -h 127.0.0.1 -U vcp -d volunteer_cert_portrait -v ON_ERROR_STOP=1 -f sql/11_activity_images.sql -f sql/12_activity_images_demo.sql -f sql/13_attachment_binary.sql` |
| 登录失败（`code` 不是 0） | 账号是否存在、口令是否 123456 | 老库要跑 `sql/08_password_bcrypt.sql`（新库不需要）；库里口令应是 `$2a$10$` 开头的密文 |
| 后端启动失败，日志报连不上数据库 | `pg_isready -h 127.0.0.1`；`grep -v '^#' /etc/postgresql/18/main/pg_hba.conf \| grep 127.0.0.1` | pg_hba 应有 `host all all 127.0.0.1/32 scram-sha-256`；改完 `systemctl reload postgresql`；再核对 `/opt/vcp/vcp.env` 的口令（注意 YAML/env 里口令要引号） |
| 网页 502 Bad Gateway | `systemctl status vcp`、`journalctl -u vcp -n 50` | 后端没起来或崩了：常见是 `-Xmx` 太大被 OOM、vcp.env 口令不对；`curl http://127.0.0.1:8080/api/v1/auth/colleges` 直接验后端 |
| 磁盘满 | `df -h`、`du -sh /var/log /root/*.dump /var/lib/postgresql` | journald 已限 200 MB；删旧 dump；`journalctl --vacuum-size=100M`；PG 的 WAL 在 `max_wal_size` 内属正常 |
| 页面白屏 / 样式丢失 | 浏览器 F12 看 404 的 js/css 文件名 | dist 没传全或 `index.html` 被缓存：重新传 dist + `vcp-precompress` + `reload nginx`，浏览器强制刷新 |
| 前端请求 401 / 被踢回登录页 | token 过期（30 天）或换了域名 | 重新登录；此项目 token 走 `Authorization: Bearer`，不需要 Cookie 配置 |
| `nginx -t` 报 `unknown directive "gzip_static"` | `nginx -V \| grep gzip_static` | `apt-get install -y nginx-full` 后恢复配置里那行；03-deploy.sh 在缺模块时会自动注释掉它 |

---

## 九、这 7 个文件分别干什么

| 文件 | 关键点 |
|---|---|
| `01-install.sh` | 8 步：环境检查 → apt 源 → 基础工具 → 时区 → swap → JDK 21 → PG 18 → nginx+加固。幂等；改系统文件前备份 `.vcp.bak` |
| `02-database.sh` | 建 `vcp` 角色与 `volunteer_cert_portrait` 库；按硬顺序跑 02→03→05→06→04→10→11→12→13→14；跑只读自检 `09` 并**断言**「检查项总数 20，违规合计 0」，不符退出码 1 |
| `03-deploy.sh` | 建 `vcp` 系统用户、放 jar/dist、生成 `vcp.env`(600)、装 systemd、启动并等 8080 就绪、预压缩、装 nginx 站点、跑 8 项验收 |
| `vcp.service` | 小内存 JVM 参数（`-Xmx` 由 vcp.env 按内存覆盖）、Tomcat/Hikari 覆盖、`SuccessExitStatus=143`、`ProtectSystem=full` 等四件套加固 |
| `postgres-tuning.conf` | 1G/2G 两档参数，用 `>>>PROFILE-xx>>>` 标记分隔，`01-install.sh` 按内存提取一档写入 `conf.d/` |
| `nginx-vcp.conf` | 反代 + SPA 回退 + `gzip_static` + `client_max_body_size 6m` + `/nginx-health`；末尾是 443/TLS 模板（含阿里云证书与 certbot 两条路） |

与仓库其它文件的关系：`sql/` 目录由 `02-database.sh` 读取（不复制、不修改）；
`deploy/precompress.py` 被复制到 `/opt/vcp/precompress.py`（服务器上没有仓库目录树，
所以预压缩要用 `--dist /var/www/vcp`，快捷命令 `vcp-precompress` 已包好）。

---

## 十、诚实声明与需要人工决定的事

**没在真机上跑过。** 本部署包是在一台 **Windows** 开发机上按 Debian 12 的文档与镜像目录写出来的，
**没有在真实 ECS 上实跑**：脚本语法、嵌套的引号与 heredoc 是逐行检查过的，但
`apt`/`systemd`/`pg_hba` 这些系统级行为只有到真机上才能确认。执行时若报错，**请把完整输出贴回来**
（尤其是 `01-install.sh` 的 apt 输出、`journalctl -u vcp -n 100`、`nginx -t`），按报错修一轮即可。

**已经用网络实测纠正过的两处「常识」**（别照网上博客抄）：

1. **`bookworm-backports` 里没有 `openjdk-21`**（2026-09-27 实测：Debian 的
   `openjdk-21` 只在 trixie / forky / sid，没有任何 `~bpo12` 版本）。
   所以 `01-install.sh` 里 backports 只是「先试一下」，真正装上的大概率是
   **清华 TUNA 的 Temurin 21 压缩包**（约 190 MB，解到 `/opt/jdk-21`，用 update-alternatives 挂成
   `/usr/bin/java`）。若哪天 backports 真收录了，脚本会自动走官方包，不用改。
2. **清华 TUNA 并不镜像 PGDG 的 apt 源**（实测 `mirrors.tuna.tsinghua.edu.cn/postgresql/repos/apt/`
   返回 404），国内能做 PGDG 镜像的是阿里云（`mirrors.aliyun.com/postgresql/repos/apt/`，
   ECS 内网地址 `mirrors.cloud.aliyuncs.com`）。所以 `PGDG_MIRROR=tuna` 这个取值会自动转 aliyun，
   默认值也直接是 `aliyun`。

**需要人工确认/决定的项**：

- **阿里云内网镜像域名**：脚本用 `mirrors.cloud.aliyuncs.com`（VPC 网络）写死在 `ALIYUN_HOST`，
  经典网络/非阿里云要用 `mirrors.aliyuncs.com` 或直接 `USE_ALIYUN_MIRROR=0`。请按你的 ECS
  网络类型确认一次；`01-install.sh` 的 PGDG 源还有一个「内网不可达自动退公网」的兜底。
- **域名 / 备案 / 证书**：用域名 + HTTPS 需要域名备案（阿里云）与证书（免费证书或 certbot），
  本包只给了 443 模板（`nginx-vcp.conf` 末尾），没替你申请。
- **是否用 OSS 存备份**：见第七节，一句话的事，但需要你决定存哪、用什么账号。
- **是否把机器升到 2 GB**：1 GB 档的参数都写好了，但演示当天如果有并发（比如老师同学一起点），
  2 GB 会稳得多。
- **nginx 版本**：默认 Debian 自带 1.22（够用）；想用新版设 `NGINX_FROM_OFFICIAL=1`，
  但 nginx.org 在国内下载可能很慢。
- **`-Xmx384m/512m` 与 PG `shared_buffers` 的微调**：如果 `free -h` 显示 swap 长期吃紧，
  优先降 `-Xmx`；如果数据库查询慢，再考虑 2 GB 档参数（现在是自动按内存选的）。
