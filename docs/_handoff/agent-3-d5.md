# 代理 3 · D5 部署收尾 —— Handoff

> 范围：前端构建产物、`deploy/**`、`docs/部署文档.md`（本代理独占）。
> 时间：2026-09-25 17:00–17:30。基线提交 `28c2ef6`，**全程无任何 git 写操作**。

## ① 一句话结论

**没有干净机器、本机也没有 nginx，所以 D5 的正式验收（真机照文档走一遍）仍未完成；但「能在本机验的部分」这一轮全部实测掉了 —— 真后端口径构建（mock 彻底消失）、静态托管 + history 回退、`/api` 反代、`/uploads` alias 四项都有实跑证据，并新增了可复跑的 `deploy/verify-deploy.ps1`（本机实跑 PASS 9 / FAIL 0 / SKIP 4、退出码 0，反向自检能正确报 4 个 FAIL）；同时修掉 3 个真问题（`vcp.service` 的 `JAVA_OPTS` 死配置、验收清单里「用域名访问 `/doc.html` 验 prod」这条根本测不出来、`dist/` 留的是旧 mock 产物）。`nginx -t`、真机 systemd、干净机器全流程**如实标注为未实测**。**

## ② 产物清单

| 产物 | 状态 | 说明 |
|---|---|---|
| `volunteer-cert-portrait-web/dist/` | **已重建（不入库，`dist/` 被 gitignore）** | **口径 = 真后端（`VITE_USE_MOCK=false`）**，入口 `assets/index-C47X2cye.js`。**旧的 mock 产物（`mock-DAfFHmNx.js`）已被覆盖**，现在 `dist/` 里没有任何 mock chunk |
| `deploy/verify-deploy.ps1` | **新增（入库）** | 部署验收脚本，`#Requires -Version 7.0`。参数：`-SiteUrl` / `-BackendUrl` / `-DistPath` / `-UploadsSamplePath` / `-ProdProfile` / `-ExpectedSignups` / `-ExpectedHours` / `-PublicBackendUrl` |
| `deploy/nginx.conf` | 改（`M`） | 回填实测 chunk 体积（`:31-40`）、补 `try_files` 内部重定向语义（`:50-55`）、补「`/doc.html` 不经 Nginx」及注释掉的做法（`:100-117`）、收口一节与 `vcp.service` 对齐（`:124-131`）、头部状态声明（`:11-15`） |
| `deploy/vcp.service` | 改（`M`） | **修 `JAVA_OPTS` 死配置**：`ExecStart` 引用 `$JAVA_OPTS`（`:62`）+ 依据与踩坑说明（`:47-57`）；`JAVA_TOOL_OPTIONS` 补注释（`:71-74`）；`Documentation=` 补前提说明（`:16-17`） |
| `docs/部署文档.md` | 改（`M`，+283/−76） | 逐节见下表 |
| `.env.production.local` | **已删除** | 临时覆盖文件，构建后即删；`.env.production` **未被改动**（仍是 `VITE_USE_MOCK=true`，已复核） |
| `.tmp-deploy-probe/nginx-mimic.mjs` | 临时、**不入库**（`.tmp-*` 被 gitignore） | nginx 替身（按 nginx.conf 语义实现 `try_files`/`alias`/`proxy_pass`/缓存头），**不是 nginx** |

`docs/部署文档.md` 改动点：

| 节 | 行 | 改了什么 |
|---|---|---|
| 头部状态声明 | `:6-12` | 明说「仍未真正部署过」，但本轮把本机可测的都测了 |
| 3.1 复用现有云库 | `:65-79` | **过期内容**：原写「小型图文演示数据 5 账号 / 1 组织 / 3 活动」→ 改为 07/12 两组口径表（1500/386/10719/19319.3 与 1503/389） |
| 3.2 自建库 | `:81-113` | **补 `10_base_and_test_accounts.sql`（新环境必需）**、标明 `04` 是 `07` 的硬前置、`12` 必须放最后、`08/09` 不在主链 |
| 4.1 打包 | `:117-126` | jar 体积 42 MB → 实测 40.8 MB |
| 4.2 上传 | `:128-147` | 补 `docs/` 拷贝（`Documentation=` 的指向）、写明 `vcp.upload.dir` ↔ `WorkingDirectory` 必须同时对、列出 3 张演示图 |
| 4.4 限制只监听本机 | `:185-206` | 与 `vcp.service` 对齐（**已默认带 `--server.address=127.0.0.1`**），补「想直连 8080 怎么改」 |
| 4.5 起服务 | `:207-244` | 新增**坑 8**：`Environment=` 不会自动进 `ExecStart` |
| 5.1 前端构建 | `:247-325` | 改为**推荐用 `.env.production.local` 覆盖**（不动入库文件）；补两套口径产物对照表 + 「入口 chunk 变小不是代码变少」的说明 + 本次 `dist/index.html` 的 preload 列表 |
| 六、Nginx | `:339-364` | 三条「不写会出事」的表加「实测情况」列；新增 `/doc.html` 不经 Nginx 的说明 |
| 七、验收清单 | `:365-395` | 加脚本用法；**修掉「用域名访问 `/doc.html` 验 prod」这条**（改为直连后端）；新增「产物不含 mock」「`/uploads` 图片 200」两条 |
| 八、故障排查 | `:396-418` | 新增 6 行：`/api` 返回 200+HTML、20001 口径、`/doc.html` 打开前端页属正常、`JAVA_OPTS` 不生效、uploads 404、8080 连不上 |
| 九、核实到什么程度 | `:419-492` | **整节重写**：实测 16 条 / 未实测 8 条 / 替身 vs 真 nginx 差异 9 条 |

