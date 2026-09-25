# 代理 1 · D4 系统截图入库 —— Handoff

> 任务：D4 系统截图入库（答辩硬前置，此前仓库里一直是 0 张图片）。
> 状态：**完成**。日期：2026-09-25。基线：`28c2ef6`（未做任何 git 写操作）。

---

## ① 一句话结论

**D4 截图已入库**：从 50 张真后端全页走查截图里挑选 **28 张**（三角色 22 个关键页 + 6 张 620 响应式证据），
放在 `docs/screenshots/{student,org,admin}/`，配套索引 `docs/screenshots/README.md`；
优化后合计 **2,859,003 B = 2.73 MB**（原始 5.89 MB，压掉 53.7%），**5 MB 上限 PASS，余量 2.27 MB**；
仓库「0 张图片」的状态就此终结。

---

## ② 产物清单

| 路径 | 内容 | 张数 | 体积 |
|---|---|---|---|
| `docs/screenshots/student/` | 学生端 8 个关键页（1440）+ 2 张 620 | 10 | 1,756,100 B (1.67 MB) |
| `docs/screenshots/org/` | 组织端 6 个页面全量（1440）+ 2 张 620 | 8 | 473,720 B (0.45 MB) |
| `docs/screenshots/admin/` | 管理端 8 个关键页（1440）+ 2 张 620 | 10 | 629,183 B (0.60 MB) |
| `docs/screenshots/README.md` | 索引表 + 口径警示 + 处理策略 + 复现命令 | 1（md） | 18,780 B（handoff 另计） |
| **合计** | | **28 PNG + 1 MD** | **2,859,003 B = 2.73 MB** |

> 三目录体积为 `Get-ChildItem docs/screenshots/<role> -File -Filter *.png | Measure-Object Length -Sum`
> 实测输出：student 10 张 1,756,100 B / org 8 张 473,720 B / admin 10 张 629,183 B；
> 合计 28 张 2,859,003 B（1,756,100 + 473,720 + 629,183 = 2,859,003 ✓）。
> 其中 `student/student-activities-389-1440.png` 单张 1,049,685 B，占 36.7%（保留 RGB 的刻意取舍，见 ③）。

**入库页面清单**（22 个 1440 页）：

- 学生端 8：`/student/home`、`/student/activities`、`/student/activities/389`（**详情**）、
  `/student/signups`、`/student/durations`、`/student/portrait`（**图表**）、
  `/student/notifications/14282`（**详情**）、`/student/profile`（**表单**）
- 组织端 6（**全量，一个不缺**）：`/org/dashboard`（**图表**）、`/org/activities`、`/org/activities/new`（**表单**）、
  `/org/signups`、`/org/attendance`、`/org/durations`
- 管理端 8：`/admin/dashboard`（**图表，9 canvas**）、**`/admin/colleges`（本轮新增页）**、
  `/admin/users`、`/admin/orgs`、`/admin/durations`、`/admin/portraits`（**图表**）、
  `/admin/categories`、`/admin/logs`

**620 响应式证据 6 张**（三端各 2 张 = 各一张主看板 + 各一张特色页）：
`student-home-620`、`student-portrait-620`、`org-dashboard-620`、`org-activities-new-620`、
`admin-dashboard-620`、`admin-colleges-620`。

---

## ③ 证据

### 3.1 入库决策的理由（拍板依据）

原待办第 28 项待拍板的是「入哪几页、放哪个目录」。拍板结论与理由：

- **目录** `docs/screenshots/{student,org,admin}/` —— 按角色分目录，与三角色架构对齐，
  引用路径稳定、一眼能看出属于哪个角色。
- **文件名不重命名** —— 沿用走查产物原名 `<页面>-<路由参数>-<视口>.png`，
  好处是文件名可直接反查 `.tmp-verify/walk-walk-real-*.json` 里的那条走查记录，便于溯源。
- **每角色 6~8 页**，覆盖「首页/看板 + 列表 + 详情 + 表单 + 图表 + 学院管理」六类，
  且优先选体现**闭环**的页（发布 → 报名 → 审核 → 签到 → 时长 → 审核 → 画像）。
- **组织端 6 个页面全入**（总共就 6 个，没有取舍余地）。
- **舍页理由**：学生端舍「通知列表」（信息量低于已入的「通知详情」）；
  管理端舍「角色管理」（只读权限清单、信息密度低）与「通知公告管理」（与组织端通知页重复度高）。
