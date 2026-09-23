# 高校志愿服务时长认证与公益画像数据分析系统

> Volunteer Service Certification and Public Welfare Portrait Analysis System
> 项目代号 **VCP**（Volunteer Cert Portrait）

面向高校的志愿服务全流程管理系统：活动发布 → 报名审核 → 签到签退 → 服务时长记录 → 多角色时长审核 → 学生公益画像，六个环节构成完整闭环。

## 当前进度

| 部分 | 状态 |
|---|---|
| 前端 `volunteer-cert-portrait-web` | ✅ 28 个页面全部完成，可独立运行（走本地模拟数据）；接口层 62 个调用与后端全部对得上，**正在联调（B12）** |
| 后端 `volunteer-cert-portrait-server` | ✅ 基础设施 + **业务模块 6/6 全部落地**（2026-09-23）：`vcp-system`、`vcp-org`、`vcp-volunteer`、`vcp-certification`、`vcp-portrait`、`vcp-analytics`；`mvn package` 11 个模块全过，三个角色实打 20 个接口全部符合预期、日志零异常 |
| 数据库脚本 `sql/` | ✅ 已完成：16 张表 + 初始化数据 + 演示数据 + 补列脚本（`01`~`06`） |
| 文档 `docs/` | ✅ 已完成：待办清单、前后端进展、公益等级与标签规则方案、开发计划与分工、知识库 |

前端已按真实后端契约写好接口层，**把 `.env` 里的 `VITE_USE_MOCK` 改成 `false`** 即可切到真实后端，
页面代码一行都不用动（开发环境由 Vite 把 `/api` 代理到后端 8080，不重写路径）。
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
**联调前提**：`sql/06_backend_gap_fix2.sql` 必须在库上执行（已执行）。

## 下一步待办

1. **与前端联调（B12）**：把前端 `.env` 的 `VITE_USE_MOCK` 改成 `false`，逐页对接口。
2. **修集成时发现的 4 个问题（B20）**：画像等级色调（B20-3）与标签分布文案「八类」（B20-4）
   前端侧已于 2026-09-23 改完；剩 `signed_count` 两处口径不一致（B20-1）、
   `activity_category` 缺 `remark` 列（B20-2）两项属后端侧，尚未修。
3. **收尾**：接入密码加密（B15，现在 `sys_user.password` 还是明文）、
   表结构稳定后开启 Flyway（B14）。
4. **A 组剩余待定**：A6（时间类型）、A8（演示数据尺度对齐前端）、
   A9（`sys_user.status` 用码还是标志位）。A12（活动分类口径）已于 2026-09-23 按后端对齐，不再是待定项。

完整清单见 [docs/待办清单.md](docs/待办清单.md)，后端细节见 [docs/后端进展与待办.md](docs/后端进展与待办.md)。

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
#    → 06_backend_gap_fix2.sql（必执行）
#    注意：05、06 是后端接口的前置依赖，缺了 05 登录接口会直接报错

# 2) 编译并启动（默认端口 8080）
cd volunteer-cert-portrait-server
mvn package -DskipTests
java -jar vcp-boot/target/vcp-boot-1.0.0.jar
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
├── sql/                             # 建表 + 初始化 + 演示数据 + 补列脚本
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

- 分支：`main` / `develop` / `feature/*` / `fix/*`
- 提交前缀：`feat` / `fix` / `docs` / `test` / `style` / `refactor`
- 接口前缀统一 `/api/v1/`，响应外壳 `{ code, message, data }`，成功 `code = 0`
- 分页统一 `{ total, list }`
- 错误码分段：1xxxx 通用 / 2xxxx 认证权限 / 3xxxx 活动报名 / 4xxxx 时长认证 / 5xxxx 画像统计
- 状态机统一「英文码存库 + 字典翻译」，前端不出现硬编码的中文状态字符串
