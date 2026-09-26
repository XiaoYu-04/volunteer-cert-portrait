# 高校志愿服务时长认证与公益画像数据分析系统

> Volunteer Service Certification and Public Welfare Portrait Analysis System · 项目代号 **VCP**

面向高校的志愿服务**全流程管理系统**：活动发布 → 报名审核 → 签到签退 → 服务时长记录 → 多角色时长审核 → 学生公益画像，六个环节构成闭环。

| 组成 | 技术栈 |
|---|---|
| 后端 | Java 21 + **Spring Boot 4.1.1** + MyBatis-Plus 3.5.17 + Sa-Token 1.46.0 |
| 数据库 | PostgreSQL 18.6（云库；建表脚本按 16+ 编写） |
| 前端 | Vue 3.5 + Vite 8 + Pinia 4 + Vue Router 5 + Axios 1.20 + ECharts 6（Node `^22.18.0 \|\| >=24.12.0`） |
| 接口文档 | Knife4j Next，后端起来后访问 `/doc.html` |

**三种角色**

| 角色编码 | 名称 | 能做什么 |
|---|---|---|
| `STUDENT` | 学生 | 注册登录、浏览活动、报名、签到签退、查看本人时长与公益画像、收通知 |
| `ORG_ADMIN` | 组织管理员 | 发布活动（含图片）、审核报名、管理签到、提交服务时长（可批量） |
| `SCHOOL_ADMIN` | 学校管理员 | 审核组织资质、时长终审、用户与角色管理、学院/分类/公告管理、全校数据看板 |

**系统能做什么**

- **活动发布与报名**：组织按分类发布活动（封面 + 图文说明），学生浏览、报名，组织审核通过 / 驳回。
- **签到签退**：活动开始前 30 分钟开放签到、结束后 30 分钟内签退，漏签可由组织管理员人工修正。
- **时长认定与审核**：组织提交服务时长（可批量）→ 学校管理员终审，通过后累加进学生累计时长。
- **公益画像与看板**：按累计时长定 6 档公益等级、生成 8 类标签；学校端由 10 个聚合接口驱动数据看板。

接口前缀统一 `/api/v1/`，响应外壳 `{ code, message, data }`（成功 `code = 0`）。

## 一、5 分钟跑起来

| 依赖 | 版本 | 用途 |
|---|---|---|
| JDK | 21 | 跑后端（本仓库按 Java 21 构建） |
| Maven | 3.9+ | 打包后端 |
| Node.js | `^22.18.0 \|\| >=24.12.0` | 跑前端（见 `package.json` 的 `engines`） |
| Python 3 | 任意 3.x | 可选：Nginx 的 `.gz` 预压缩脚本 |

### 第 1 步：建库建表

**演示库已经内置**：云上 PostgreSQL 18.6 的连接串与口令（`123456`）写在 `application.yml` 里，clone 下来**零配置**即可连，这一步可以跳过。

要自己建一套库时，按顺序执行 `sql/` 脚本（完整顺序与说明见「五、数据库脚本」）：

```bash
psql -U postgres -h <主机> -p <端口> -f sql/01_create_database.sql
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f sql/02_schema.sql
# …其余脚本见「五、数据库脚本」的完整顺序
```

> ⚠️ 明文口令写进配置是**刻意的演示口径**（任何人 clone 即可连库），代价是仓库 public 等于口令公开，这个库只能放演示数据；**答辩结束后必须轮换**，生产部署必须用环境变量 `VCP_DB_PASSWORD` 覆盖。

### 第 2 步：启动后端

```bash
cd volunteer-cert-portrait-server
mvn package -DskipTests
java -jar vcp-boot/target/vcp-boot-1.0.0.jar
```

- 环境要求：JDK 21 + Maven；**不需要本地装 PostgreSQL**。
- 启动后监听 **8080**，接口文档在 <http://localhost:8080/doc.html>。
- 重新打包前先停掉正在运行的后端：运行中的进程会锁住 `target/vcp-boot-1.0.0.jar`，`repackage` 会报 `Unable to rename ... .jar.original`。
- 默认是**开发口径**（文档页免登录、跨域允许所有来源、MyBatis 全量打印 SQL）；生产口径见「六、生产部署」。