- **只入 1440 为主 + 620 抽样**：620 图与对应 1440 图信息重复，全入只增体积不增信息。

### 3.2 图片处理策略（实测数据）

用捆绑 Python（Pillow 12.3.0）执行 `.tmp-verify/ingest-screenshots.py`。
**受控实测**（`.tmp-verify/bench-controlled.py`，同一组 28 张，宽 ≤ 1280 的图不放大）：

| 策略 | 28 张合计 | 相对原图 |
|---|---|---|
| A 原图 | 5.89 MB | — |
| B 仅 `optimize=True`（不缩放） | 5.77 MB | −2.0% |
| C 不缩放 + 量化 256 | 2.26 MB | −61.7% |
| **D BOX→1280 + 量化 256** | **2.09 MB** | **−64.6%** ← 采用 |
| E LANCZOS→1280 + 量化 256 | 3.10 MB | −47.5% |

**采用策略 = D + 质量兜底**：等比缩放到宽 1280（**BOX 区域平均**重采样）→ 自适应 256 色调色板
（MEDIANCUT）→ 量化后 PSNR < 42 dB 的页保留 RGB → 统一 `PNG optimize=True, compress_level=9`。

**合计（入库脚本实跑输出）**：

```
入库 28 张；优化前 6,176,395 B (5.89 MB) -> 优化后 2,859,003 B (2.73 MB)，压掉 53.7%
5 MB 上限：PASS（余量 2,383,877 B = 2.27 MB）
```

**逐文件优化前后字节**（完整表在 `docs/screenshots/README.md` 第二节，此处列 1440 页）：

| 文件 | 原始 → 入库 | 文件 | 原始 → 入库 |
|---|---|---|---|
| student-home-1440 | 491,118 → 179,335 | admin-dashboard-1440 | 300,722 → 110,728 |
| student-activities-1440 | 204,542 → 72,272 | admin-colleges-1440 | 80,641 → 29,556 |
| student-activities-389-1440 | 1,345,504 → **1,049,685**（RGB） | admin-users-1440 | 155,742 → 58,986 |
| student-signups-1440 | 196,104 → 74,498 | admin-orgs-1440 | 145,116 → 51,404 |
| student-durations-1440 | 149,015 → 54,310 | admin-durations-1440 | 177,886 → 67,365 |
| student-portrait-1440 | 118,955 → 45,075 | admin-portraits-1440 | 209,760 → 78,978 |
| student-notifications-14282-1440 | 80,825 → 30,610 | admin-categories-1440 | 115,133 → 43,654 |
| student-profile-1440 | 87,429 → 34,125 | admin-logs-1440 | 119,297 → 44,776 |
| org-dashboard-1440 | 189,705 → 69,190 | student-home-620 | 462,327 → 175,943 |
| org-activities-1440 | 184,354 → 64,368 | student-portrait-620 | 102,276 → 40,247 |
| org-activities-new-1440 | 98,963 → 39,000 | org-dashboard-620 | 186,267 → 69,944 |
| org-signups-1440 | 159,075 → 59,373 | org-activities-new-620 | 97,691 → 39,204 |
| org-attendance-1440 | 169,536 → 64,793 | admin-dashboard-620 | 308,227 → 115,386 |
| org-durations-1440 | 168,981 → 67,848 | admin-colleges-620 | 71,204 → 28,350 |

### 3.3 ⚠️ 更正主会话那组 benchmark（重要，影响结论方向）

主会话用 `.tmp-verify/bench-png-total.py` 实测后给出三条结论并**撤回了「缩到 1280」**。
我复核后确认：**那组数字有两个混淆因素，结论需要更正**。已用 `.tmp-verify/bench-controlled.py`
与 `.tmp-verify/check-620-upscale.py` 复现并定位：