## ③ 证据

### 3.1 构建：mock chunk 消失的前后对比（同机同 vite 8.3.0）

**前（旧 `dist/`，mock 口径）**：

```
mock-DAfFHmNx.js       45.7 kB      ← mock chunk 存在
index-BqPuHyTe.js      61.2 kB
request-KGj1kf8g.js   111.7 kB
echarts-Chzkqar6.js   637.5 kB
grep vcp_mock_extra_users → HIT: dist\assets\mock-DAfFHmNx.js
dist/index.html: 3 条引用（入口 + request + useApi）
```

**后（本次构建，真后端口径）**——`npm run build`（vite 8.3.0，794 modules，0.42 s）：

```
dist/assets/index-C47X2cye.js    22.93 kB │ gzip:   7.87 kB   ← 入口
dist/assets/request-PRHHT1LR.js 113.14 kB │ gzip:  42.91 kB
dist/assets/echarts-D-IMLEKC.js 652.78 kB │ gzip: 219.43 kB   ← 懒加载，不在 preload
dist/assets/index-DrbLQXPL.css   28.68 kB │ gzip:   6.17 kB
dist/assets/options-dzK06Q-9.js   9.92 kB │ gzip:   3.54 kB
ls dist/assets/mock-*.js              → 不存在 ✅
grep -rl vcp_mock_extra_users dist/   → 无输出 ✅
grep 其它 mock 标记（chenshiyuan@example.edu / vcp_mock / USE_MOCK）→ 命中文件数均为 0 ✅
```

**对照组（同一台机器，只把开关改回 `true` 再构建）**：`mock-DAfFHmNx.js` 46.77 kB 重新出现、`index-BqPuHyTe.js` 62.62 kB、grep 命中 `assets/mock-*.js`。
→ 说明「入口 chunk 从 62.62 掉到 22.93 kB」**不是代码变少**，而是关掉 mock 后 rolldown 把共享组件拆成了 9 个 `modulepreload` chunk（合计 42.73 kB）。
**首屏 JS 合计**：mock 关 65.66 kB（gzip 26.31）/ mock 开 63.46 kB —— 两者基本持平，与 `CLAUDE.md` 前端踩坑第 9/10 条的口径一致（`request` 113.14 ≈ 文档记的 113.0，`echarts` 652.78 ≈ 653）。

`dist/index.html`（真后端口径）的 modulepreload 列表：`request` / `InkField` / `pinia` / `useToast` / `InkDialog` / `useApi` / `InkButton` / `auth` / `_plugin-vue_export-helper`，入口 `index-C47X2cye.js`，样式 `index-DrbLQXPL.css`（11 条 `/assets/...` 根绝对路径引用）。

### 3.2 静态托管 + history 回退：替身实测（`nginx-mimic.mjs` @ 4173）

替身按 `deploy/nginx.conf` 的语义实现，访问日志（节选）：

```
09:24:17  GET  /                                    → 200  (1ms)    1320 B  text/html
09:24:17  GET  /student/profile                     → 200  (1ms)    ← 深层路由回退
09:24:17  GET  /student/activities/389              → 200  (0ms)
09:24:17  GET  /admin/colleges                      → 200  (0ms)
09:24:17  GET  /org/activities                      → 200  (0ms)
09:24:17  GET  /assets/index-C47X2cye.js            → 200  (1ms)    22939 B text/javascript
09:24:17  GET  /api/v1/categories                   → 200  (3ms)    {"code":20001,"message":"登录已过期，请重新登录"}
09:24:18  POST /api/v1/auth/login                   → 200  (467ms)  code=0 + token
09:24:19  GET  /api/v1/analytics/dashboard          → 200  (871ms)  活动 389 / 报名 10719 / 时长 19319.3
09:24:19  GET  /uploads/demo/activities/children-reading.png → 200 (5ms) 2155792 B image/png
```