### 第 3 步：起前端（二选一）

**开发模式** —— 适合改前端、和后端联调：

```bash
cd volunteer-cert-portrait-web
npm install
npm run dev        # http://localhost:5173
```

Vite 把 `/api` 代理到 `http://127.0.0.1:8080`；要和别人联调时，在 `volunteer-cert-portrait-web/.env.local` 里写 `VITE_API_TARGET=http://<对方机器>:8080` 覆盖（该文件不入库）。

**生产模式** —— 适合部署给别人访问：

```bash
cd volunteer-cert-portrait-web
npm run build
python deploy/precompress.py    # 生成 .gz 预压缩文件（需要 Python 3）
# 再用 deploy/nginx-win 的 Nginx 把 dist/ 跑起来，见「六、生产部署」
```

生产构建默认走真实后端（`.env.production` 里 `VITE_USE_MOCK=false`），**必须有 Nginx（或其它反代）把 `/api` 转到 8080**，否则页面能打开但全是空数据。

## 二、账号与密码

所有演示账号的登录口令统一是 **`123456`**。库里 `sys_user.password` 存的是它的 **BCrypt 密文**，明文不落库。

### 2.1 三个测试账号

| 角色 | 用户名 | 密码 | 姓名 | 说明 |
|---|---|---|---|---|
| 学生 | `student` | `123456` | 林书瑶 | 计算机学院 / 软件工程 / 软件2201，学号 `202201010001`；**41.0 小时 / 五星志愿者 / 7 场活动**，标签：热心志愿者、长期坚持型、大型活动型、助老服务型 |
| 组织管理员 | `org_admin` | `123456` | 赵启明 | 绑定组织「计算机学院青年志愿者协会」 |
| 学校管理员 | `admin` | `123456` | 高志远 | 全部管理权限（时长终审、组织审核、用户管理、看板） |

### 2.2 公益等级：五星 → 一星（每档一个真实学生账号）

等级按**累计有效时长**（小时，演示尺度）分档：

| 等级 | 阈值 |
|---|---|
| 五星志愿者 | ≥ 40 |
| 四星志愿者 | 20 ~ 40 |
| 三星志愿者 | 10 ~ 20 |
| 二星志愿者 | 6 ~ 10 |
| 一星志愿者 | 3 ~ 6 |
| 普通志愿者 | < 3 |

下表每档一个**真实存在于演示库**的账号（用户名 = 学号，密码统一 `123456`），均按「参与场次多、标签全」从库里现选：

| 等级 | 用户名（学号） | 密码 | 姓名 | 学院 | 累计时长 | 标签 |
|---|---|---|---|---|---|---|
| 五星志愿者 | `202308020004` | `123456` | 钱明辉 | 生命科学学院 | 41.0 | 热心志愿者,长期坚持型,大型活动型,助老服务型 |
| 四星志愿者 | `202310020004` | `123456` | 邱天佑 | 医学院 | 36.0 | 热心志愿者,长期坚持型,大型活动型,助老服务型 |
| 三星志愿者 | `202303010010` | `123456` | 周启宸 | 经济管理学院 | 19.0 | 热心志愿者,大型活动型,助老服务型 |
| 二星志愿者 | `202408020005` | `123456` | 田立行 | 生命科学学院 | 8.0 | 热心志愿者,大型活动型,文化传播型 |
| 一星志愿者 | `202302020025` | `123456` | 罗欣蕊 | 电子信息学院 | 5.0 | 热心志愿者,校园服务型,环保行动型 |
| 普通志愿者 | `202408010016` | `123456` | 司马欣瑜 | 生命科学学院 | 0.0 | 校园服务型,助老服务型 |

> 测试学生 `student`（学号 `202201010001`）本身也是五星：41.0 小时 / 7 场活动。

### 2.3 10 名学校管理员 + 10 名组织管理员

口令统一 `123456`。除了上面的 `admin` / `org_admin`，演示库还各有 10 名学校管理员与组织管理员：

