# 高校志愿服务时长认证与公益画像数据分析系统

> Volunteer Service Certification and Public Welfare Portrait Analysis System
> 项目代号 **VCP**（Volunteer Cert Portrait）

面向高校的志愿服务全流程管理系统：活动发布 → 报名审核 → 签到签退 → 服务时长记录 → 多角色时长审核 → 学生公益画像，六个环节构成完整闭环。

## 当前进度

| 部分 | 状态 |
|---|---|
| 前端 `volunteer-cert-portrait-web` | ✅ 29 个页面全部完成（第 29 页「学院管理」为 2026-09-24 新增）；接口层 70 个调用与后端全部对得上，**联调已完成（B12，2026-09-23 审计通过）**。dev server 代理可配置（`VITE_API_TARGET`，默认 `http://127.0.0.1:8080`） |
| 后端 `volunteer-cert-portrait-server` | ✅ 基础设施 + **业务模块 6/6 全部落地**（2026-09-23）：`vcp-system`、`vcp-org`、`vcp-volunteer`、`vcp-certification`、`vcp-portrait`、`vcp-analytics`；`mvn package` 11 个模块全过，三个角色实打 20 个接口全部符合预期、日志零异常 |
| 数据库脚本 `sql/` | ✅ 已完成：16 张表 + 初始化数据 + 演示数据 + 补列脚本 + 演示数据放大 + 口令密文化 + 学院字典 / 图文演示（`01`~`12`）。**2026-09-25 已按 `02 → 03 → 04 → 05 → 06 → 07 → 10 → 11 → 12` 整库重建并回灌全量演示数据**，随后 `09` 只读复验：检查项总数 20、违规合计 0 |
| 文档 `docs/` | ✅ 已完成：待办清单、下一步待办、会话记录、前后端进展、公益等级与标签规则方案、ER 图、开发计划与分工、知识库 |

前端已按真实后端契约写好接口层，`.env.development` 的 `VITE_USE_MOCK` 已设为 `false`，直接走真实后端，
页面代码一行都不用动（开发环境由 Vite 把 `/api` 代理到后端 8080，不重写路径）。
代理目标默认 `http://127.0.0.1:8080`；要和别人联调时在 `volunteer-cert-portrait-web/.env.local` 里设
`VITE_API_TARGET=http://<对方机器>:8080` 覆盖即可（`.env.local` 不入库）。
详见 [前端 README](volunteer-cert-portrait-web/README.md)。

**后端接口现状**：六个业务模块的接口均已可用并实测通过 ——
`/api/v1/auth/*`（登录、注册、退出、查改本人资料）、
`/api/v1/system/*`（用户、角色、字典、学院、学生档案、通知公告、操作日志）、
`/api/v1/orgs/*`（组织列表、详情、资料维护、资质审核、启停）、
`/api/v1/activities|categories|signups|attendance`（活动、分类、报名、签到签退）、
`/api/v1/durations|duration-audits`（时长提交与审核）、
`/api/v1/portraits/*`（公益画像、等级、标签）、
`/api/v1/analytics/*`（看板 10 个聚合接口）。
**接口规模**：OpenAPI 共 59 个路径 / 72 个「方法 + 路径」（2026-09-24 运行实例 `/v3/api-docs` 实测），
前端 `src/api/` 的 70 个调用全部能对上，0 缺失。
（2026-09-24 口令安全三件套新增 2 条：`PUT /api/v1/auth/password`、`PUT /api/v1/system/users/{id}/password`）