逐条：

- **① 深层路由**：`/admin/colleges`、`/student/activities/389`、`/student/profile`、`/org/activities` 全部 **200**，且响应体与首页 `index.html` 一致（1320 B、含 `<div id="app">`、入口 script 同为 `/assets/index-C47X2cye.js`）。
- **② 静态资源与 `/uploads`**：11 个入口资源全部 200 且 MIME 正确；`/uploads/demo/activities/children-reading.png` → 200 + `image/png` + 2,155,792 B（替身按 `alias /opt/vcp/uploads/` 语义直出文件系统）。**后端自身的静态映射也直连实测过**：`http://127.0.0.1:8080/uploads/demo/activities/children-reading.png` → 200 + `image/png` + 2,155,792 B（`WebMvcConfig.java:98-106`）。
- **③ 与真 Nginx `try_files $uri $uri/ /index.html` 的差异**（重要）：替身能验的是**语义**（路径不存在 → 回退 index.html → 200），**不能验 nginx 本身**。差异清单已写进 `docs/部署文档.md` 第九节，要点：
  - 真 nginx 的 `try_files` 最后一个参数是 URI → **内部重定向并重新匹配 location**，所以回退出来的 index.html 会命中 `location = /index.html` 拿到 no-cache 头（替身按此实现并实测 `/` 与深层路由都带 `Cache-Control: no-cache, must-revalidate`）；
  - **缺文件的语义三种环境不同**：真 nginx `/assets/nope.js` → 404、`/uploads/nope.png` → 404；后端静态映射 → 200 + `{"code":10007,"message":"请求的接口不存在"}`（实测）；`vite preview` → `/assets/nope.js` 返回 **200 + index.html**（会把「白屏」故障掩盖成 200，**别用 preview 验这条**）。
  - `nginx -t` 的语法校验、模块行为、`alias` 归一化仍**完全未验**。
- **附：`vite preview` 也跑了**（4175，第二参照）：深层路由 200、`/api` 与 `/uploads` 也能通 —— 原因是 vite 的 `preview.proxy` 默认继承 `server.proxy`（`node_modules/vite/dist/node/chunks/node.js:34998`：`proxy: preview?.proxy ?? server.proxy`）。但它的缓存头与缺文件语义与 nginx 不一致（见上），且**只监听 `::1`**（用 `127.0.0.1:4175` 会连不上，得用 `localhost`）。

### 3.3 `deploy/verify-deploy.ps1` 实跑输出（本机，对着替身 4173 + 真后端 8080）

```
===== 部署验收（deploy/verify-deploy.ps1）=====
站点      : http://127.0.0.1:4173
后端直连  : http://127.0.0.1:8080
pwsh      : 7.6.6
--- 环境 ---
[SKIP] 环境 · nginx -t 语法校验          本机没有 nginx —— 这是「未实测」项，别当成通过
[SKIP] 环境 · systemd 单元 vcp 在跑       本机不是 systemd 环境（Windows 开发机）
--- 产物 ---
[PASS] 产物 · 不含 mock（真后端口径）    无 mock-*.js，grep vcp_mock_extra_users 无命中
--- 前端 ---
[PASS] 前端 · 首页可访问                 HTTP 200，1320 字节，含 <div id="app">
[PASS] 前端 · index.html 不缓存          / →「must-revalidate, no-cache」  深层路由 →「must-revalidate, no-cache」
[PASS] 前端 · 深层路由刷新不 404         4 条深层路由全部 200 且内容 = index.html（入口 /assets/index-C47X2cye.js）
[PASS] 前端 · 入口静态资源可取           11 个入口资源全部 200 且 MIME 正确
[INFO] 前端 · /assets/ 缓存策略          Cache-Control: public, immutable
--- 反代与后端 ---
[PASS] 反代 · /api 到达后端              HTTP 200 + body code=20001（未登录口径，说明请求确实到了后端）
[PASS] 后端 · 登录链路（连库）            admin 登录 code=0，token 已下发，角色 SCHOOL_ADMIN
[PASS] 后端 · 看板数字来自真库            活动 389 / 报名 10719 / 时长 19319.3 小时
--- 上传图片 ---
[PASS] 上传 · /uploads 图片可取          HTTP 200，image/png，2,155,792 字节
--- 上线收口 ---
[SKIP] 收口 · prod 下文档页被拒           /doc.html HTTP 200、/v3/api-docs HTTP 200 → 当前后端**未激活 prod**（未加 -ProdProfile，按开发口径 SKIP）
[SKIP] 收口 · 后端端口未对外暴露          没给 -PublicBackendUrl（要验这条得从外网打后端端口）
===== 汇总：PASS 9 / FAIL 0 / SKIP 4 / INFO 1 =====   [exit code: 0]
```