| 用户名 | 姓名 | 角色 |
|---|---|---|
| `school_admin_01` | 陈国华 | 学校管理员 |
| `school_admin_02` | 李慧敏 | 学校管理员 |
| `school_admin_03` | 王志远 | 学校管理员 |
| `school_admin_04` | 周文娟 | 学校管理员 |
| `school_admin_05` | 吴建华 | 学校管理员 |
| `school_admin_06` | 郑晓峰 | 学校管理员 |
| `school_admin_07` | 孙丽萍 | 学校管理员 |
| `school_admin_08` | 黄志强 | 学校管理员 |
| `school_admin_09` | 徐雅琴 | 学校管理员 |
| `school_admin_10` | 马文博 | 学校管理员 |

| 用户名 | 姓名 | 角色 |
|---|---|---|
| `org_admin_01` | 钱静怡 | 组织管理员 |
| `org_admin_02` | 孙浩然 | 组织管理员 |
| `org_admin_03` | 李思远 | 组织管理员 |
| `org_admin_04` | 周慧敏 | 组织管理员 |
| `org_admin_05` | 吴天成 | 组织管理员 |
| `org_admin_06` | 郑雨欣 | 组织管理员 |
| `org_admin_07` | 王嘉豪 | 组织管理员 |
| `org_admin_08` | 冯雪梅 | 组织管理员 |
| `org_admin_09` | 韩立新 | 组织管理员 |
| `org_admin_10` | 曹文轩 | 组织管理员 |

### 2.4 学生登录：用户名就是学号

学生的**用户名就是学号**，登录时在用户名框输入学号即可（后端对纯数字输入先按 `student_info.student_no` 反查，查不到再按用户名兜底）。例如学号 `202308020004` + 口令 `123456`，和输入 `student` 是同一套流程。

## 三、用户使用示例

> 页面路径都是前端路由：开发模式是 `http://localhost:5173/...`，生产模式是 Nginx 的 80 端口。curl 示例默认后端在 `http://localhost:8080`；登录响应里的 `data.token` 是令牌，后续请求带 `Authorization: Bearer <token>`。

### 3.1 学生

1. **注册**（`/register`）→ 填用户名、姓名、学号、密码，**学院必须从下拉里选**（下拉数据来自字典 `college`，共 10 个学院）→ 注册成功即视为登录，直接进 `/student/home`。
2. **浏览活动**（`/student/activities`）→ 可按关键字、分类筛选；点进详情（`/student/activities/{id}`）能看到封面图与图文说明。
3. **报名**（活动详情页点「我要报名」）→ 生成一条「待审核」报名，`/student/signups` 能看到状态；审核结果会收到站内通知。
4. **签到 / 签退**（`/student/signups` 里操作）→ **活动开始前 30 分钟**开放签到，**结束后 30 分钟内**必须签退；没签退会被判缺勤，该条时长按活动预计时长计（设备异常时可请组织管理员人工修正）。
5. **我的时长**（`/student/durations`）→ 按「待审核 / 已通过 / 已驳回」三态筛选，驳回的能看到原因。
6. **公益画像**（`/student/portrait`）→ 公益等级 + 8 类标签 + 时长与参与趋势。
7. **通知公告**（`/student/notifications`）→ 报名通过、时长认定结果、系统公告都在这里，点开可标记已读。

```bash
# 登录（把 student 换成学号 202308020004 效果一样）
curl -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d "{\"username\":\"student\",\"password\":\"123456\"}"

# 用上一步返回的 token 查自己的公益画像
curl http://localhost:8080/api/v1/portraits/me -H "Authorization: Bearer <token>"
```

### 3.2 组织管理员

1. **登录**（`org_admin`）→ 落地 `/org/dashboard`，看本组织的活动、报名、签到与时长概览。
2. **发布活动**（`/org/activities/new`）→ 填名称、分类、日期时间、地点、名额、单人时长、报名截止、联系方式，上传 **1 张封面 + 最多 6 张图文说明**；图片上传即入库（见「四、活动图片」），保存后出现在 `/org/activities`。
3. **审核报名**（`/org/signups`）→ 通过 / 驳回（驳回要填理由，理由会随通知发给学生）。
4. **签到管理**（`/org/attendance`）→ 查看报名者的签到签退记录，漏签可人工修正。
5. **提交服务时长**（`/org/durations`）→ 可单条提交，也可勾选多人**批量**提交（请求体 `items` 数组）；提交后是「待审核」，等学校管理员终审。