| 主会话结论 | 复核结果 |
|---|---|
| 「缩 1280 + 量化 256 = **4.42 MB**（−18.4%），反而更差」 | ❌ **那是 LANCZOS 的结果**（`bench-png-total.py:38` 写死 `Image.LANCZOS`）。换成 **BOX** 后同一组图是 **2.09 MB（−64.6%），是所有策略里最小的**，比「不缩放 + 量化」的 2.26 MB 还小 0.17 MB |
| 「620 高图会爆炸：`student-home-620.png` 451K → 1167K（2.6 倍）」 | ⚠️ **成因是上采样**：`bench-png-total.py:36` 无条件 `w = 1280`，把 620 宽的图**放大**到 1280。已逐张复现该数字（1167 K / +158%）。**我的入库脚本对宽 ≤ 1280 的图不放大**，该文件入库是 **175,943 B（−61.9%）**，不存在爆炸 |
| 「`optimize=True` 单独用等于没优化（−2.0%）」 | ✅ **成立**，我的实测同为 −2.0%，已写进 README |
| 「PSNR 那列数字是错的，别引用」 | ✅ 同意，未引用。我另做了**同尺寸** PSNR（量化图 vs 原图）：UI 页 **46.5~51.0 dB**，并生成 1:1 局部裁剪对比图目视确认（`.tmp-verify/cmp-crops/`），文字清晰、无抖动噪点 |

**因此我保留了「等比缩放到宽 1280」**（这是任务原始要求），只是把滤波器从 LANCZOS 换成 BOX、
并禁止对 620 图做上采样。两个决定都有上表的字节证据。

**质量兜底**：只有 `student-activities-389-1440.png` 触发 PSNR < 42 dB 的兜底
（39.59 dB，含 3 张实拍照片，量化会在肤色上出色带），它以 **RGB** 入库，比量化版多占 0.64 MB。
这是**刻意的质量取舍** —— 它是「图文活动详情」的旗舰证据图，且 5 MB 预算有 2.27 MB 余量。
若主会话认为体积优先，把 `ingest-screenshots.py` 的 `PSNR_FLOOR` 调到 0 重跑，总量即为 **2.09 MB**。

### 3.4 关于 620 张数从 4 加到 6

按主会话建议执行（原任务写 3~4 张，主会话说「有余量多入几页」）。
加到 6 张的编排逻辑是「三端各 2 张 = 各一张本角色主看板 + 各一张本角色特色页」，
代价 +185,330 B（2.55 MB → 2.73 MB），仍 PASS。

### 3.5 `git status` 里的新增文件（29 个）

```
?? docs/screenshots/README.md
?? docs/screenshots/admin/admin-categories-1440.png
?? docs/screenshots/admin/admin-colleges-1440.png
?? docs/screenshots/admin/admin-colleges-620.png
?? docs/screenshots/admin/admin-dashboard-1440.png
?? docs/screenshots/admin/admin-dashboard-620.png
?? docs/screenshots/admin/admin-durations-1440.png
?? docs/screenshots/admin/admin-logs-1440.png
?? docs/screenshots/admin/admin-orgs-1440.png
?? docs/screenshots/admin/admin-portraits-1440.png
?? docs/screenshots/admin/admin-users-1440.png
?? docs/screenshots/org/org-activities-1440.png
?? docs/screenshots/org/org-activities-new-1440.png
?? docs/screenshots/org/org-activities-new-620.png
?? docs/screenshots/org/org-attendance-1440.png
?? docs/screenshots/org/org-dashboard-1440.png
?? docs/screenshots/org/org-dashboard-620.png
?? docs/screenshots/org/org-durations-1440.png
?? docs/screenshots/org/org-signups-1440.png
?? docs/screenshots/student/student-activities-1440.png
?? docs/screenshots/student/student-activities-389-1440.png
?? docs/screenshots/student/student-durations-1440.png
?? docs/screenshots/student/student-home-1440.png
?? docs/screenshots/student/student-home-620.png
?? docs/screenshots/student/student-notifications-14282-1440.png
?? docs/screenshots/student/student-portrait-1440.png
?? docs/screenshots/student/student-portrait-620.png
?? docs/screenshots/student/student-profile-1440.png
?? docs/screenshots/student/student-signups-1440.png
```

**已核实**：`git status --porcelain` 对 7 个共享总账文件（`docs/待办清单.md`、`docs/下一步待办.md`、
`docs/答辩前待办.md`、`docs/测试报告.md`、`docs/会话记录.md`、`CLAUDE.md`、`README.md`）**输出为空**，
即一个都没改。本代理未执行任何 git 写操作。

**未入库**：`docs/截图清单.md` 之类的第二份索引**不存在**（`glob docs/*.md` 核对过，13 个 md 无此文件），
故 `docs/screenshots/README.md` 即唯一出处，无需另建。

### 3.6 复现命令

```powershell
# 重新生成入库图（原始素材在 .tmp-shots/walk-real-*/ 就位时）
<python> .tmp-verify/ingest-screenshots.py --dry-run   # 只测体积
<python> .tmp-verify/ingest-screenshots.py             # 正式写入
# 复核证据
<python> .tmp-verify/bench-controlled.py               # A~E 五策略总量
<python> .tmp-verify/check-620-upscale.py              # 620 上采样代价
<python> .tmp-verify/cmp-png-strategy.py               # 同尺寸 PSNR
```