**反向自检**（证明脚本不是「永远绿」）：对着**只有后端、没有前端**的 8080 跑 →
`[FAIL] 前端 · 首页可访问`、`[FAIL] index.html 不缓存`、`[FAIL] 深层路由刷新不 404`、`[FAIL] 入口静态资源可取`，
汇总 **PASS 5 / FAIL 4 / SKIP 4**、**退出码 1** ✅

两个设计要点（写进脚本注释了）：
1. 后端未登录返回的是 **HTTP 200 + `{"code":20001}`**，不是 HTTP 401 —— 所以断言必须看响应体；
2. 反代没配好时 `/api/**` 会被 `try_files` 回退成 **200 + index.html**，看起来也是 200 —— 脚本用 `Content-Type` 单独判 FAIL。

### 3.4 配置一致性逐项核对（`文件:行`）

| 项 | deploy 侧 | 实际配置侧 | 结论 |
|---|---|---|---|
| 后端端口 8080 | `deploy/nginx.conf:92` `proxy_pass http://127.0.0.1:8080;` | `application.yml:2` `server.port: 8080` | ✅ 一致 |
| 无 context-path、`/api` 原样透传 | `deploy/nginx.conf:85-92`（结尾不带斜杠） | 全 `application.yml` 无 `context-path`；实测 `/api/v1/categories` 到后端返回 `code=20001` | ✅ 一致 |
| `/uploads` alias | `deploy/nginx.conf:75-76` `alias /opt/vcp/uploads/;` | `application.yml:128` `vcp.upload.dir: ./uploads` + `:130` `public-prefix: /uploads`；`WebMvcConfig.java:98-106` 静态映射；`vcp.service:29` `WorkingDirectory=/opt/vcp` | ✅ 一致（**三段拼起来才成立**，已在 4.2 写明） |
| 上传体积上限 | `deploy/nginx.conf:29` `client_max_body_size 6m;` | `application.yml:13-14` `max-file-size: 5MB` / `max-request-size: 6MB` | ✅ 一致（Nginx 6m 覆盖 multipart 开销） |
| CORS | —（同源反代不命中） | `application-prod.yml:21` = 占位值 `https://vcp.example.edu.cn` | ⚠️ 占位值，**部署前必改**（4.5 坑 6 已写） |
| knife4j prod 收口 | — | `application-prod.yml:24-25` `enable: false`（`SaTokenConfig.DOC_EXCLUDE_PATHS` 联动） | ⚠️ 配置一致，但**验收方式原先写错了**：`/doc.html` 不经 Nginx（替身实测返回前端 index.html），已改为直连后端验 |
| SQL 日志 prod | — | `application-prod.yml:27-29` `com.vcp: info` ↔ `application.yml:137` `debug` | ✅ 一致 |
| 前端 API 前缀 | `deploy/nginx.conf:87-88` 注释「与前端 .env 的 `VITE_API_BASE_URL=/api` 对得上」 | `volunteer-cert-portrait-web/.env.production:11` `VITE_API_BASE_URL=/api`；`src/utils/request.js:26` `baseURL: import.meta.env.VITE_API_BASE_URL \|\| '/api'` | ✅ 一致 |
| prod profile | `deploy/vcp.service:42` `SPRING_PROFILES_ACTIVE=prod` | `application-prod.yml` 三处收口 | ✅ 一致 |
| **`JAVA_OPTS`** | 原 `ExecStart` 未引用 → **死配置** | `vcp.service:69` 的 `-Xms256m -Xmx512m -Duser.timezone=Asia/Shanghai` 全不生效 | ❌ **已修**（`:62` 加 `$JAVA_OPTS`） |
| **`server.address`** | `deploy/vcp.service:62` 原无此参数，但 `docs/部署文档.md:190`（旧版）写「见 vcp.service 的 ExecStart」 | `application.yml:1-2` 无 `server.address` | ❌ **已修**：`ExecStart` 加 `--server.address=127.0.0.1`，文档 4.4 同步 |
| `Documentation=` 指向 | `deploy/vcp.service:17` `file:/opt/vcp/docs/部署文档.md` | 4.2 原先没拷 `docs/` | ⚠️ **已补**：4.2 加 `sudo mkdir -p /opt/vcp/docs && sudo cp -r docs/* /opt/vcp/docs/`（可选） |
| gzip 体积注释 | `deploy/nginx.conf:31-40` 原为 mock 口径数字（主 chunk 56.7 kB） | 实测真后端口径 | ⚠️ **已回填**（含「首屏 JS 合计」口径说明） |
| 脚本顺序 | `docs/部署文档.md:81-113` 原缺 `10` | `sql/README.md` + `CLAUDE.md` 踩坑 20：`10` 供学院字典、新环境必需 | ❌ **已补**（并标明 `04` 是 `07` 硬前置、`12` 放最后） |
| 库数据现状 | `docs/部署文档.md:65-79` 原写「5 账号 / 1 组织 / 3 活动」 | 实跑看板 389 活动 / 10719 报名 / 19319.3 小时 | ❌ **已更新** |