```bash
curl -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d "{\"username\":\"org_admin\",\"password\":\"123456\"}"

# 本组织的数据总览（活动 / 报名 / 签到 / 时长）
curl http://localhost:8080/api/v1/activities/org-overview -H "Authorization: Bearer <token>"
```

### 3.3 学校管理员

1. **登录**（`admin`）→ 落地 `/admin/dashboard`。
2. **审核组织资质**（`/admin/orgs`）→ 通过 / 驳回待审核组织，结果通知组织联系人。
3. **时长终审**（`/admin/durations`）→ 对组织提交的时长逐条通过 / 驳回；通过后累加进学生累计时长，并影响公益等级。
4. **用户管理**（`/admin/users`）→ 新增用户、重置密码（`PUT /api/v1/system/users/{id}/password`）、启用 / 停用。
5. **学院管理**（`/admin/colleges`）→ 新增 / 启停 / 删除学院；注册页的学院下拉实时取这里的数据。
6. **数据看板**（`/admin/dashboard`）→ 由 10 个聚合接口驱动：`/api/v1/analytics/dashboard|overview|trend|types|colleges|orgs|profiles|audit|signin|heatmap`。
7. 另有分类管理 `/admin/categories`、角色管理 `/admin/roles`、通知公告 `/admin/notices`、操作日志 `/admin/logs`。

```bash
curl -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"123456\"}"

# 看板总览（10 个聚合接口之一）
curl http://localhost:8080/api/v1/analytics/dashboard -H "Authorization: Bearer <token>"
```

## 四、活动图片（2026-09-27 起存数据库）

- 图片二进制存在 `attachment.file_data`（`BYTEA`），同行记录 `sha256`、`content_type`、`caption`、`sort_order`。**本地和服务器都不再保留 jpg，部署时不再需要同步 `uploads/` 目录。**
- 读取：`GET /api/v1/attachments/{id}/content` —— **免登录**，带 `ETag`（内容 sha256）与 `Cache-Control: public, max-age=31536000, immutable`，命中 `If-None-Match` 返回 304。
- 上传：`POST /api/v1/attachments/images`（multipart，字段名 `file`）—— 单张 ≤ 5MB，仅 JPG / PNG，按**文件头识别真实图片**（不看扩展名），像素上限 2500 万。
- 活动封面与图文说明的地址都写成内容接口路径（`/api/v1/attachments/{id}/content`），前端 `<img>` 直接加载；Nginx 只需反代 `/api/`，不用再配 `/uploads/`。

## 五、数据库脚本

`sql/` 目录共 **13 个**建库/演示脚本，执行顺序**不可颠倒**（另有独立的性能索引脚本 `14_performance_indexes.sql`，见「七、性能测试」）：

```text
01_create_database.sql → 02_schema.sql → 03_init_data.sql
→ 05_backend_gap_fix.sql → 06_backend_gap_fix2.sql → 04_demo_data.sql
→ 10_base_and_test_accounts.sql → 11_activity_images.sql
→ 12_activity_images_demo.sql → 13_attachment_binary.sql
→ 09_consistency_check.sql（只读验收，期望「检查项总数 20、违规合计 0」）
```

| 脚本 | 内容 | 必需性 |
|---|---|---|
| `01_create_database.sql` | 建库 `volunteer_cert_portrait` | 必需 |
| `02_schema.sql` | 建表：16 张表 + 表/字段注释 + 索引（可重复执行，**会清空数据**） | 必需 |
| `03_init_data.sql` | 基础数据：3 个角色、3 个测试账号、1 个组织、6 个活动分类、7 类状态字典 | 必需 |
| `05_backend_gap_fix.sql` | 后端联调补列（第一批：字典色调、最近登录、组织档案等） | 必需，且必须在 `04` 之前 |
| `06_backend_gap_fix2.sql` | 后端联调补列（第二批：报名截止、学生性别年级、时长所属组织等） | 必需，且必须在 `04` 之前 |
| `04_demo_data.sql` | 演示数据：10 学院 / 1000 学生 / 10 场活动 / 报名 / 签到 / 时长 / 画像 | 演示与联调 |
| `10_base_and_test_accounts.sql` | 清库后的基础数据 + **学院字典 10 条** | 新环境必需，可重复执行 |
| `11_activity_images.sql` | 活动图片增量字段（`content_type` / `caption` / `sort_order`） | 必需 |
| `12_activity_images_demo.sql` | 10 场活动 × 4 张图片元数据（共 40 条） | 演示可选 |
| `13_attachment_binary.sql` | 图片二进制入库（`file_data` + `sha256`，base64 回填） | 演示可选 |
| `14_performance_indexes.sql` | 性能索引（两条 partial 索引 + 全库 `ANALYZE`） | 可选但推荐，可重复执行 |
| `09_consistency_check.sql` | 20 项一致性自检（**只读**，可重复执行） | 可选但推荐，放最后跑 |
| `08_password_bcrypt.sql` | 明文口令刷成 BCrypt 密文（不在上面主链里） | **仅老库** |

