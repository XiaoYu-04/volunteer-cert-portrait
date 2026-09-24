# volunteer-cert-portrait-web

高校志愿服务时长认证与公益画像数据分析系统 —— 前端工程（简称 **vcp-web**）。

Vue 3 + Vite + Pinia + Vue Router + Axios + ECharts，视觉风格为**新中式水墨风**，UI 组件全部手写，不依赖第三方组件库。

## 快速开始

```bash
npm install
npm run dev
```

打开 [http://localhost:5173](http://localhost:5173)。登录页提供三个演示账号，点击即可填入：

| 角色 | 用户名 | 密码 | 登录后落地页 |
|---|---|---|---|
| 学生 | `student` | `123456` | `/student` |
| 组织管理员 | `org_admin` | `123456` | `/org` |
| 学校管理员 | `admin` | `123456` | `/admin` |

> **Node 版本**：`package.json` 要求 `^22.18.0 || >=24.12.0`。
> ⚠️ **本机当前是 v22.14.0，低于该下限**，所以 `npm install` 会报 `EBADENGINE` 警告 ——
> 实测（2026-09-24）`build` / `lint` / `verify:mock` 在这个版本上都能跑通，
> 但那是「碰巧能跑」，不是受支持的组合。
> 根目录的 `.nvmrc` 声明 `24.20.0`（满足 `>=24.12.0`），装了 nvm 的话 `nvm use` 即可切过去。

## 命令

```bash
npm run dev        # 开发服务器
npm run build      # 生产构建，产物在 dist/
npm run preview    # 预览构建产物
npm run lint       # oxlint + eslint 检查并自动修复
npm run format     # prettier 格式化 src/
npm run verify:mock # 校验模拟数据的自洽关系（见下文「模拟数据」）
```

## 后端对接：改一个环境变量即可

后端（`volunteer-cert-portrait-server/`）已就绪。开发环境**当前已切到真实后端**
（`.env.development` 的 `VITE_USE_MOCK=false`，请求经 Vite 代理打到 8080）；
纯前端独立演示仍走本地模拟数据（`.env.production` 的 `VITE_USE_MOCK=true`）。
接口层是按真实后端契约书写的，**两种跑法都不需要改任何页面代码**：

```bash
# .env.development —— 联调（当前值）
VITE_USE_MOCK=false   # false = 走真实 axios，请求经 Vite 代理打到后端
VITE_API_BASE_URL=/api

# .env.production —— 纯前端演示（当前值）
VITE_USE_MOCK=true    # true = 走 src/mock 本地模拟，不依赖后端
VITE_API_BASE_URL=/api
```

置为 `false` 后，`utils/request.js` 会改用 axios 请求 `${VITE_API_BASE_URL}/v1/...`。
开发环境下由 `vite.config.js` 的 `server.proxy` 把 `/api` 代理到后端 ——
**默认 `http://127.0.0.1:8080`**（各人跑各自的本机后端）；要和队友联调时，
在 `.env.local` 里设 `VITE_API_TARGET` 覆盖，**不要把某个人的内网 IP 写进入库文件**。
注意改完 `.env*` 或 `vite.config.js` 必须**重启 dev server** 才生效。
`/uploads` 也使用同一个代理目标，用来读取后端上传的活动图片。

## 活动图片

发布活动页支持 1 张封面和最多 6 张图文说明，组件为
`src/components/common/InkImageUploader.vue`。开发环境上传到后端
`POST /api/v1/attachments/images`；只支持 JPG/PNG、单张不超过 5MB。
纯 mock 模式不提供真实上传能力，页面会明确显示不可用，避免误判为已保存。

**响应契约**（与架构文档一致）：

- 统一外壳 `{ code, message, data }`，成功 `code = 0`
- 分页统一 `{ total, list }`
- 认证失败错误码 `20001 / 20002 / 20003`，命中即清 token 并跳登录
- 错误码分段：1xxxx 通用 / 2xxxx 认证权限 / 3xxxx 活动报名 / 4xxxx 时长认证 / 5xxxx 画像统计

## 目录结构

```text
src/
├── api/            接口层，按后端模块一一对应
├── components/
│   ├── charts/     InkChart.vue（ECharts 容器）+ options.js（配置工厂）
│   ├── common/     手写水墨组件库
│   └── biz/        业务展示组件（活动卡、画像印章、排行、公告）
├── composables/    useTable / useToast / useConfirm / useMenu
├── directives/     v-perm 按钮级权限
├── layouts/        StudentLayout（顶部导航）/ ConsoleLayout（侧边栏）
├── mock/           模拟数据与按 method+path 分发的 mock 路由
├── router/         routes/ 按角色拆分 + guard.js 全局守卫
├── stores/         user（登录态与权限）/ dict（状态字典）
├── styles/         tokens.css → base.css → ink.css（顺序不可颠倒）
└── views/          login / student / org / admin / error
```

## 设计系统

配色与排版令牌集中在 `src/styles/tokens.css`，**不要在页面里硬编码颜色**：

```text
纸白底 #F8F7F4   墨 #1A1A1A   朱砂 #B03A2E   石青 #3E6B7A
标题走衬线体（--font-display）  数据走等宽体（--font-mono / .num）
零圆角（--r-*: 0）  区块间距 88px  细墨线分隔而非卡片阴影
```

图表配色由 `components/charts/options.js` 通过 `getComputedStyle` 从 `:root` 读取，
因此 CSS 是配色的唯一数据源，改令牌图表会跟着变。

> 例外：图表内的柱状元素保留了轻微圆角（`borderRadius: [6,6,0,0]`），
> 这是原型 `styles_12-chinese-ink.html` 的刻意选择，不要"统一"成零圆角。

## 状态枚举

后端存英文码，前端经 `stores/dict.js` 翻译成中文标签与色调，页面里不出现硬编码的中文状态串：

| 字典类型 | 取值 |
|---|---|
| `activity_status` | `DRAFT` / `PUBLISHED` / `CLOSED` / `CANCELED` |
| `signup_status` | `PENDING` / `APPROVED` / `REJECTED` / `CANCELED` / `COMPLETED` |
| `attendance_status` | `NOT_SIGNED` / `SIGNED_IN` / `SIGNED_OUT` / `ABNORMAL` / `ABSENT` |
| `duration_status` | `PENDING_SUBMIT` / `PENDING_AUDIT` / `APPROVED` / `REJECTED` |
| `org_status` | `PENDING` / `APPROVED` / `REJECTED` / `DISABLED` |
| `audit_action` | `SUBMIT` / `APPROVE` / `REJECT`（注意与状态值的 `APPROVED`/`REJECTED` 不同形） |
| `notification_type` | `SIGNUP` / `DURATION` / `SYSTEM` |

用 `<StatusTag type="signup_status" :value="row.status" />` 渲染。

> **码值的权威来源是后端**：`sql/02_schema.sql` 的列注释与 `sql/03_init_data.sql` 的
> `sys_dict` 种子数据。前端这份是同一套码的镜像，改动前请先确认后端。
> 本节码值已于 2026-09-24 与后端逐条核对，**全部对齐**（`attendance_status` 用 `NOT_SIGNED`、
> `notification_type` 三档为 `SIGNUP`/`DURATION`/`SYSTEM`、`audit_action` 含 `SUBMIT`）；
> 核对过程见 [docs/前端进展与待办.md](../docs/前端进展与待办.md) 第三节第 6 条。

## 模拟数据

`src/mock/data/dataset.js` 的聚合指标继承自设计原型。这些数字之间有隐性约束，
改一个数很容易让不同页面显示的数字互相打架，所以断言写进了 `scripts/verify-mock-data.mjs`：

```bash
npm run verify:mock
```

```text
学院时长合计 = 86,420 = 累计志愿时长
活动类型合计 =    386 = 活动总数
公益画像合计 = 12,480 = 报名总人数
签到率     = 2,290 / 2,480 = 92.3%
审核通过率 = 2,142 / 2,418 = 88.6%
```

**持久化范围**：mock 数据活在内存里，整页刷新会重置。只有**注册产生的新账号**会写进
`localStorage`（`vcp_mock_extra_users`）—— 否则「注册 → 刷新 → 被踢回登录页，且新账号
再也登不回来」。其余实体（活动、报名、时长…）保持内存态，刷新即回到干净的种子数据，
方便反复演示同一条流程。想彻底重置，清掉该 key 即可。

## 新增页面的步骤

1. 在 `src/views/<角色>/` 建组件
2. 在 `src/router/routes/<角色>.js` 的 `children` 里加一条，`meta.menu = true` 的会自动出现在导航/侧边栏
3. 在 `src/api/` 加接口函数，在 `src/mock/data/` 加对应的 mock 路由（`{ method, path, handler }`，支持 `:id` 占位）
4. 需要新图表时，在 `components/charts/echarts.js` 里**注册对应图表类型**，否则运行时静默不渲染