### 3.5 复现命令（**父代理可照这个复跑**）

```powershell
# 0) 前置：后端 8080 起着（开发口径即可）；本机无 nginx 属正常
cd C:\Users\lsy04\Documents\Project\volunteer-cert-portrait

# 1) 出「真后端口径」的 dist（不改入库的 .env.production）
Set-Content volunteer-cert-portrait-web\.env.production.local -Value "VITE_API_BASE_URL=/api`nVITE_USE_MOCK=false" -Encoding UTF8
cd volunteer-cert-portrait-web; npm run build
# 校验：无 mock chunk + grep 无输出
Get-ChildItem dist\assets -Filter "mock-*.js"            # 应为空
Get-ChildItem dist -Recurse -File | ForEach-Object { if (Select-String -Path $_.FullName -Pattern "vcp_mock_extra_users" -SimpleMatch -Quiet) { $_.Name } }   # 应无输出
cd ..

# 2) 起 nginx 替身（模拟 Nginx 托管 + try_files 回退 + /api 反代 + /uploads alias）
#    后台起，端口 4173
node .tmp-deploy-probe\nginx-mimic.mjs volunteer-cert-portrait-web\dist volunteer-cert-portrait-server\uploads 4173 http://127.0.0.1:8080

# 3) 跑验收脚本（另开一个终端）
pwsh -NoProfile -File deploy\verify-deploy.ps1 -SiteUrl http://127.0.0.1:4173
#    期望：PASS 9 / FAIL 0 / SKIP 4 / INFO 1，退出码 0
#    若 8080 没起：/api、登录、看板、uploads 会 FAIL —— 那是「后端没起」，不是部署缺陷，等它起来复跑

# 4) 收尾
Remove-Item volunteer-cert-portrait-web\.env.production.local -Force     # 必删
Get-NetTCPConnection -State Listen | Where-Object LocalPort -eq 4173 | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
# 可选第二参照：cd volunteer-cert-portrait-web; npx vite preview --port 4175 --strictPort   # 用 http://localhost:4175（只监听 ::1）
```

> ⚠️ 第 1 步结束后 `dist/` 就是真后端口径。**要回到 mock 演示口径**：删掉 `.env.production.local` 后重跑 `npm run build`（`.env.production` 里本来就是 `true`）。
> ⚠️ 若在真机上跑：`-SiteUrl http://<域名> -ProdProfile -ExpectedSignups 10719 -ExpectedHours 19319.3 -PublicBackendUrl http://<公网IP>:8080`，脚本会自动跑 `nginx -t` 与 `systemctl is-active vcp`。

## ④ 可直接粘贴的 Markdown 片段

### ④-1 插进 `docs/下一步待办.md` 的**第 20 项**（D5）

> 替换/追加 `docs/下一步待办.md:578` 那一项（`20. [ ] **D5 部署** —— 后端 jar + 前端 npm run build + Nginx 托管并反代 /api；部署文档也要写。`）的正文：