`<python>` = `C:\Users\lsy04\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe`（含 Pillow 12.3.0）。
脚本全部在 `.tmp-verify/`（被 `.gitignore` 的 `.tmp-*` 覆盖，不入库）。

---

## ④ 可直接粘贴的 Markdown 片段

### 4.1 → `docs/下一步待办.md` **第 28 项**（截图入库决策，标 ✅）

把 310~316 行整段替换为：

```markdown
28. [x] **D4 截图的入库决策**（交付物，**需拍板**）—— ✅ **2026-09-25 已拍板并执行完毕**
    - **决策**：入库目录 **`docs/screenshots/{student,org,admin}/`**（按角色分目录，与三角色架构对齐）；
      **入 22 个关键页**（学生 8 / 组织 6 / 管理 8）+ **6 张 620 视口响应式证据**，共 **28 张**；
      文件名沿用走查产物原名 `<页面>-<路由参数>-<视口>.png`，便于反查 `walk-walk-real-*.json`。
    - **选页口径**：覆盖「首页/看板 + 列表 + 详情 + 表单 + 图表 + 学院管理」六类，优先体现闭环。
      组织端 6 个页面**全入**；学生端舍「通知列表」（信息量低于已入的通知详情）；
      管理端舍「角色管理」（只读权限清单）与「通知公告管理」（与组织端重复）。
    - **体积**：入库 28 张共 **2,859,003 B = 2.73 MB**（原始 5.89 MB，压掉 53.7%），
      **5 MB 上限 PASS、余量 2.27 MB**。索引见 `docs/screenshots/README.md`。
    - **口径**：全部是**真后端**那套（389 活动 / 10,719 报名 / 19,319.3 小时 / 签到率 90.1%），
      **不是** mock 那套（12,480 / 86,420）—— 答辩材料引用时必须标明。
    - 复现：`<python> .tmp-verify/ingest-screenshots.py`；重拍见 `docs/screenshots/README.md` 第四节。
```

### 4.2 → `docs/下一步待办.md` **第 19 项**（D4 系统截图）

把 572~577 行整段替换为：

```markdown
19. [x] **D4 系统截图** —— ✅ **2026-09-25 完成，仓库不再是 0 张图片**。
    - **产物**：`docs/screenshots/{student,org,admin}/` 共 **28 张**（22 个关键页 × 1440 +
      6 张 620 响应式证据），**2.73 MB**，索引 `docs/screenshots/README.md`。
    - **来源**：2026-09-25「真后端逐页点检」，25 个角色页 × 2 视口 = 50 条记录、**0 信号**
      （0 控制台报错 / 0 未捕获异常 / 0 失败请求 / 0 个 ≥400 响应 / 0 横向溢出 / 0 零尺寸 canvas），
      报告 `.tmp-verify/walk-walk-real-{student,org,admin}.json`，原始素材 50 张 / 8.93 MB（未入库）。
    - **口径**：截的是**真后端**那套（389 活动 / 10,719 报名 / 19,319.3 小时 / 签到率 90.1%），
      走查时全站 **0 处**命中 mock 数字（12,480 / 86,420）。
      **⚠️ 答辩材料里的数字必须标明是哪一套** —— mock 口径仍是 12,480 报名 / 86,420 小时。
    - 说明：本轮走的是**前端 dev server（`localhost:5173`，`.env.development` 为
      `VITE_USE_MOCK=false`）+ 真后端**这条路，**没有**动 `dist/`，
      所以「`.env.production` 仍是 `VITE_USE_MOCK=true`、现存 `dist/` 是 mock 口径」这两条
      **依然成立**，D5 构建时仍要记得改。重拍命令见 `docs/screenshots/README.md` 第四节。
```

### 4.3 → `docs/待办清单.md` **D4 段**（原 844 行那条）

