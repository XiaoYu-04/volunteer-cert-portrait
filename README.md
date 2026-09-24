# 高校志愿服务时长认证与公益画像数据分析系统

> Volunteer Service Certification and Public Welfare Portrait Analysis System
> 项目代号 **VCP**（Volunteer Cert Portrait）

面向高校的志愿服务全流程管理系统：活动发布 → 报名审核 → 签到签退 → 服务时长记录 → 多角色时长审核 → 学生公益画像，六个环节构成完整闭环。

## 当前进度

| 部分 | 状态 |
|---|---|
| 前端 `volunteer-cert-portrait-web` | ✅ 28 个页面全部完成；接口层 62 个调用与后端全部对得上，**联调已完成（B12，2026-09-23 审计通过）**。dev server 代理可配置（`VITE_API_TARGET`，默认 `http://127.0.0.1:8080`） |
| 后端 `volunteer-cert-portrait-server` | ✅ 基础设施 + **业务模块 6/6 全部落地**（2026-09-23）：`vcp-system`、`vcp-org`、`vcp-volunteer`、`vcp-certification`、`vcp-portrait`、`vcp-analytics`；`mvn package` 11 个模块全过，三个角色实打 20 个接口全部符合预期、日志零异常 |
| 数据库脚本 `sql/` | ✅ 已完成：16 张表 + 初始化数据 + 演示数据 + 补列脚本 + 演示数据放大（`01`~`07`） |
| 文档 `docs/` | ✅ 已完成：待办清单、前后端进展、公益等级与标签规则方案、ER 图、开发计划与分工、知识库 |

前端已按真实后端契约写好接口层，`.env.development` 的 `VITE_USE_MOCK` 已设为 `false`，直接走真实后端，
页面代码一行都不用动（开发环境由 Vite 把 `/api` 代理到后端 8080，不重写路径）。
代理目标默认 `http://127.0.0.1:8080`；要和别人联调时在 `volunteer-cert-portrait-web/.env.local` 里设
`VITE_API_TARGET=http://<对方机器>:8080` 覆盖即可（`.env.local` 不入库）。
详见 [前端 README](volunteer-cert-portrait-web/README.md)。

**后端接口现状**：六个业务模块的接口均已可用并实测通过 ——
`/api/v1/auth/*`（登录、注册、退出、查改本人资料）、
`/api/v1/system/*`（用户、角色、字典、学生档案、通知公告、操作日志）、
`/api/v1/orgs/*`（组织列表、详情、资料维护、资质审核、启停）、
`/api/v1/activities|categories|signups|attendance`（活动、分类、报名、签到签退）、
`/api/v1/durations|duration-audits`（时长提交与审核）、
`/api/v1/portraits/*`（公益画像、等级、标签）、
`/api/v1/analytics/*`（看板 10 个聚合接口）。
**接口规模**：OpenAPI 共 52 个路径 / 64 个「方法 + 路径」，前端 `src/api/` 的 62 个调用全部能对上，0 缺失。
**联调前提**：`sql/06_backend_gap_fix2.sql` 与 `sql/07_demo_scale.sql` 必须在库上执行（均已执行）。
`07` 含 `activity_category.remark` 补列，执行后需**重启后端**，否则分类接口会查不存在的列报错。

## 下一步待办

1. **收尾**：接入密码加密（B15，现在 `sys_user.password` 还是明文）、
   表结构稳定后开启 Flyway（B14）。
2. **修剩余的联调遗留**：`signed_count` 两处口径不一致（B20-1，需在「改实现」与
   「改 `04` 回填口径并重跑」之间二选一，改实现时 `sql/07` 的自检口径要同步改）；
   操作日志「操作对象」列恒空（B20-6，已在前端止血，根治要给 `@OperationLog` 加 `target` 属性）。
3. **A 组剩余待定**：A6（时间类型是否改 `timestamptz`）。
   A8（演示数据尺度）、A9（`sys_user.status` 码值）、A12（活动分类口径）均已拍板并落地，不再是待定项。