```markdown
20. [ ] **D5 部署** —— 后端 jar + 前端 `npm run build` + Nginx 托管并反代 `/api`；部署文档也要写。
    **2026-09-25 进展（代理 3）**：文档与配置这一层已经收口，**本机能实测的都实测掉了**，
    正式验收（干净机器照文档走一遍）**仍未做**。
    - ✅ **前端真后端口径构建**：用 `.env.production.local` 覆盖 `VITE_USE_MOCK=false`（不动入库的
      `.env.production`）后 `npm run build`；`dist/` 里 **mock chunk 彻底消失**
      （`mock-DAfFHmNx.js` 45.7 kB 已随旧产物被覆盖；`grep -rl vcp_mock_extra_users dist/` 无输出）。
      实测体积：入口 `index-C47X2cye.js` 22.93 kB / gzip 7.87 kB、`request` 113.14 kB / gzip 42.91 kB、
      `echarts` 652.78 kB / gzip 219.43 kB（懒加载）、`index.css` 28.68 kB；**首屏 JS 合计 65.66 kB**。
      ⚠️ `dist/` 现在留的就是这一套（真后端口径），旧的 mock 产物没了。
    - ✅ **静态托管 + history 回退 + `/api` 反代 + `/uploads` alias**：本机没有 nginx，用了一个按
      `deploy/nginx.conf` 语义实现的 Node 替身实测 —— 4 条深层路由（`/student/profile`、
      `/student/activities/389`、`/admin/colleges`、`/org/activities`）全部 200 且内容 = `index.html`；
      `/api/v1/categories` 到后端返回 `{"code":20001}`；`/uploads/demo/activities/children-reading.png`
      200 + `image/png`。**替身不是 nginx**，`nginx -t` 与真机行为仍未验（见 `docs/部署文档.md` 第九节差异清单）。
    - ✅ **新增 `deploy/verify-deploy.ps1`**（入库）：照验收清单写成一键脚本，PASS/FAIL/SKIP 分明、
      有 FAIL 则退出码 1。本机实跑 **PASS 9 / FAIL 0 / SKIP 4 / INFO 1，退出码 0**；
      反向自检（对着只有后端的 8080 跑）**FAIL 4、退出码 1**，证明它不是永远绿。
    - ✅ **修了 3 个真问题**：① `deploy/vcp.service` 的 `JAVA_OPTS` 是死配置（`ExecStart` 没引用
      `$JAVA_OPTS` → 堆上限与 `-Duser.timezone` 都没生效，而本项目时间字段是 `TIMESTAMP` 无时区，
      时区错了会影响看板与签到窗口），已修并补依据；② 验收清单里「用 `http://<域名>/doc.html`
      验 prod 收口」**根本测不出来**（`/doc.html` 不经 Nginx，会被 try_files 回退成前端页面，
      实测 200 + index.html），已改为**直连后端**验；③ 文档 3.1/3.2 的库数据现状与脚本顺序过期
      （缺新环境必需的 `sql/10`）。
    - ⏭️ **剩下的（必须在真机上做）**：干净机器照文档走一遍、`nginx -t`、systemd 启停与
      `$JAVA_OPTS` 实际展开、prod profile 实跑（`/doc.html` 应 401）、从外网确认 8080 连不上。
      复跑命令见 `docs/_handoff/agent-3-d5.md` 的 3.5 节。
```

### ④-2 插进 `docs/答辩前待办.md` 的 **D5 段**

> 替换 `docs/答辩前待办.md:53-63` 那一条（`- [ ] **D5 部署** —— ⬅️ **文档与配置已就绪（2026-09-24），未实际部署**…`）的正文：

```markdown
- [ ] **D5 部署** —— 文档与配置已就绪，**2026-09-25 又补了一轮「本机能实测的都实测掉」**，
      但**仍未在真机上部署过**（团队决定先出文档、机器到位再实操）：
      `docs/部署文档.md` + `deploy/nginx.conf` + `deploy/vcp.service` + **`deploy/verify-deploy.ps1`（新增，验收一键跑）**。
      **本轮已实测（本机，无 nginx）**：① 前端**真后端口径**构建 —— `dist/` 里 mock chunk 彻底消失
      （`grep -rl vcp_mock_extra_users dist/` 无输出），入口 22.93 kB / `request` 113.14 kB /
      `echarts` 652.78 kB，首屏 JS 合计 65.66 kB；② 用一个按 `nginx.conf` 语义实现的 Node 替身实测
      **history 回退**（4 条深层路由 200 且内容 = index.html）、**`/api` 反代**（到后端返回 `code=20001`）、
      **`/uploads` alias**（200 + `image/png`）、`index.html` 的 no-cache 头；
      ③ 验收脚本实跑 **PASS 9 / FAIL 0 / SKIP 4、退出码 0**（反向自检 FAIL 4、退出码 1）；
      ④ 修掉 3 个真问题（`vcp.service` 的 `JAVA_OPTS` 死配置、验收清单里测不出来的 `/doc.html` 那条、
      文档 3.1/3.2 过期内容）。
      **仍未实测（首次部署时优先怀疑）**：`nginx -t` 语法、真 nginx 运行时行为、systemd 单元行为、
      Nginx ↔ 后端真机联调、prod profile 实跑、公网 8080 是否真的连不上、**干净机器照文档走一遍**。
      逐条差异见 `docs/部署文档.md` 第九节（含「替身 vs 真 nginx」对照表）。
      ⚠️ **构建口径**：本轮 `dist/` 留的是**真后端**那一套（`VITE_USE_MOCK=false`）；
      入库的 `.env.production` **仍是 `true`**（纯前端独立演示口径，答辩兜底）—— 要出 mock 包，
      直接 `npm run build` 即可（真后端包则先建 `.env.production.local` 写 `VITE_USE_MOCK=false`）。
      见文档 5.1「坑 7」。