```markdown
- [x] **D4 系统截图** —— ✅ **2026-09-25 已入库**：`docs/screenshots/{student,org,admin}/`
      共 **28 张**（22 个关键页 × 1440 + 6 张 620 响应式证据），**2.73 MB**
      （原始 5.89 MB，压掉 53.7%；5 MB 上限 PASS、余量 2.27 MB），索引见 `docs/screenshots/README.md`。
      **口径 = 真后端**：389 活动 / 10,719 报名 / 19,319.3 小时 / 签到率 90.1%
      （与 mock 的 12,480 报名 / 86,420 小时**不是同一套**，答辩材料引用时必须标明）。
      入库决策（原第 28 项待拍板）已拍板：按角色分目录、文件名沿用走查原名便于溯源、
      每角色 6~8 页覆盖「看板/列表/详情/表单/图表/学院管理」，组织端 6 页全入。
      来源为 2026-09-25「25 页 × 2 视口、0 信号」真后端走查（报告在 `.tmp-verify/`）。
      ⚠️ 本轮走前端 dev server，**未动 `dist/`** —— 「`.env.production` 仍为 `VITE_USE_MOCK=true`、
      现存 `dist/` 是 mock 口径」依然成立，D5 构建前仍要改。
```

### 4.4 → `docs/答辩前待办.md` **D4 段**（原 64 行那条）

```markdown
- [x] **D4 系统截图** —— ✅ **2026-09-25 完成**。验收对照：
      「三个角色各一套关键页面截图」→ `docs/screenshots/{student,org,admin}/` 学生 10 张 /
      组织 8 张 / 管理 10 张，共 **28 张**；「命名规整」→ 沿用 `<页面>-<路由参数>-<视口>.png`，
      并配 `docs/screenshots/README.md` 索引表（文件名/页面/路由/视口/说明/口径）；
      「随仓库提交」→ 已落在 `docs/screenshots/`，等主会话统一提交。
      **体积**：**2.73 MB**（原始 5.89 MB，压掉 53.7%），5 MB 上限 PASS。
      **口径 = 真后端**：389 活动 / 10,719 报名 / 19,319.3 小时 / 签到率 90.1%；
      与 mock 口径（12,480 报名 / 86,420 小时）**不是同一套**，**答辩材料里的数字必须标明是哪一套**。
      ✅ 已覆盖此前两处遗留：「620 视口未走查」「C3 那 56 张未整套重跑」——
      本轮 25 页 × 2 视口、0 信号，其中 620 视口入 6 张作响应式证据。
      ⚠️ 本轮走的是**前端 dev server（`localhost:5173`，`VITE_USE_MOCK=false`）+ 真后端**，
      **未动 `dist/`**；所以「`.env.production` 仍是 `VITE_USE_MOCK=true`」这条**依然成立**，
      D5 构建前仍要改（见上一节）。
```

---

## ⑤ 未做 / 受限项（如实记录）

1. **未重跑浏览器**（按任务要求）：截图素材直接取用 2026-09-25 已有的 50 张走查产物，
   本轮没有启动 Chrome / playwright，也没有重启后端或前端。因此本轮的「真后端」结论
   **继承自那轮走查记录**（`walk-walk-real-*.json` 的 `0 信号`），不是本轮新测的。
2. **620 视口只入 6 张，其余 19 张未入库**（共 25 张 620 素材）。原因：与对应 1440 图信息重复。
   未入库素材仍在 `.tmp-shots/`（gitignored），随时可补。
3. **1440 有 3 页未入库**：学生端「通知公告列表」、管理端「角色管理」「通知公告管理」，理由见 3.1。
4. **`student-activities-389-1440.png` 保留了 RGB、未量化**，比量化版多占 0.64 MB（质量取舍，见 3.3 末段）。
   若要求极限压体积，调 `PSNR_FLOOR` 重跑即可降到 2.09 MB。
5. **图片质量只有 PSNR 数值 + 3 张 1:1 局部裁剪的目视比对**，没有逐张 28 张全部目视检查过。
   已目视确认的：`admin-colleges-1440`（入库后 1280×806，文字清晰、1,503 学生等数字正确）、
   `admin-colleges-620`（响应式布局正确）、以及 `cmp-crops/` 里的照片/表格/图表三处 1:1 对比。
6. **未提交**：按铁律不做任何 git 写操作，29 个新文件仍是 untracked，等主会话统一提交。
7. **未修改任何共享总账文件**（7 个），要写进去的内容全在本文件第 ④ 节。
8. **`.tmp-verify/` 下的脚本不入库**（被 `.gitignore:36` 的 `.tmp-*` 覆盖）。
   若希望入库脚本本身也进仓库以便他人复现，需要主会话另行决定放置位置 ——
   目前脚本内容与用法已完整写在 `docs/screenshots/README.md` 第四节。