**活动图片**：发布页支持 1 张封面 + 最多 6 张带说明图片。
图片文件保存在后端 `uploads/`，PostgreSQL 的 `attachment` 只保存 URL、大小、说明和排序；
活动封面保存在 `volunteer_activity.cover`。上传接口为 `POST /api/v1/attachments/images`，
仅支持 JPG/PNG、单张不超过 5MB。
**联调前提**：`sql/06_backend_gap_fix2.sql` 与 `sql/07_demo_scale.sql` 必须在库上执行（均已执行）。
`07` 含 `activity_category.remark` 补列，执行后需**重启后端**，否则分类接口会查不存在的列报错。
**2026-09-25 现状**：云库已整库重建并回灌全量演示数据；后端已重新打包并重启（**pid 45644**，8080 监听）；
**B31「删除用户不级联」已修复并实测闭环**；**D4 系统截图已入库**（`docs/screenshots/`，28 张 / 2.73 MB，真后端口径）；
论文五章初稿与答辩材料包已产出（`docs/论文/`、`docs/答辩材料/`）。
两组口径见「[新同学四步上手](#新同学四步上手)」第 2 步与「[下一步待办](#下一步待办)」第 5 条。

## 下一步待办

1. **收尾**：~~接入密码加密（B15）~~ ✅ 2026-09-24 已完成 —— 口令以 BCrypt 密文入库
   （`PasswordUtils`），并配套做完「本人改密 / 管理员重置 / 登录失败锁定」三件套。
   **老库需补跑 `sql/08_password_bcrypt.sql`**（把明文口令刷成密文；新库不用跑）。
   **剩余**：表结构稳定后开启 Flyway（B14）。
2. **修剩余的联调遗留**：~~`signed_count` 两处口径不一致（B20-1）~~ ✅ 2026-09-24 已完成 ——
   口径定为**「报名即占名额」**（未取消未驳回都计入），走「改脚本对齐实现」这一支，
   `sql/07` 新活动名额下限同步 40→46；**只剩**操作日志「操作对象」列恒空
   （B20-6，已在前端止血，根治要给 `@OperationLog` 加 `target` 属性）。
3. **A 组已无剩余待定**：A6（时间类型是否改 `timestamptz`）已于 2026-09-24 拍板，结论「**不改** —— 继续用
   `TIMESTAMP` + `LocalDateTime`」。理由：单一部署、无跨时区需求；改成 `timestamptz` 要动 16 张表 + 全部实体 +
   前端渲染 + 演示数据重跑，收益与代价不匹配。
   A8（演示数据尺度）、A9（`sys_user.status` 码值）、A12（活动分类口径）均已拍板并落地，不再是待定项。
4. **交付物**：论文与答辩材料（D 组）。
5. **数据库现状（2026-09-25）**：云库已整库重建并回灌**全量演示数据**，不再是空库 ——
   `07` 口径 **1500 学生 / 386 活动 / 10719 报名 / 19319.3 小时**；
   再叠加 `12`（图文演示，不造报名）后是 **1503 学生 / 389 活动**（报名与时长不变）。
   **两组数字别混用**。`sql/09_consistency_check.sql` 复跑：**检查项总数 20、违规合计 0**；
   后端 `mvn -B package -DskipTests` 11 个模块 BUILD SUCCESS，已重启（**pid 32936**，8080 监听）。
   过程与证据见 [docs/会话记录.md](docs/会话记录.md) 会话 11。

> 另：云数据库口令已轮换，另外两位同学需把新口令更新到自己那份 `application-local.yml`
> （旧口令已失效）。连不上库时先查这里，不是代码问题。
>
> **口令从哪拿**：口令不在仓库里，只存在于执行轮换那台机器的 `application-local.yml`
> （该文件已 gitignore）；需要的人找轮换执行者线下索取（群聊 / 私聊），拿到后写进自己的
> `application-local.yml` 或设环境变量 `VCP_DB_PASSWORD`，**不要回写进任何被跟踪的文件**。
> 新同学的完整上手流程见下文「[新同学四步上手](#新同学四步上手)」一节。

完整清单见 [docs/待办清单.md](docs/待办清单.md)，后端细节见 [docs/后端进展与待办.md](docs/后端进展与待办.md)。
**下一次开工从哪开始**见 [docs/下一步待办.md](docs/下一步待办.md)（含「下次开工的起点」一节），
**上次会话做了什么、怎么验的、留下什么坑**见 [docs/会话记录.md](docs/会话记录.md)。

## 新同学四步上手

新加入的同学按这四步走，本地就能把整个系统跑起来。

1. **克隆仓库**：

   ```bash
   git clone https://github.com/XiaoYu-04/volunteer-cert-portrait.git
   cd volunteer-cert-portrait
   ```

2. **建库建表**：按顺序执行 `sql/01_create_database.sql` ~ `sql/12_activity_images_demo.sql`
   （`05`、`06`、`11` 是后端接口的前置依赖，**必执行**；`10` 是**学院字典的唯一来源**，也**必执行** ——
   缺了它注册页的学院下拉是空的、注册必被拒（「请选择学院」）；
   `04` **不是可选项**：`07` 的新活动按 `org_id = 1 + (g % 6)` 取组织，要求 `org_info` 里存在 id 1~6，
   而 id 2~8 全部由 `04` 创建，所以 **`04` 是 `07` 的硬前置**；
   `07`、`12` 是演示数据脚本（要全量演示口径就得跑）；`09` 是**只读**自检，可选但推荐）：

   ```text
   sql/01_create_database.sql → 02_schema.sql → 03_init_data.sql
   → 04_demo_data.sql（演示数据；07 的硬前置，必执行）
   → 05_backend_gap_fix.sql（必执行）→ 06_backend_gap_fix2.sql（必执行）
   → 07_demo_scale.sql（演示数据放大：1500 学生 / 386 活动 / 10719 报名 / 19319.3 小时）
   → 10_base_and_test_accounts.sql（基础数据 + 学院字典 5 条，必执行、可重复执行）
   → 11_activity_images.sql（活动图片字段，必执行）
   → 12_activity_images_demo.sql（少量账号、活动和 3 张图文演示，可选；叠加后 1503 学生 / 389 活动）
   → 09_consistency_check.sql（只读自检，20 项违规合计 0 即通过；可选但推荐，放最后跑）
   ```

   > **2026-09-25 实测顺序与结果**：`02 → 03 → 04 → 05 → 06 → 07 → 10 → 11 → 12`，
   > 最后跑 `09` 复验（**检查项总数 20、违规合计 0**）。`sql/10` 头部写明的「清库后要补跑 `05` → `06`」
   > 只对**清库**场景成立；按上面这条完整序列走，`05` / `06` 本来就在 `10` 之前，不用补跑。
   > `08_password_bcrypt.sql` 只服务于「先建库、后升级到加密版代码」的老库，新库不必跑（见「快速开始（后端）」）。

3. **配置数据库口令**：复制
   `volunteer-cert-portrait-server/vcp-boot/src/main/resources/application-local.yml.example`
   为同目录下的 `application-local.yml`，并填入数据库口令（口令不在仓库里，
   获取渠道见上文「下一步待办」一节末尾的说明）。这份文件已 gitignore、也**不会**被打进 jar；
   放在这个位置时，IDE 内运行与下一步「在 `volunteer-cert-portrait-server/` 下 `java -jar`」都能读到它。

4. **起前后端**：

   ```bash
   # 后端（默认端口 8080）
   cd volunteer-cert-portrait-server
   mvn package -DskipTests
   java -jar vcp-boot/target/vcp-boot-1.0.0.jar

   # 前端（另开一个终端窗口）
   cd volunteer-cert-portrait-web
   npm install
   npm run dev
   ```

## 快速开始（前端）

```bash
cd volunteer-cert-portrait-web
npm install
npm run dev
```

> 环境要求：Node `^22.18.0 || >=24.12.0`（见 `package.json` 的 `engines`）。

演示账号（登录页可点击填入）：

| 角色 | 用户名 | 密码 |
|---|---|---|
| 学生 | `student` | `123456` |
| 组织管理员 | `org_admin` | `123456` |
| 学校管理员 | `admin` | `123456` |
| 学生（图文演示） | `demo_stu_01` / `demo_stu_02` / `demo_stu_03` | `123456` |
| 组织管理员（图文演示） | `demo_org_01` | `123456` |
| 学校管理员（图文演示） | `demo_school_01` | `123456` |

> 表中的口令是**登录时输入的明文**；库里 `sys_user.password` 存的是它的 BCrypt 密文
> （同一明文每次哈希结果都不同，所以三个账号在库里的密文并不相同，这是正常的）。

## 快速开始（后端）

```bash
# 1) 建库建表：按顺序执行 sql/ 下的脚本
#    01_create_database.sql → 02_schema.sql → 03_init_data.sql
#    → 04_demo_data.sql（演示数据；07 的硬前置，必执行）
#    → 05_backend_gap_fix.sql（必执行）→ 06_backend_gap_fix2.sql（必执行）
#    → 07_demo_scale.sql（演示数据放大：1500 学生 / 386 活动 / 10719 报名 / 19319.3 小时）
#    → 10_base_and_test_accounts.sql（基础数据 + 学院字典 5 条，必执行、可重复执行）
#    → 08_password_bcrypt.sql（仅老库需要：把明文口令刷成 BCrypt 密文）
#    → 11_activity_images.sql（活动图片字段，必执行）
#    → 12_activity_images_demo.sql（少量账号、活动和 3 张图文演示，可选）
#    → 09_consistency_check.sql（只读自检，20 项违规合计 0 即通过；可选但推荐，放最后跑）
#    注意：05、06 是后端接口的前置依赖，缺了 05 登录接口会直接报错
#    07 是增量脚本（不重建库、可重复执行），含 activity_category.remark 补列，
#    执行后需重启后端；它把演示数据放大到 1500 学生 / 386 活动 / 10719 条报名
#    10 是学院字典的唯一来源（注册页下拉与「新增学生」都要它），缺了注册必被拒
#    04 不是可选项：07 的新活动 org_id = 1 + (g % 6) 需要 org_info 里存在 id 1~6，
#    而 id 2~8 全部由 04 创建 —— 04 是 07 的硬前置
#    12 叠加在 07 之后：学生 1500 → 1503、活动 386 → 389（报名与时长不变，12 不造报名）
#    08 只服务于「先建库、后升级到加密版代码」的库：03/04/07 的种子口令已是密文，
#    新库不必跑；反过来，老库不跑 08 就直接上加密版代码，所有账号都登不进去

# 2) 编译并启动（默认端口 8080）
cd volunteer-cert-portrait-server
mvn package -DskipTests
java -jar vcp-boot/target/vcp-boot-1.0.0.jar
#    提示：后端正在运行时先停掉再 mvn package —— 运行中的进程会锁住
#    target/vcp-boot-1.0.0.jar，repackage 会报 Unable to rename ... .jar.original
```

启动后接口文档在 <http://localhost:8080/doc.html>，可直接在页面上登录调试。

后端连的是云上 PostgreSQL 18.6，**不需要本地装数据库**。数据库凭证已**外置**：
口令走环境变量 `VCP_DB_USERNAME` / `VCP_DB_PASSWORD`，或
`vcp-boot/src/main/resources/application-local.yml`（已 gitignore，模板见同目录
`application-local.yml.example`），连接串与其余配置见 `application.yml`。

## 生产部署

完整步骤（含 Nginx 配置、systemd 单元、验收清单与故障排查表）见
[docs/部署文档.md](docs/部署文档.md) 与 [deploy/](deploy/)，这里只强调**一条**：

```bash
# 生产启动必须带 prod profile，否则三项收紧全部不生效（且不会有任何报错提示）
java -jar vcp-boot/target/vcp-boot-1.0.0.jar --spring.profiles.active=prod
```

不带 profile 时（开发口径）：`/doc.html` 免登录可访问、跨域允许所有来源、MyBatis 全量打印 SQL。
`application-prod.yml` 会把这三项收口；它不激活就完全不生效。

另有一条同样「不报错但会出错」的开关：前端 `volunteer-cert-portrait-web/.env.production` 的
`VITE_USE_MOCK`。它当前是 `true`（纯前端独立演示，不依赖后端）；**部署到真后端前必须改成 `false`**，
否则产物页面照常打开、只是根本不请求后端 —— 判据是看数据看板的数字（真实库 10,719 报名 /
19,319.3 小时 vs mock 12,480 / 86,420）。

## 仓库结构

```text
volunteer-cert-portrait/
├── volunteer-cert-portrait-web/     # 前端工程（Vue 3 + Vite）
├── volunteer-cert-portrait-server/  # 后端工程（Spring Boot 多模块，11 个模块）
│   ├── vcp-common/                  # 统一返回、分页、枚举、异常、工具
│   ├── vcp-framework/               # Sa-Token、全局异常、MyBatis-Plus、操作日志切面
│   ├── vcp-system/                  # ✅ 用户 / 角色 / 字典 / 学生档案 / 通知 / 操作日志
│   ├── vcp-org/                     # ✅ 组织信息 + 资质审核
│   ├── vcp-volunteer/               # ✅ 活动 / 报名 / 签到签退
│   ├── vcp-certification/           # ✅ 服务时长提交与审核
│   ├── vcp-portrait/                # ✅ 公益画像
│   ├── vcp-analytics/               # ✅ 看板统计
│   └── vcp-boot/                    # 启动模块，打包为唯一可执行 jar
├── sql/                             # 建表 + 初始化 + 演示数据 + 补列 + 演示数据放大脚本
├── deploy/                          # 部署产物：nginx.conf（站点配置）+ vcp.service（systemd 单元）
├── docs/                            # 待办清单、下一步待办、会话记录、进展记录、规则方案、开发计划
│   ├── 部署文档.md                   # 部署步骤、验收清单、故障排查（配套 deploy/ 下两个文件）
│   └── 知识库已确认项目选题与技术背景.md   # 选题与技术选型依据
```

> `bug/` 目录曾用来放前端同学的缺陷笔记，笔记里的问题都已修（提交 `cb5141b`），
> 两份笔记本身已随 `5ad6215` / `af7cc16` 删除，目录不再存在。

## 技术选型

**前端**：Vue 3.5 + Vite 8 + Pinia 4 + Vue Router 5 + Axios 1.20 + ECharts 6（Node `^22.18.0 || >=24.12.0`）
UI 组件全部手写，视觉风格为**新中式水墨风**（纸白底 + 墨黑 + 朱砂 + 石青，零圆角、细墨线分隔），不引入第三方组件库。

**后端**：Java 21 + Spring Boot 4.1.1 + MyBatis-Plus 3.5.17 + Sa-Token 1.46.0 + PostgreSQL 18.6（建表脚本按 16+ 编写）

> 后端选型有两个易踩的坑：MyBatis-Plus 必须用 `mybatis-plus-spring-boot4-starter`（不是 boot3 版）；
> Sa-Token 必须用 `sa-token-spring-boot4-starter`。Knife4j 官方版仅适配到 Boot 3，Boot 4 需用 Knife4j Next。

## 角色与权限

| 角色编码 | 名称 | 主要职责 |
|---|---|---|
| `STUDENT` | 学生 | 浏览活动、报名、查看本人时长与公益画像 |
| `ORG_ADMIN` | 组织管理员 | 发布活动、审核报名、管理签到、提交服务时长 |
| `SCHOOL_ADMIN` | 学校管理员 | 审核组织资质与时长、维护用户、字典与学院、查看全校数据看板 |

## 开发规范

- 分支：本项目至今只有 `main` 一条分支、无 PR 历史，所以小改动直接推 `main`；
  多人并行的较大改动开 `feature/*` 分支再合回 `main`；
  涉及 force push 的操作（例如日后清理 git 历史）动手前先在群里打招呼
- 提交前缀：`feat` / `fix` / `docs` / `test` / `style` / `refactor`
- 接口前缀统一 `/api/v1/`，响应外壳 `{ code, message, data }`，成功 `code = 0`
- 分页统一 `{ total, list }`
- 错误码分段：1xxxx 通用 / 2xxxx 认证权限 / 3xxxx 活动报名 / 4xxxx 时长认证 / 5xxxx 画像统计
- 状态机统一「英文码存库 + 字典翻译」，前端不出现硬编码的中文状态字符串