```

### ④-3 插进 `docs/测试报告.md` 的 **§6.2「D5 部署验收」**

> 替换 `docs/测试报告.md:227-228` 那一条（`2. **D5 部署验收**：部署文档与配置已写（docs/部署文档.md + deploy/），但「在干净机器上照文档走一遍」尚未执行，Nginx 托管与 history 回退未实测。`）：

```markdown
2. **D5 部署验收**（2026-09-25 更新）：部署文档与配置已写（`docs/部署文档.md` + `deploy/`），
   「**在干净机器上照文档走一遍**」**仍未执行** —— 没有干净机器，这条仍是 D5 的正式验收标准。
   **但「Nginx 托管与 history 回退未实测」这一句已不成立**：本轮在本机（Windows，无 nginx）用一个
   **按 `deploy/nginx.conf` 语义实现的 Node 替身**（`.tmp-deploy-probe/nginx-mimic.mjs`，未入库）
   把能验的都验了，并新增了入库的验收脚本 `deploy/verify-deploy.ps1`：

   | 验收项 | 结果 | 证据 |
   |---|---|---|
   | 前端产物为**真后端口径**（不含 mock） | ✅ 实测 | `VITE_USE_MOCK=false` 构建后 `ls dist/assets/mock-*.js` 不存在、`grep -rl vcp_mock_extra_users dist/` 无输出；对照构建（开关置 `true`）mock chunk 46.77 kB 且 grep 命中 |
   | chunk 体积符合预期 | ✅ 实测 | 入口 22.93 kB / gzip 7.87、`request` 113.14 kB / gzip 42.91、`echarts` 652.78 kB / gzip 219.43（懒加载）、首屏 JS 合计 65.66 kB |
   | **深层路由刷新不 404**（history 回退） | ✅ 实测（替身） | `/student/profile`、`/student/activities/389`、`/admin/colleges`、`/org/activities` → 全部 200 且内容 = `index.html`（入口 `/assets/index-C47X2cye.js`） |
   | `index.html` 不缓存 / `/assets/` 长缓存 | ✅ 实测（替身） | `/` 与深层路由均 `Cache-Control: no-cache, must-revalidate`；`/assets/index-C47X2cye.js` → `public, immutable` |
   | `/api` 反代原样透传 | ✅ 实测（替身 + 真后端） | `GET /api/v1/categories` → 200 + `{"code":20001,...}`；`POST /api/v1/auth/login` → `code=0` + token |
   | `/uploads/` 图片可取 | ✅ 实测（两路） | 替身按 `alias` 语义 → 200 + `image/png` + 2,155,792 B；后端静态映射直连 8080 同样 200 + `image/png` |
   | 看板数字来自真库 | ✅ 实测 | `/api/v1/analytics/dashboard` → 活动 389 / 报名 10719 / 时长 19319.3 小时（**不是** mock 的 12,480 / 86,420） |
   | 验收脚本可用 | ✅ 实测 | `deploy/verify-deploy.ps1` 实跑 **PASS 9 / FAIL 0 / SKIP 4 / INFO 1，退出码 0**；反向自检（只有后端的 8080）**FAIL 4、退出码 1** |
   | **`nginx -t` 语法校验** | ❌ **未实测** | 本机（及编写配置的机器）**没有装 nginx** |
   | **真 nginx 的运行时行为** | ❌ **未实测** | 上面所有「替身」结论都来自 Node 脚本，不是 nginx；`try_files` 内部重定向、`alias` 归一化、模块行为均未验 |
   | **systemd 单元行为**（含 `$JAVA_OPTS` 展开、`--server.address`） | ❌ **未实测** | 没有 Linux 环境 |
   | **prod profile 实跑**（`/doc.html` 应 401、CORS 收紧、SQL 日志降级） | ❌ **未实测** | 本机 8080 是开发口径，实测 `/doc.html`、`/v3/api-docs` 均 200（即收口未激活） |
   | **公网 8080 是否真的连不上** | ❌ **未实测** | 需要外网视角；脚本留了 `-PublicBackendUrl` 参数 |
   | **干净机器照文档走一遍** | ❌ **未实测** | 没有干净机器 |

   顺带修正一处**文档口径错误**：原验收清单里「`http://<域名>/doc.html` 要求登录（prod 生效的证据）」
   **测不出来** —— `/doc.html` 不在 `/api/` 下，会落到 `location /` 被 `try_files` 回退成前端 `index.html`
   （替身实测 200 + 前端页面，不是 Knife4j 页面）。已改为**在服务器上直连后端**验
   （`curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/doc.html` → 期望 401）。
   详见 `docs/部署文档.md` 第六节末与第七节。