三条容易踩的顺序理由：

- **`05` / `06` 必须在 `04` 之前**：`04` 会写它们补的列（组织档案、学生性别年级、报名截止、时长所属组织等），列不存在时 `04` 整脚本报错。
- `07_demo_scale.sql` **已删除**：旧的 1500 学生放大口径并入 `04`，不再维护。
- `08_password_bcrypt.sql` **只服务老库**：把明文口令刷成 BCrypt 密文；新库的 `03` / `04` / `10` 种子口令已是密文，不用跑。

图形工具（DBeaver / pgAdmin）里执行时注意：`01` 的 `CREATE DATABASE` 不能放在事务中，单独执行。

**数据集规模（演示数据跑完后的口径）**

| 项 | 数量 |
|---|---|
| 学院字典 | 10 |
| 学生 | 1000（+1 测试学生） |
| 学校管理员 / 组织管理员 | 10 / 10 |
| 组织 | 11 |
| 活动 | 10（7 场已结束 + 3 场已发布） |
| 报名 / 签到 / 时长 | 2349 / 1814 / 1716 |
| 活动图片 | 40（10 场 × 1 封面 + 3 张内部图） |
| 累计认定时长 | 9163.0 小时 |

公益等级六档齐全：普通 302 / 一星 104 / 二星 143 / 三星 317 / 四星 131 / 五星 4。7 场已结束活动的时长是 5 + 6 + 5 + 8 + 5 + 6 + 6 = **41 小时**，刻意让「7 场全参加」刚好够到五星线。

## 六、生产部署（Nginx + 后端）

### Windows 本机（已内置 nginx 1.31.6 mainline）

用仓库自带的 `deploy/nginx-win/`（该目录就是 nginx prefix，站点配置在 `conf/nginx.conf`）：

- SPA history 回退：`try_files $uri $uri/ /index.html`，刷新深层路由不会 404；
- `/api/` 反代到 `http://127.0.0.1:8080`（不带斜杠，路径原样透传）；
- `gzip_static`：优先发送 `precompress.py` 预生成的 `.gz`；
- 带内容 hash 的静态资源 1 年 `immutable`，`index.html` 不缓存；
- `client_max_body_size 6m`（活动图片单张上限 5MB，另留表单开销）。

> 首次使用要改一处：`conf/nginx.conf` 里的 `root` 目前是作者本机的绝对路径，**必须改成你自己的 `volunteer-cert-portrait-web/dist` 路径**（Windows 下用正斜杠，例如 `C:/Users/you/.../dist`）。

```powershell
cd deploy\nginx-win
.\nginx.exe -t        # 校验配置
start .\nginx.exe     # 启动（后台）
.\nginx.exe -s reload # 平滑重载（改配置或重发前端后）
.\nginx.exe -s quit   # 优雅停止
taskkill /IM nginx.exe /F   # 进程残留时的兜底
```

**改完前端要三连**：`npm run build` → `python deploy/precompress.py` → `.\nginx.exe -s reload`。

启动后可这样验一把：`curl http://localhost/nginx-health` 应返回 `ok`；`curl -i http://localhost/api/v1/activities?page=1&pageSize=1` 应返回 JSON（未登录会得到 `{ "code": 20001 }`，也说明反代通了）。注意生产用 Nginx 时 `/doc.html` **不经过 `/api/` 反代**，会被 SPA 回退成前端首页，要核对文档页请直连 `http://127.0.0.1:8080/doc.html`。