4. **交付物**：论文与答辩材料（D 组）。

> 另：云数据库口令已轮换，另外两位同学需把新口令更新到自己那份 `application-local.yml`
> （旧口令已失效）。连不上库时先查这里，不是代码问题。
>
> **口令从哪拿**：口令不在仓库里，只存在于执行轮换那台机器的 `application-local.yml`
> （该文件已 gitignore）；需要的人找轮换执行者线下索取（群聊 / 私聊），拿到后写进自己的
> `application-local.yml` 或设环境变量 `VCP_DB_PASSWORD`，**不要回写进任何被跟踪的文件**。
> 新同学的完整上手流程见下文「[新同学四步上手](#新同学四步上手)」一节。

完整清单见 [docs/待办清单.md](docs/待办清单.md)，后端细节见 [docs/后端进展与待办.md](docs/后端进展与待办.md)。

## 新同学四步上手

新加入的同学按这四步走，本地就能把整个系统跑起来。

1. **克隆仓库**：

   ```bash
   git clone https://github.com/XiaoYu-04/volunteer-cert-portrait.git
   cd volunteer-cert-portrait
   ```

2. **建库建表**：按顺序执行 `sql/01_create_database.sql` ~ `sql/07_demo_scale.sql`
   （`05`、`06` 是后端接口的前置依赖，**必执行**；`04`、`07` 是演示数据脚本，可选）：

   ```text
   sql/01_create_database.sql → 02_schema.sql → 03_init_data.sql
   → 04_demo_data.sql（演示数据，可选）→ 05_backend_gap_fix.sql（必执行）
   → 06_backend_gap_fix2.sql（必执行）→ 07_demo_scale.sql（演示数据放大，可选）
   ```

3. **配置数据库口令**：复制
   `volunteer-cert-portrait-server/vcp-boot/src/main/resources/application-local.yml.example`
   为同目录下的 `application-local.yml`，并填入数据库口令（口令不在仓库里，
   获取渠道见上文「下一步待办」一节末尾的说明）。

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

## 快速开始（后端）

```bash
# 1) 建库建表：按顺序执行 sql/ 下的脚本
#    01_create_database.sql → 02_schema.sql → 03_init_data.sql
#    → 04_demo_data.sql（演示数据，可选）→ 05_backend_gap_fix.sql（必执行）
#    → 06_backend_gap_fix2.sql（必执行）→ 07_demo_scale.sql（演示数据放大，可选）
#    注意：05、06 是后端接口的前置依赖，缺了 05 登录接口会直接报错
#    07 是增量脚本（不重建库、可重复执行），含 activity_category.remark 补列，
#    执行后需重启后端；它把演示数据放大到 1500 学生 / 386 活动 / 10719 条报名

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
├── docs/                            # 待办清单、进展记录、规则方案、开发计划
│   └── 知识库已确认项目选题与技术背景.md   # 选题与技术选型依据
└── bug/                             # 前端同学的缺陷笔记
```

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
| `SCHOOL_ADMIN` | 学校管理员 | 审核组织资质与时长、维护用户与字典、查看全校数据看板 |

## 开发规范

- 分支：本项目至今只有 `main` 一条分支、无 PR 历史，所以小改动直接推 `main`；
  多人并行的较大改动开 `feature/*` 分支再合回 `main`；
  涉及 force push 的操作（例如日后清理 git 历史）动手前先在群里打招呼
- 提交前缀：`feat` / `fix` / `docs` / `test` / `style` / `refactor`
- 接口前缀统一 `/api/v1/`，响应外壳 `{ code, message, data }`，成功 `code = 0`
- 分页统一 `{ total, list }`
- 错误码分段：1xxxx 通用 / 2xxxx 认证权限 / 3xxxx 活动报名 / 4xxxx 时长认证 / 5xxxx 画像统计
- 状态机统一「英文码存库 + 字典翻译」，前端不出现硬编码的中文状态字符串