```

## ⑤ 未做 / 受限项（如实标注，**都不是通过**）

| 项 | 状态 | 原因 |
|---|---|---|
| `nginx -t` 语法校验 | **未实测** | 本机没有 nginx（脚本里已写成 SKIP，部署机上会自动跑） |
| 真 nginx 的运行时行为 | **未实测** | 所有「静态托管/回退/反代/alias」结论都来自 **Node 替身**；替身只验「配置思路对不对」，不验「nginx 会不会照这个思路执行」 |
| systemd 单元行为（`vcp.service`） | **未实测** | 无 Linux 环境。包括 `$JAVA_OPTS` 的实际展开、`--server.address=127.0.0.1` 的实际效果、`EnvironmentFile` 权限、`Restart=on-failure` |
| Nginx ↔ 后端真机联调 | **未实测** | 替身那一跳走的是 Node `http.request`，不是 nginx `proxy_pass` |
| prod profile 实跑 | **未实测** | 8080 是开发口径；prod 下 `/doc.html` 是否真 401、CORS 是否真收紧、SQL 日志是否真降级，**只在配置层核对过** |
| 公网 8080 暴露 | **未实测** | 需外网视角；脚本的 `-PublicBackendUrl` 留给真机 |
| 干净机器照文档走一遍 | **未实测** | 没有干净机器 —— **这仍是 D5 的正式验收标准** |
| HTTPS / certbot | **未实测** | 文档里只是建议段落，未做任何验证 |
| 服务器上没有 pwsh 时的验收方式 | **部分受限** | `verify-deploy.ps1` 需要 pwsh 7（`#Requires -Version 7.0`，用了 `-SkipHttpErrorCheck`）。文档第七节写了「装一个，或照各条用 curl 手工过」。**没有提供 `.sh` 版本** —— 本机没有 bash/WSL，写一个跑不了的 shell 脚本违背本项目「验证过的文件才算数」的约定 |
| `deploy/**` 之外 | 未碰 | 未跑 `mvn`、未碰 8080/8081 的 jar、未改后端源码、未写数据库、**全程无 git 写操作**；7 个共享总账文件一个都没改（内容都在本 handoff 的 ④ 节） |

**另外两条给父代理的判断依据**：

1. **`deploy/vcp.service` 我改了行为，不只是改注释** —— `ExecStart` 现在带 `--server.address=127.0.0.1`，
   即**默认只监听本机**。理由：① 文档 4.4 本来就「推荐做」；② 旧文档写「见 vcp.service 的 ExecStart」
   暗示已经做了，实际没有（doc↔config 不一致）；③ Nginx 同源反代下不需要外部直连 8080。
   **代价**：想从别的机器直连 8080（例如用开发口径看 `/doc.html`）就得把那一段删掉。
   已在 `vcp.service:59-61` 与文档 4.4 写明怎么改回来。**若你不认可这个默认，改回来只需删一段参数。**
2. **`dist/` 现在留的是真后端口径**（`VITE_USE_MOCK=false`，入口 `index-C47X2cye.js`）。
   入库的 `.env.production` **没动**（仍是 `VITE_USE_MOCK=true`），已复核。要出 mock 演示包：
   直接 `npm run build`；要再出真后端包：先写 `.env.production.local`（命令见 3.5 节）。
   ⚠️ **D4 截图如果现在做，截到的是「真后端」那套数字** —— 与答辩材料里要标的口径要对齐。