还可以用仓库自带的验收脚本逐项实跑（需要 PowerShell 7）：

```powershell
pwsh deploy/verify-deploy.ps1 -SiteUrl http://127.0.0.1 -ProdProfile -ExpectedSignups 2349 -ExpectedHours 9163.0
```

### Linux 服务器

> **阿里云 ECS + Debian 12（1~2 GB 内存）请直接看 [`deploy/small-server/README.md`](deploy/small-server/README.md)** ——
> 那套是给这台机器量身写的：三个脚本（装环境 / 建库 / 部署）+ systemd 单元 + PostgreSQL 小内存调参 +
> nginx 站点配置，含验收清单与故障排查，照抄即可。

参考仓库里的 `deploy/nginx.conf` 与 [docs/部署文档.md](docs/部署文档.md)（含 systemd 单元 `deploy/vcp.service`、验收脚本 `deploy/verify-deploy.ps1`、故障排查表）。两条硬要求：

```bash
# 1) 生产启动必须带 prod profile：否则文档页免登录、跨域放开、SQL 全量打印这三项收紧都不生效
java -jar vcp-boot/target/vcp-boot-1.0.0.jar --spring.profiles.active=prod
# 2) 生产口令必须外置覆盖（仓库默认的 123456 仅供演示）
export VCP_DB_PASSWORD=<真实口令>
```

## 七、性能测试

`deploy/perf/loadtest.java` —— Java 21 **单文件、零依赖**（用 `java` 直接跑源码）的压测工具：

```bash
java deploy/perf/loadtest.java --base http://127.0.0.1:8080 --users 50 --seconds 30 --scenario mixed
```

| 参数 | 含义 |
|---|---|
| `--base` | 目标地址，默认 `http://127.0.0.1:8080` |
| `--users` | 并发用户数 |
| `--seconds` | 持续时间（秒） |
| `--scenario` | `read`（只读列表/聚合）、`write`（报名、签到等写接口）、`mixed`（混合） |

结果里重点看 **RPS**（吞吐）与 **p95 / p99**（长尾延迟）：p99 明显拉开说明有慢查询或锁竞争，先看数据库索引。`deploy/perf/README.md` 给了等价的 k6 脚本与读表口径；数据库侧的优化索引见 `sql/14_performance_indexes.sql`。

## 八、常见问题（FAQ）

1. **连不上数据库？** 按「口令 → 工作目录 → 网络」三件事排查：`echo %VCP_DB_PASSWORD%`（环境变量优先级最高，残留的旧口令会盖掉 `application.yml` 的默认值）；在 `volunteer-cert-portrait-server/` 目录下启动（`application-local.yml` 的候选路径是相对工作目录的）；`Test-NetConnection 103.40.14.100 -Port 19476` 确认网络可达。
2. **注册时学院下拉是空的？** 学院字典只在 `10_base_and_test_accounts.sql` 与 `04_demo_data.sql` 里，跑一下 `10` 即可（它是学院字典的唯一来源，缺了注册会被拒「请选择学院」）。
3. **口令明明是 `123456` 却登录失败？** 库里存的是 BCrypt 密文：**老库**（先建库、后升级到加密代码的库）必须补跑 `08_password_bcrypt.sql`；再确认账号未被停用、连的是正确的库。
4. **活动图片 404？** 图片现在存在数据库，读取走 `GET /api/v1/attachments/{id}/content`；`/uploads/...` 是 2026-09-27 之前的旧地址，不再生成（`sql/12` 已把 `file_url` 回写成内容接口地址）。
5. **`/doc.html` 打不开？** 启动带了 `--spring.profiles.active=prod` 时，文档页与 `/v3/api-docs` 会收口（要求登录态）；开发口径不带 profile 即可访问。
6. **前端页面打开了但全是空数据？** 生产构建必须有 Nginx 把 `/api` 反代到 8080；开发模式检查 `VITE_USE_MOCK`（应为 `false`）与 `VITE_API_TARGET`（默认 `http://127.0.0.1:8080`）。判断产物用的是 mock 还是真库，看数据看板的数字最快：真库 **2,349 报名 / 9,163.0 小时**，mock 是 12,480 / 86,420。
7. **端口被占用？** 三个端口：后端 8080、前端 dev 5173、Nginx 80；`netstat -ano | findstr :8080` 找到进程停掉旧实例，或改 `application.yml` / `vite.config.js` / nginx 配置。
8. **时区与时间口径？** 全链路统一 `TIMESTAMP` + `LocalDateTime`，Jackson 按 `GMT+8` 输出，不做跨时区转换；签到窗口按活动时间判定（开始前 30 分钟开放签到，结束后 30 分钟内签退）。
9. **签到 / 签退按钮点不了？** 只有「活动开始前 30 分钟 ~ 结束」能签到、结束后 30 分钟内能签退，其他时间接口会拒绝；漏签由组织管理员在 `/org/attendance` 人工修正。
10. **`mvn package` 报 `Unable to rename ... .jar.original`？** 后端还在运行，进程锁着 jar；先停掉再打包。
11. **重跑 `sql/02_schema.sql` 会丢数据吗？** 会 —— `02` 开头是 `DROP TABLE IF EXISTS`，一跑就是清库重建；要的是「清空后重灌演示数据」，就按「五、数据库脚本」的完整顺序从 `02` 走一遍。
12. **上传活动图片失败？** 单张 ≤ 5MB、仅 JPG / PNG（按文件头识别，改扩展名无效）、像素上限 2500 万；Nginx 侧 `client_max_body_size` 已给到 `6m`。

