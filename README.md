# 高校志愿服务时长认证与公益画像数据分析系统

> Volunteer Service Certification and Public Welfare Portrait Analysis System
> 项目代号 **VCP**（Volunteer Cert Portrait）

面向高校的志愿服务全流程管理系统：活动发布 → 报名审核 → 签到签退 → 服务时长记录 → 多角色时长审核 → 学生公益画像，六个环节构成完整闭环。

## 当前进度

| 部分 | 状态 |
|---|---|
| 前端 `volunteer-cert-portrait-web` | ✅ 已完成，可独立运行（走本地模拟数据） |
| 后端 `volunteer-cert-portrait-server` | ⬜ 未开始（规划为 Spring Boot 4.1.1 多模块） |
| 数据库脚本 `sql/` | ⬜ 未开始 |
| 文档 `docs/` | ⬜ 未开始 |

前端已按真实后端契约写好接口层，后端就绪后**只需把 `.env` 里的 `VITE_USE_MOCK` 改成 `false`**，
页面代码一行都不用动。详见 [前端 README](volunteer-cert-portrait-web/README.md)。

## 快速开始（前端）

```bash
cd volunteer-cert-portrait-web
npm install
npm run dev
```

演示账号（登录页可点击填入）：

| 角色 | 用户名 | 密码 |
|---|---|---|
| 学生 | `student` | `123456` |
| 组织管理员 | `org` | `123456` |
| 学校管理员 | `admin` | `123456` |

## 仓库结构

```text
volunteer-cert-portrait/
├── volunteer-cert-portrait-web/     # 前端工程（Vue 3 + Vite）
├── volunteer-cert-portrait-server/  # 后端工程（Spring Boot 多模块，待建）
├── sql/                             # 建表脚本 + 初始化数据（待建）
├── docs/                            # 需求、数据库设计、接口文档、测试用例（待建）
└── 知识库已确认项目选题与技术背景.md   # 选题与技术选型依据
```

## 技术选型

**前端**：Vue 3 + Vite + Pinia + Vue Router + Axios + ECharts
UI 组件全部手写，视觉风格为**新中式水墨风**（纸白底 + 墨黑 + 朱砂 + 石青，零圆角、细墨线分隔），不引入第三方组件库。

**后端（规划）**：Java 21 + Spring Boot 4.1.1 + MyBatis-Plus 3.5.17 + Sa-Token 1.46 + PostgreSQL 16

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