## 九、目录结构

```text
volunteer-cert-portrait/
├── volunteer-cert-portrait-web/     # 前端（Vue 3 + Vite）：/student、/org、/admin 三端 + /login、/register
├── volunteer-cert-portrait-server/  # 后端（Spring Boot 多模块，11 个模块）
│   ├── vcp-common/                  # 统一返回、分页、枚举、异常、工具
│   ├── vcp-framework/               # Sa-Token、全局异常、MyBatis-Plus、操作日志切面
│   ├── vcp-system/                  # 用户/角色/字典/学院/学生档案/通知/操作日志/附件
│   ├── vcp-org/                     # 组织信息 + 资质审核
│   ├── vcp-volunteer/               # 活动/分类/报名/签到签退
│   ├── vcp-certification/           # 服务时长提交与审核
│   ├── vcp-portrait/                # 公益画像与等级
│   ├── vcp-analytics/               # 看板 10 个聚合接口
│   └── vcp-boot/                    # 启动模块，打包为唯一可执行 jar
├── sql/                             # 建库/建表/初始化/演示数据/补列/图片入库/一致性自检脚本
├── deploy/                          # nginx.conf（Linux）、vcp.service、nginx-win/（Windows Nginx 1.31.6）、verify-deploy.ps1
└── docs/                            # 部署、测试、待办、会话记录、论文与答辩材料
```

**文档索引**

| 文档 | 内容 |
|---|---|
| [docs/部署文档.md](docs/部署文档.md) | 完整部署步骤、验收清单、故障排查表 |
| [docs/测试报告.md](docs/测试报告.md) | 功能测试结果与缺陷修复记录 |
| [docs/待办清单.md](docs/待办清单.md) | 当前待办与已拍板事项 |
| [docs/会话记录.md](docs/会话记录.md) | 逐次开发的经过、验证方式与遗留问题 |
| [docs/论文/](docs/论文/) | 论文初稿（摘要、绪论、设计与实现、测试、结论、附录等） |
| [docs/答辩材料/](docs/答辩材料/) | 演示脚本、演示视频分镜、技术亮点、数据库说明、成员分工 |
| [sql/README.md](sql/README.md) | 每个 SQL 脚本的细节与全局设计约定 |

## 十、开发与文档

- **前端**：`npm run lint`（oxlint + eslint）、`npm run build`、`npm run verify:mock`（校验 mock 数据集内部数字自洽，改 mock 后跑一遍）。
- **后端**：`cd volunteer-cert-portrait-server && mvn package -DskipTests`（11 个模块全过）。
- **数据库自检**：`sql/09_consistency_check.sql` —— 只读、可重复执行，期望输出「检查项总数 20、违规合计 0」。
- 分支约定：小改动直接推 `main`；多人并行的较大改动开 `feature/*` 再合回；提交前缀用 `feat` / `fix` / `docs` / `test` / `style` / `refactor`。
