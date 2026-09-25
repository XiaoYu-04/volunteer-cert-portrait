# 代理 2 handoff：B31「删除用户不级联」已修复 + prod profile 三项收紧实测

> 角色：5 个并行子代理中的**代理 2**，独占后端代码 / Maven 构建 / 8080·8081 的 jar 生命周期 / 数据库写操作。
> 完成时间：2026-09-25 17:20–17:35（+08:00）。基线提交 `28c2ef6`，**未做任何 git 写操作**。

---

## ① 一句话结论

**B31 已修复并实测闭环**：`DELETE /api/v1/system/users/{id}` 现在在同一事务里级联逻辑删除该学生的
`student_info` / `activity_signup` / `attendance_record` / `service_duration`，并按 `sql/09` 第 ⑦ 项的同一口径
重算 `signed_count`；修好后重跑 `verify.ps1`，看板**不再漂**（跑前跑后都是 1503 学生 / 389 活动 /
10719 报名 / 19319.3 小时），`sql/09` 20 项违规合计 **0**。**prod profile 三项收紧也三条全实测**：
文档路径确实不再免登录、CORS 白名单确实拦住任意 Origin、SQL 日志确实不再全量打印。

⚠️ **一处必须知道的偏差**：任务书建议的「未完成报名置 `CANCELED`」**不足以修好这个缺陷**（有硬证据，
见 ②-6），实际采用**逻辑删除 `activity_signup`（`deleted = 1`）并保留原 `status`**。

---

## ② 改动清单（文件:行 + 为什么这么改）

### 新增 5 个文件

| 文件 | 关键行 | 作用与理由 |
|---|---|---|
| `vcp-system/.../service/StudentSignupPurgePort.java`（新） | `:25` 接口、`:31` `purgeByStudentId` | vcp-system 处在依赖链底层（`vcp-volunteer → vcp-system`），**不能反向依赖 vcp-volunteer**，碰不到 `activity_signup` / `attendance_record`。按项目既有的依赖倒置套路（`OrgLookupPort` / `OrgCollegeCountPort`）定义端口，由 vcp-volunteer 实现。**必需注入**而非 `ObjectProvider.getIfAvailable()`：缺实现时安静跳过 = 静默的半个级联，正是 B31 的缺陷形态，宁可启动失败 |
| `vcp-system/.../service/StudentDurationPurgePort.java`（新） | `:24` `purgeByStudentId` | 同上，`service_duration` 归 vcp-certification。**拆两个端口而不是一个**：每个模块只清自己域的表，vcp-system 只负责编排 |
| `vcp-system/.../service/impl/StudentArchivePurger.java`（新） | `:79` `purgeByUserId`、`:88` 活动域、`:89` 时长域、`:92` `studentInfoMapper.deleteById` | 销档入口，与既有的 `StudentArchiveRegistrar`（建档）成对，让「删账号要动哪几张表」只有一个落点。三步同处调用方事务；顺序：活动域 → 时长域 → 软删档案（释放名额要读报名状态，必须在报名行被软删**之前**做） |
| `vcp-volunteer/.../service/impl/StudentSignupPurgePortImpl.java`（新） | `:64` `purgeByStudentId`、`:69` 作废签到、`:71` 逻辑删报名、`:74` 重算名额 | 三步顺序不可换，类注释写明了理由 |
| `vcp-certification/.../service/impl/StudentDurationPurgePortImpl.java`（新） | `:49` `purgeByStudentId`、`:53` 逻辑删时长 | 只软删 `service_duration`，**不动 `duration_audit`**（只追加的流水，记录「审核确实发生过」） |

### 修改 4 个文件

| 文件:行 | 改动 | 为什么 |
|---|---|---|
| `vcp-system/.../service/impl/UserServiceImpl.java:75`、`:301`、`:312`、`:323` | `deleteUser` 末尾加 `studentArchivePurger.purgeByUserId(existing.getId())`；注入新字段；补类注释 | B31 的落点。与 `userMapper.deleteById` / `userRoleMapper.delete` 同处 `@Transactional`，任何一步失败整体回滚 |
| `vcp-volunteer/.../mapper/VolunteerActivityMapper.java:52-76` | 新增 `refreshSignedCountByStudent(studentId)`：一条 UPDATE 把活动 `signed_count` **直接置成子查询结果** | **采用主会话建议的口径**：与 `sql/09` 第 ⑦ 项**同一段谓词**（`deleted = 0 AND status IN ('PENDING','APPROVED','COMPLETED')`），因此跑完第 ⑦ 项必然为 0；同一学生在一个活动下有多条报名时也不会多减漏减，且天然幂等。比逐行 `decreaseSignedCount` 稳 |
| `vcp-volunteer/.../mapper/AttendanceRecordMapper.java:79-94` | 新增 `invalidateByStudentId(studentId)`（`invalidateBySignupId` 的批量版） | 学生名下报名可能多条，逐条调用是 N 次往返。子查询不过滤 `activity_signup.deleted`，所以放在软删报名之前之后都对 |
| `vcp-portrait/.../mapper/PortraitAggregateMapper.java:181-199` | `selectTagCounts` 补 `JOIN student_info si ON si.id = sp.student_id AND si.deleted = 0` | **同族缺口**（主会话要求一并做掉）：`student_profile` 没有 `deleted` 列，是整表重算的派生快照；该查询此前不 JOIN 档案表，被删账号的快照仍计入标签分布。同 Mapper 的 `selectPortraitPage:84` / `selectPortrait:126` / `selectStudentStats:241` 一直都是这么过滤的，只有本方法漏了 |

### 6. 为什么**不**采用「报名置 CANCELED」（关键口径，有硬证据）

- 看板「报名总人数」的口径是 **`COUNT(*) FROM activity_signup WHERE deleted = 0`**
  —— 见 `vcp-analytics/.../mapper/AnalyticsMapper.java:84`（核心指标 `enrolled`）。
  **`CANCELED` 行照样会被数进去**：只改状态的话，删一个账号看板仍 +1，验收点（回到 10719）过不了。
- 实测反证：修复前那一轮（旧 jar）跑完 `verify.ps1`，报名行留在库里且 `deleted = 0`，看板
  `enrolled` 从 10719 变 10720；逻辑删除后才回到 10719。
- **保留原 `status` 不改写**：`deleted` 是业务表的逻辑删除位，所有读路径都过滤它；把 `APPROVED`/`COMPLETED`
  改写成 `CANCELED` 会抹掉「该生当时确实完成过这场活动」的审计事实，并把「账号被删」伪装成
  「学生主动取消」，两者在审计上不是一回事。
- 顺带说明：`sql/09` 文件头「取消报名是 status='CANCELED' 复用同一行，deleted 保持 0」说的是
  **学生主动取消**那条路径，本次不动它。

---

## ③ 证据

### 3.1 只读复现（旧 jar，`pid 32936`，开发口径）

**复现动作**：`.tmp-verify/verify.ps1` 实跑一轮（该脚本第 7 步会 `DELETE /api/v1/system/users/{id}`）。
原始输出留档 `.tmp-b31/repro-before-fix.txt`，**PASS 30 / SKIP 2 / FAIL 0**；脚本自报
`删除 e2e0925171615 (id=1515) -> code=0`（接口返回成功，库却没清干净）。

复现查询（`Q.java`，只读）：

```
SELECT si.id AS si_id, si.user_id, si.student_no, si.deleted AS si_deleted, si.total_duration,
       u.username, u.deleted AS u_deleted
  FROM student_info si JOIN sys_user u ON u.id = si.user_id
 WHERE si.deleted = 0 AND u.deleted = 1 ORDER BY si.id;
```
```
 si_id | user_id | student_no | si_deleted | total_duration | username       | u_deleted
  1504 |    1515 | S001515    |          0 |            2.5 | e2e0925171615  |         1
(1 rows)
```

```
SELECT sg.id, sg.activity_id, sg.student_id, sg.status, sg.deleted AS sg_deleted,
       sd.id AS duration_id, sd.duration, sd.status AS dur_status, sd.deleted AS sd_deleted,
       ar.id AS att_id, ar.status AS att_status, ar.deleted AS att_deleted
  FROM activity_signup sg
  LEFT JOIN service_duration sd ON sd.signup_id = sg.id
  LEFT JOIN attendance_record ar ON ar.signup_id = sg.id
 WHERE sg.student_id = 1504;
```
```
 signup_id | activity_id | student_id | status   | sg_deleted | duration_id | duration | dur_status | sd_deleted | att_id | att_status | att_deleted
     10720 |         380 |       1504 | APPROVED |          0 |        7424 |      2.5 | APPROVED   |          0 |   8236 | NOT_SIGNED |           0
(1 rows)
```

**看板随之漂移**（`GET /api/v1/analytics/dashboard`，留档 `.tmp-b31/dash-after-repro.txt`）：

| 指标 | 复现前 | 复现后 | 漂移 |
|---|---|---|---|
| `stats.enrolled` 报名 | 10719 | **10720** | +1 |
| `stats.hours` 累计时长 | 19319.3 | **19321.8** | +2.5 |
| `signin` 应签到/实签到 | 8235 / 7423 | **8236** / 7423 | +1 |
| `audit.total` / 已通过 | 7423 / 5614 | **7424** / 5615 | +1 |
| 库内 `student_info` | 1503 | **1504** | +1 |

（复现前基线：`.tmp-b31/dash-before-repro.txt`——文件名为 `dash.ps1` 首次运行输出，即修复前干净态。）

### 3.2 构建（11 个模块）

先 `mvn -B compile -DskipTests` 抓编译错（不碰 jar，8080 可继续跑），再停 8080 后打包：

```
[INFO] vcp-dependencies ................................... SUCCESS [  0.001 s]
[INFO] volunteer-cert-portrait-server ..................... SUCCESS [  0.000 s]
[INFO] vcp-common ......................................... SUCCESS [  0.700 s]
[INFO] vcp-framework ...................................... SUCCESS [  0.334 s]
[INFO] vcp-system ......................................... SUCCESS [  0.209 s]
[INFO] vcp-org ............................................ SUCCESS [  1.597 s]
[INFO] vcp-volunteer ...................................... SUCCESS [  1.472 s]
[INFO] vcp-certification .................................. SUCCESS [  0.903 s]
[INFO] vcp-portrait ....................................... SUCCESS [  0.839 s]
[INFO] vcp-analytics ...................................... SUCCESS [  0.749 s]
[INFO] vcp-boot ........................................... SUCCESS [  0.925 s]
[INFO] BUILD SUCCESS
[INFO] Total time:  8.006 s
```

留档：`.tmp-b31/mvn-package.log`。停 8080 的命令：`Get-NetTCPConnection -LocalPort 8080` → `Stop-Process -Id <pid> -Force`
（Windows 上不停会撞 `Unable to rename ... .jar.original`，见 CLAUDE.md 坑 15）。

### 3.3 修复后回归（两轮 `verify.ps1`，均为新 jar）

| 轮次 | 场景 | PASS/SKIP/FAIL | 留档 |
|---|---|---|---|
| 第 1 轮 | 库**带着上面那 5 行复现残留**跑 | **PASS 30 / SKIP 2 / FAIL 0** | `.tmp-b31/verify-after-fix-run1.txt` |
| 第 2 轮 | **整库重建回干净口径之后**跑 | **PASS 30 / SKIP 2 / FAIL 0** | `.tmp-b31/verify-after-fix-run2.txt` |

第 1 轮（带残留）跑完，库侧实测**不再新增任何残留**：

```
 signup_id | student_id | status   | sg_deleted | dur_id | sd_deleted | att_id | att_deleted
     10720 |       1504 | APPROVED |          0 |   7424 |          0 |   8236 |           0   <-- 旧 jar 留下的残留（修复不追溯）
     10721 |       1505 | APPROVED |          1 |   7425 |          1 |   8237 |           1   <-- 新 jar：三张表全部逻辑删除
(2 rows)
```
看板 `enrolled` / `hours` / `signin` / `audit` **与跑前逐项相同**（10720 / 19321.8 / 8236 / 7424），
即「不再漂」；`sql/09` 在该状态下仍 **20 项违规合计 0**（留档 `.tmp-b31/sql09-after-fix-run1.txt`）。

### 3.4 整库重建 + 干净态复验（第 2 轮）

重建链 `02 → 03 → 04 → 05 → 06 → 07 → 10 → 11 → 12`，**顺序未换**，整链 **9.7 秒**：

```
### sql/07_demo_scale.sql
    [note] [07 自检] 全部通过：学生 1500 人、活动 386 场、报名 10719 条、累计时长 19319.3 小时（演示账号 22.6 小时 / 标签 热心志愿者,长期坚持型,大型活动型）
=== 整链耗时 9.7 秒 ===
```
（每个脚本都 `executed in ... ms`；`06`/`11` 各报一条 `already exists, skipping` 的幂等 note，与既有一致。）

重建后库侧实测：

```
 students | activities | signups | hours   | durations | attendance | profiles | sg_dead | sd_dead | si_dead
     1503 |        389 |   10719 | 19319.3 |      7423 |       8235 |     1503 |       0 |       0 |       0
(1 rows)
```

`sql/09` 实跑（留档 `.tmp-b31/sql09-after-rebuild.txt`）：

```
99 | 汇总 | 检查项总数 20，违规合计 0 | 0
```

**第 2 轮 `verify.ps1` 跑前 / 跑后看板逐项对比**（`.tmp-b31/dash-before-final-verify.txt` / `dash-after-fix-run2.txt`）：

| 指标 | 跑前 | 跑后 | 结论 |
|---|---|---|---|
| `stats.activities` | 389 | 389 | 一致 |
| `stats.enrolled` | **10719** | **10719** | ✅ 不漂 |
| `stats.hours` | **19319.3** | **19319.3** | ✅ 不漂 |
| `signin.total` / `signed` | 8235 / 7423 | 8235 / 7423 | ✅ 不漂 |
| `audit.total` / 已通过 | 7423 / 5614 | 7423 / 5614 | ✅ 不漂 |
| `colleges` 行数 / 首位 | 5 / 外国语学院 299 人 | 5 / 外国语学院 299 人 | ✅ 不漂 |
| `stats.signRate` / `passRate` | 90.1% / 75.6% | 90.1% / 75.6% | ✅ 不漂 |

> **对照价值**：同一脚本、同一台机器、同一个库口径，**修复前跑一轮就漂**（10719→10720、19319.3→19321.8），
> **修复后跑一轮不漂** —— 这是本次修复最直接的对照证据。
>
> ⚠️ **一处如实说明**：`/portraits/distribution` 跑后仍与跑前有 6 个标签各 **−1**
> （社区服务型 393→392、环保行动型 287→286、大型活动型 232→231、校园服务型 223→222、
> 文化传播型 201→200、助老服务型 166→165），**这不是 B31 的漂移**：`verify.ps1` 第 5 步会调
> `POST /portraits/recompute`，脚本自己打印 `更新份数=@{scanned=1504; updated=4}` ——
> 运行期算法重写了 4 份画像的 `tags`，与 04/07 脚本生成的标签有差异（属既有的「脚本口径 vs 运行期算法」差异，
> 本轮**未**处理）。修复前那一轮同样存在这 6 个 −1，可对照 `.tmp-b31/dash-after-repro.txt`。
> `热心志愿者` 修复前 +1（被删学生仍被计数）、修复后 ±0（被删学生已被 `selectTagCounts` 的
> `si.deleted = 0` 排除）—— 这正是那处同族小修的实测效果。

### 3.5 收工态

| 项 | 值 |
|---|---|
| 8080 上跑的 jar | `volunteer-cert-portrait-server/vcp-boot/target/vcp-boot-1.0.0.jar`，**42,747,861 B / 17:20:29**（开发口径未带 prod profile） |
| 8080 pid | **45644**（17:27:29 起进程、17:27:33 完成启动 4.199 秒；监听 `::8080`） |
| 8081 prod 临时实例 | pid 46504，**已停**（`Get-NetTCPConnection -LocalPort 8081` 已无监听；见 3.6 末尾） |
| 库口径 | **1503 学生 / 389 活动 / 10719 报名 / 19319.3 小时**（`student_info`/`activity_signup`/`service_duration`/`attendance_record` 的 `deleted = 1` 行数全为 0）—— 最后成功查库时间 **2026-09-25 17:22–17:23** |
| `sql/09` | 检查项总数 20，**违规合计 0**（最后成功实跑 17:23） |

**「8080 跑的确实是新构建」的独立校验**（不靠时间戳推断）：直接读 jar 内嵌的模块 jar，
确认本轮新增的 5 个类都在里面 ——

```
BOOT-INF/lib/vcp-system-1.0.0.jar         143898  2026/9/25 17:19:50
BOOT-INF/lib/vcp-volunteer-1.0.0.jar      125904  2026/9/25 17:20:24
BOOT-INF/lib/vcp-certification-1.0.0.jar   50732  2026/9/25 17:20:26
BOOT-INF/lib/vcp-portrait-1.0.0.jar        47199  2026/9/25 17:20:26
--- vcp-system       : com/vcp/system/service/impl/StudentArchivePurger.class
                       com/vcp/system/service/StudentDurationPurgePort.class
                       com/vcp/system/service/StudentSignupPurgePort.class
--- vcp-volunteer    : com/vcp/volunteer/service/impl/StudentSignupPurgePortImpl.class
--- vcp-certification: com/vcp/certification/service/impl/StudentDurationPurgePortImpl.class
```
（复跑命令：`Add-Type -AssemblyName System.IO.Compression.FileSystem` 后 `[IO.Compression.ZipFile]::OpenRead(<jar>)`
逐层读 `BOOT-INF/lib/vcp-*.jar` 的条目名。`vcp-portrait` 只有**改动**没有新增类，故该行空。）

> **注意**：收工那一刻 8080 进程虽然活着，但**云库连接被打满**，因此带库的请求会返回
> `code=10000`（后端日志：`CannotGetJdbcConnectionException: Failed to obtain JDBC Connection`
> → `Caused by: PSQLException: FATAL: sorry, too many clients already`）。
> 这是**环境问题、不是本次改动引起**：同一个进程在 17:23 还能正常服务 `verify.ps1` 的全部 30 项；
> 库一旦有空闲连接，Hikari 会自行恢复，无需重启。留档 `.tmp-b31/boot-8080-final2.log`。
> 最后一次连接重试 **17:37:50** 仍失败（累计 30 余次），最后一次成功查库 **17:22–17:23**。

### 3.6 任务 B：prod profile 三项收紧实测（同一个 jar，8081 临时实例 pid 46504）

启动命令：`java -jar vcp-boot/target/vcp-boot-1.0.0.jar --spring.profiles.active=prod --server.port=8081`
启动日志：`The following 1 profile is active: "prod"` / `Tomcat started on port 8081` / `Started VcpApplication in 4.254 seconds`
留档：`.tmp-b31/boot-8081-prod.log`、脚本 `.tmp-b31/prod-checks.ps1`、输出 `.tmp-b31/prod-checks-out.txt`。

**① 文档路径不再免登录 —— ✅ 实测通过**

| 请求 | dev(8080) 对照 | prod(8081) |
|---|---|---|
| `GET /doc.html` | `HTTP/1.1 200` + `Content-Type: text/html` + Knife4j 页面（710 B） | `HTTP/1.1 200`（传输层）+ `Content-Type: application/json` + 正文 `{"code":20001,"message":"登录已过期，请重新登录"}`（60 B） |
| `GET /v3/api-docs` | `HTTP/1.1 200` + OpenAPI JSON **51618 B** | 正文 `{"code":20001,"message":"登录已过期，请重新登录"}` |

> 口径说明：本项目**所有接口都返回 HTTP 200**，业务结果在 body 的 `code` 里（`20001` = 未登录），
> 所以判据是 **body 的 `code` 与 `Content-Type`**，不是 HTTP 状态码 —— 与
> `docs/部署文档.md` 里「`curl -o /dev/null -w '%{http_code}'` 期望 401」那句写法**不一致**，
> 建议改成看 body（这一点建议由主会话决定是否采纳，我**没有**改部署文档）。

**② CORS 白名单生效 —— ✅ 实测通过**

预检请求：`OPTIONS /api/v1/auth/login`，`Origin: https://evil.example.com`，
`Access-Control-Request-Method: POST`，`Access-Control-Request-Headers: content-type`

| | dev(8080) | prod(8081) |
|---|---|---|
| 状态行 | `HTTP/1.1 200` | **`HTTP/1.1 403`** |
| `Access-Control-Allow-Origin` | **`https://evil.example.com`**（放行） | **无该响应头** |
| 其它 | `Allow-Methods: GET,POST,PUT,DELETE,OPTIONS`、`Allow-Credentials: true` | body `Invalid CORS request` |

**③ SQL 日志不再全量打印 —— ✅ 实测通过（含不依赖数据库的对照）**

| | dev(8080) 日志 | prod(8081) 日志 |
|---|---|---|
| `==>  Preparing:`（MyBatis SQL）行数 | **173** | **0** |
| `DEBUG` 行数 | **521** | **0** |
| 日志总行数 | 大量 | 28 |

**关键对照（不依赖数据库，因此不受本轮云库打满影响）**：dev 日志里有两条 `com.vcp.*` 的 DEBUG
是**启动期**输出、与数据库访问无关：

```
2026-09-25T17:20:38.317 DEBUG ... [main] com.vcp.VcpApplication            : Running with Spring Boot v4.1.1, Spring v7.0.9
2026-09-25T17:20:40.005 DEBUG ... [main] c.v.f.config.SecurityHeadersFilter : Filter 'securityHeadersFilter' configured for use
```
prod 日志里这两条**同样不存在**（0 条 DEBUG）→ 证明 `logging.level.com.vcp` 确实被 prod 覆盖成了 `info`，
不是「因为没发请求所以没日志」。

配置层依据：`vcp-boot/src/main/resources/application.yml` 的
`logging.level.com.vcp: debug`（注释明写「application-prod.yml 已设为 info」）与
`application-prod.yml:27-30` 的 `logging.level.com.vcp: info`。

测完已 `Stop-Process -Id 46504 -Force`，**8081 不再监听**，8080 未受影响。

---

## ④ 可直接粘贴的 Markdown 片段

### 4.1 插进 `docs/待办清单.md` 的 **B31**（销账）

> 替换 `docs/待办清单.md:710-727` 那一整条（`- [ ] **B31 删除用户不级联…**` 到 `…第 41 项。`），
> 或者保留原文、在其后追加下面这段。**建议改成 `- [x]` 并把标题改为「已修」。**

```markdown
- [x] **B31 删除用户不级联：业务记录留在库里并继续计入看板**（2026-09-25 发现，P1，**2026-09-25 已修并实测闭环**）
      ✅ **销账证据**：`DELETE /api/v1/system/users/{id}` 现在在**同一事务**里级联处理该学生的
      `student_info` / `activity_signup` / `attendance_record` / `service_duration`，并按 `sql/09`
      第 ⑦ 项的同一口径重算 `signed_count`。修好后重跑 `.tmp-verify/verify.ps1`
      （**PASS 30 / SKIP 2 / FAIL 0**），看板**跑前跑后逐项相同**：
      389 活动 / **10719 报名** / **19319.3 小时** / 签到 8235·7423 / 审核 7423·5614；
      库内 `deleted = 1` 的残留行数为 0；`sql/09` 实跑 **检查项总数 20、违规合计 0**。
      **对照**：同一个脚本在修复前跑一轮就漂（10719→10720、19319.3→19321.8、学生 1503→1504，
      且 `student_info` 里 `si.deleted = 0` 而 `sys_user.deleted = 1` 的行有 1 条）。
      **口径（写清楚，别再改回去）**：被删学生的报名是**逻辑删除 `deleted = 1`**、**保留原 `status`**，
      **不是**置 `CANCELED` —— 看板「报名总人数」的口径是
      `COUNT(*) FROM activity_signup WHERE deleted = 0`（`AnalyticsMapper.java:84`），
      `CANCELED` 行照样会被数进去，只改状态治不了本；保留原 `status` 是为了不抹掉
      「该生当时确实完成过这场活动」的审计事实。
      **实现**（跨模块走依赖倒置，未破坏模块依赖规则）：vcp-system 声明
      `StudentSignupPurgePort` / `StudentDurationPurgePort` 两个端口，
      分别由 vcp-volunteer / vcp-certification 实现；编排在
      `vcp-system/.../service/impl/StudentArchivePurger`（与建档的 `StudentArchiveRegistrar` 成对），
      由 `UserServiceImpl.deleteUser` 调用。
      **顺带修掉同族缺口**：`PortraitAggregateMapper.selectTagCounts` 缺 `si.deleted = 0`
      （`student_profile` 无 `deleted` 列，被删账号的画像快照仍计入标签分布），已补 JOIN。
      **仍未做的两件事**（如实记）：① `sql/09` 的检查项**仍是 20 项**（本轮**没有**加「业务记录属于
      已逻辑删除的用户」这一项，因为该数字被文档大量引用，留给主会话统一评估）；
      ② 修复**不追溯**已产生的脏数据 —— 存量脏数据要靠重跑整条重建链清掉（约 10 秒，本轮实测 9.7 秒）。
```

### 4.2 插进 `docs/下一步待办.md` 的**第 41 项**（销账）

> 替换 `docs/下一步待办.md:543-564` 那一整条（`41. [ ] 【新】**删除用户不级联…**` 到
> `…所以这条待办仍然挂着。`）。

```markdown
41. [x] 【新】**删除用户不级联：业务记录留在库里并继续计入看板**（后端 + SQL，中-高，**2026-09-25 发现、当日已修并实测闭环**）
    - 现象（修复前）：`DELETE /api/v1/system/users/{id}` 只把 `sys_user.deleted` 置 1；
      该学生的 `student_info`（仍 `deleted = 0`）、`activity_signup`、`service_duration`
      **全部留在库里，并继续参与统计**（看板、档案列表、画像分布都会带上他）。
    - **选定的口径**：删用户时**同事务级联** —— 软删 `student_info`；
      **逻辑删除**其全部报名（`deleted = 1`，**保留原 `status`**）并按 `sql/09` 第 ⑦ 项的同一谓词
      **重算** `signed_count`；作废其签到记录；软删其 `service_duration`。
      ⚠️ **不用「置 CANCELED」**：看板 `enrolled` 的口径是
      `COUNT(*) FROM activity_signup WHERE deleted = 0`（`AnalyticsMapper.java:84`），
      `CANCELED` 行仍会被数进去 —— 实测只改状态的话看板照样 +1，验收过不了。
    - **实现**：vcp-system 声明 `StudentSignupPurgePort` / `StudentDurationPurgePort` 两个端口
      （照 `OrgLookupPort` / `OrgCollegeCountPort` 的既有套路，**必需注入**而非可缺省 ——
      缺实现时安静跳过就是静默的半个级联），分别由 vcp-volunteer / vcp-certification 实现；
      编排在 `StudentArchivePurger.purgeByUserId`，由 `UserServiceImpl.deleteUser` 在同一个
      `@Transactional` 里调用。新增 SQL 只有两条：`VolunteerActivityMapper.refreshSignedCountByStudent`、
      `AttendanceRecordMapper.invalidateByStudentId`。
    - **验收（已实跑）**：造学生 → 报名 → 开时长 → 审核通过 → 删该用户 → 看板数字**回到删除前**
      （389 / **10719** / **19319.3** / 签到 8235·7423 / 审核 7423·5614），
      库内 `student_info` / `activity_signup` / `service_duration` / `attendance_record`
      的 `deleted = 1` 行数全 0，`sql/09` **20 项违规合计 0**。
      脚本：`.tmp-verify/verify.ps1` **PASS 30 / SKIP 2 / FAIL 0**；
      构建：`mvn -B package -DskipTests` **11 个模块 BUILD SUCCESS**。
    - **顺带闭环**：`PortraitAggregateMapper.selectTagCounts` 补 `si.deleted = 0`
      （`student_profile` 无 `deleted` 列，被删账号的快照此前仍计入标签分布；同 Mapper 其它查询
      一直都是这么过滤的）。
    - **仍未做**：① `sql/09` **未加**「业务记录属于已逻辑删除的用户」这一检查项
      （检查项总数仍是 20，因该数字被文档大量引用，留给主会话统一评估）；
      ② 修复**不追溯**存量脏数据 —— 本轮是靠重跑整条重建链（实测 **9.7 秒**）恢复干净口径的。
    - 详细证据与改动清单：`docs/_handoff/agent-2-b31.md`。
```

### 4.3 插进 `docs/测试报告.md` 的 **§5 那一行「删除用户不级联」**（改为已修 + 证据）

> 替换 `docs/测试报告.md:205` 那一整行表格行。

```markdown
| —（**2026-09-25 新发现，用例表外**） | ~~**删除用户不级联**：`DELETE /api/v1/system/users/{id}` 只置 `sys_user.deleted = 1`，该学生的 `student_info` / 报名 / 时长仍留在库里并**继续计入看板**（实测 1503→1505 学生、10719→10721 报名、19319.3→19321.8 小时，而 `sql/09` 20 项仍全 0）~~ | ✅ **2026-09-25 已修复并实测闭环**：删用户时同事务级联软删 `student_info` / 报名 / 签到 / `service_duration`，并按 `sql/09` 第 ⑦ 项口径重算 `signed_count`。证据：重跑 `verify.ps1` **PASS 30 / SKIP 2 / FAIL 0**，看板跑前跑后逐项相同（389 / **10719** / **19319.3** / 签到 8235·7423 / 审核 7423·5614），库内 `deleted = 1` 残留行 0，`sql/09` **检查项总数 20、违规合计 0**；对照修复前同一脚本跑一轮即漂（10719→10720、19319.3→19321.8）。⚠️ **演示时仍建议不要删用户**（删了不会漂，但会永久减少一个演示账号）。见 `docs/下一步待办.md` 第 41 项 / `docs/待办清单.md` 的 B31 / `docs/_handoff/agent-2-b31.md` |
```

> 同时建议把 `docs/测试报告.md:208-210` 的补记段落最后加一句（可选）：

```markdown
> **2026-09-25 再补**：本表最后一条「删除用户不级联」已由代理 2 修复并实测闭环（口径为
> **逻辑删除报名行**而非置 `CANCELED`，理由见 `docs/_handoff/agent-2-b31.md` ②-6），
> 该条已从「已知限制」转为「已修复」，上方表格行已就地改写。
```

### 4.4 插进 `docs/测试报告.md` 的 **§6.3「prod profile 三项收紧」**（改为已实测）

> 替换 `docs/测试报告.md:257-258` 那一条（`3. **prod profile 三项收紧**：只做了配置层核对…未实际以
> --spring.profiles.active=prod 启动验证。`）。同时把 `:248` 的表格行改为已实测（片段附在后面）。

```markdown
3. **prod profile 三项收紧** —— ✅ **2026-09-25 已三条全实测**（不再是配置层核对）。
   用**同一个新构建的 jar** 在 **8081** 起临时实例
   （`java -jar vcp-boot/target/vcp-boot-1.0.0.jar --spring.profiles.active=prod --server.port=8081`，
   启动日志 `The following 1 profile is active: "prod"` / `Started VcpApplication in 4.254 seconds`），
   与 8080 的开发口径实例**逐项对照**，测完已停掉 8081：

   | 收紧项 | dev(8080) 对照 | prod(8081) 实测 | 结论 |
   |---|---|---|---|
   | ① 文档路径免登录 | `GET /doc.html` → `200` + `text/html`（Knife4j 页面 710 B）；`GET /v3/api-docs` → `200` + OpenAPI JSON 51618 B | 两者正文均为 `{"code":20001,"message":"登录已过期，请重新登录"}`、`Content-Type: application/json` | ✅ 收紧生效 |
   | ② CORS 白名单 | 预检 `Origin: https://evil.example.com` → `200` + **`Access-Control-Allow-Origin: https://evil.example.com`**（放行） | 同一预检 → **`403`** + body `Invalid CORS request` + **无 `Access-Control-Allow-Origin` 响应头** | ✅ 收紧生效 |
   | ③ SQL 日志 | 日志 `==>  Preparing:` **173** 行、`DEBUG` **521** 行 | 日志 `==>  Preparing:` **0** 行、`DEBUG` **0** 行（总 28 行） | ✅ 收紧生效 |

   ⚠️ **判据说明**：本项目所有接口都返回 **HTTP 200**、业务结果在 body 的 `code` 里
   （`20001` = 未登录），所以 ① 的判据是 **body 的 `code` 与 `Content-Type`**，
   **不是 HTTP 状态码** —— `docs/部署文档.md` 里「`curl -o /dev/null -w '%{http_code}'` 期望 401」
   那句写法与此不一致，建议改为看 body（**本轮未改部署文档**，该文件归代理 3）。
   ③ 有一处**不依赖数据库**的硬对照：dev 日志里有两条 `com.vcp.*` 的 **启动期** DEBUG
   （`com.vcp.VcpApplication : Running with Spring Boot v4.1.1…`、
   `c.v.f.config.SecurityHeadersFilter : Filter 'securityHeadersFilter' configured for use`），
   prod 日志里**同样一条都没有** → 证明是日志级别被 prod 覆盖，而不是「没发请求所以没日志」。
   留档：`.tmp-b31/boot-8081-prod.log`、`.tmp-b31/prod-checks.ps1`、`.tmp-b31/prod-checks-out.txt`。
```

> 替换 `docs/测试报告.md:248` 的表格行：

```markdown
   | **prod profile 实跑**（`/doc.html` 不再免登录、CORS 收紧、SQL 日志降级） | ✅ **已实测（2026-09-25）** | 8081 起 `--spring.profiles.active=prod` 临时实例：`/doc.html` 与 `/v3/api-docs` 正文均为 `{"code":20001,…}`（开发口径对照为 200 + 页面/JSON）；预检 `Origin: https://evil.example.com` → **403 Invalid CORS request**（开发口径 200 + 回显该 Origin）；日志 `==>  Preparing:` **0** 行 / `DEBUG` **0** 行（开发口径 173 / 521）。见本节第 3 条 |
```

### 4.5 插进 `docs/部署文档.md` 的「九、本次核实到什么程度」一节

> ⚠️ **该文件归代理 3 独占，我没有改它**。下面三段供主会话/代理 3 取用。
> ① 把 `:456` 那一行**从「未实测」表移到「有实跑证据」表**；
> ② `:442` 那行补一句实测；
> ③ `:442` 里「期望 401」的写法建议一并修正（见片段内注）。

**片段 A：加进「### ✅ 有实跑证据」表格的末尾（`:445` 之后）**

```markdown
| **prod profile 三处收口真的生效**（`/doc.html` 与 `/v3/api-docs` 不再免登录、CORS 白名单拦住任意 Origin、SQL 日志不再全量打印） | 2026-09-25 用**同一个新构建的 jar** 在 **8081** 起临时实例（`--spring.profiles.active=prod --server.port=8081`，启动日志 `The following 1 profile is active: "prod"`、`Started VcpApplication in 4.254 seconds`），与 8080 的开发口径实例逐项对照：① `GET /doc.html` → 正文 `{"code":20001,"message":"登录已过期，请重新登录"}`、`Content-Type: application/json`（dev 对照：200 + Knife4j 页面 710 B）；`GET /v3/api-docs` 同样为 `code=20001`（dev 对照：OpenAPI JSON 51618 B）。② 预检 `OPTIONS /api/v1/auth/login` + `Origin: https://evil.example.com` → **`HTTP/1.1 403` + `Invalid CORS request` + 无 `Access-Control-Allow-Origin` 响应头**（dev 对照：200 + `Access-Control-Allow-Origin: https://evil.example.com`）。③ 实例日志 `==>  Preparing:` **0** 行 / `DEBUG` **0** 行（dev 对照：**173** / **521**），且 dev 日志里两条 `com.vcp.*` 的**启动期** DEBUG 在 prod 日志里同样一条都没有（**不依赖数据库的对照**，排除「没发请求所以没日志」）。测完已停掉 8081 |
```

**片段 B：删掉「### ⚠️ 未实测」表格里的 `:456` 那一行**

```markdown
| ~~**prod profile 的实际启动效果**~~ | ✅ 2026-09-25 已实测（见上一节表格最后一行），本行移出「未实测」 |
```

**片段 C：修正 `:442` 那一行的写法（判据从 HTTP 状态码改为 body 的 `code`）**

```markdown
| `application-prod.yml` 三处收口、且不激活则不生效 | 直接读该文件（`application-prod.yml:16-30`）；**并且 2026-09-25 已用 8081 的 prod 临时实例实测三处收口全部生效**（见上一节表格最后一行）。⚠️ **判据要用 body 的 `code`，不是 HTTP 状态码**：本项目所有接口（含被拦截的请求）都返回 **HTTP 200**，未登录是 body 里的 `{"code":20001,…}` —— 第七节验收清单里「`curl -o /dev/null -w '%{http_code}' … → 期望 401`」**测不出这个结论**，请改为 `curl -s http://127.0.0.1:8080/doc.html` 看正文是否为 `code:20001` |
```

---

## ⑤ 未做 / 受限项（如实记录）

1. **`sql/09` 未加新检查项**（检查项总数仍是 **20**）。任务书要求「不要改 `sql/09` 的检查项数」，
   我遵守了；但「业务记录属于已逻辑删除的用户」这一项确实值得加（现有 20 项抓不到这类漂移，
   修复前那轮漂移发生时 20 项仍全 0）。**建议与检查项 21 的措辞一起交主会话统一评估**，
   因为「20 项」被 `docs/` 多处引用。
2. **修复不追溯存量脏数据**。级联只在新的 `DELETE` 调用上生效；本轮造的 5 行复现残留
   （`student_info` 1504 / `activity_signup` 10720 / `service_duration` 7424 / `attendance_record` 8236 /
   `student_profile` 一行）是**靠重跑整条重建链清掉的**，不是靠修复本身。
   若库里已有类似脏数据，仍需重跑重建链或写一次性订正 SQL（**未写**）。
3. **`student_profile` 的快照行不随账号删除**。该表没有 `deleted` 列，我采取的是**读取侧过滤**
   （补了 `selectTagCounts` 的 `si.deleted = 0`），**没有**加第三个端口去物理删除它。
   理由：它是整表重算的派生产物，所有读路径现在都会 JOIN `student_info` 过滤 `si.deleted = 0`。
   代价：库里会长期留着已删账号的画像快照行（`student_profile` 行数会比在用学生数多），
   不影响任何接口输出与 `sql/09`。**若团队认为必须物理清掉，需要再加一个端口**（本轮未做）。
4. **`notification`（发给该账号的通知）未清理**。读取路径都按当前登录用户过滤，已删账号的通知
   没有任何入口能读到；`duration_audit`（审核流水）同样刻意保留（只追加、无 `deleted` 列）。
   两者都写进了 `StudentArchivePurger` 的类注释「刻意不做的两件事」。
5. **`verify.ps1` 跑完后 `/portraits/distribution` 的 6 个标签各 −1**，原因是脚本第 5 步的
   `POST /portraits/recompute` 会按运行期算法重写 4 份画像的 `tags`（脚本自报 `updated=4`），
   与 04/07 脚本生成的标签存在差异。**这是既有的「脚本口径 vs 运行期算法」差异，本轮未处理**，
   修复前那一轮同样存在，不是 B31 的漂移。**建议另开一条待办**。
6. **`docs/部署文档.md` 第七节验收清单里「`/doc.html` 期望 401」的写法与本项目实际不符**
   （本项目被拦截的请求也返回 HTTP 200，业务码在 body）。我在 4.5 片段 C 里给了修正文案，
   但**没有改那个文件**（归代理 3）。
7. **环境阻塞（影响本轮最后一步的复跑）**：从 17:24 起云库连接数被**外部客户端**打满
   （`FATAL: sorry, too many clients already`），本机所有 JDBC 只读工具连续失败
   （17:24–17:35 之间重试 **30 余次**全部失败，最后一次 17:34:51；`pg_stat_activity` 里绝大多数是
   挂了 20 小时以上的 `idle` 会话，属 Navicat/DBeaver 之类的泄漏连接或队友机器上的连接池）。
   **已确认不是本机泄漏**：17:35 查 `Win32_Process` 只有两个 `java.exe`
   （8080 的 45644 与 8081 的 46504，都是本轮我自己起的应用），没有任何挂住的 JDBC 工具进程；
   且**没有空闲连接可用时无法执行 `pg_terminate_backend` 自救**（需要先有一个连接）。
   **因此**：
   - ✅ **已完成并留档的**：整库重建后的库侧实测（1503 / 389 / 10719 / 19319.3、`deleted = 1` 全 0）、
     重建后的 `sql/09`（20 项 / 违规合计 0）、第 2 轮 `verify.ps1` 跑前跑后的**看板 API 逐项对比**
     （全部一致，见 3.4）；
   - ❌ **未能复跑的**：第 2 轮 `verify.ps1` **跑完之后**再跑一次 `sql/09` 与一次库侧原始计数
     （需要 JDBC 连接）。**替代证据**：跑完后的看板 API 逐项等于干净口径
     （10719 / 19319.3 / 签到 8235·7423 / 审核 7423·5614），且第 1 轮（同样带残留状态下）
     实测过「级联后 `sql/09` 仍为 20 项 0」。**这一条请在库恢复后由主会话补跑确认**
     （命令见 ⑤ 附），我不把它写成「已验证」。
   - 另注：`student_profile` 的表行数在跑完第 2 轮后应为 **1504**（比在用学生数 1503 多 1 行，
     即被删学生的快照），这是 ⑤-3 的已知代价，不是缺陷。
8. **`signed_count` 重算方法在「活动已被逻辑删除」时会被跳过**（`refreshSignedCountByStudent` 带
   `va.deleted = 0`）。此时 `sql/09` 第 ⑦ 项本来也不统计已删活动，故无影响；如实记在这里备查。

### ⑤ 附：库恢复后建议补跑的两条命令

```powershell
# 0) 先清陈旧 idle 会话（连接打满时先做这一步；本机没有 psql，用项目里的 JDBC 工具）
#    连接可用后：SELECT pid FROM pg_stat_activity WHERE state='idle' AND state_change < now() - interval '30 minutes';
#    → SELECT pg_terminate_backend(pid) ...
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# 1) 库侧原始计数（期望 1503 / 389 / 10719 / 19319.3，且四个 deleted=1 计数全 0）
java "-Dstdout.encoding=UTF-8" -cp "$env:USERPROFILE\.m2\repository\org\postgresql\postgresql\42.7.13\postgresql-42.7.13.jar" .tmp-run-sql/Q.java . "SELECT (SELECT COUNT(*) FROM student_info WHERE deleted=0) students, (SELECT COUNT(*) FROM volunteer_activity WHERE deleted=0) activities, (SELECT COUNT(*) FROM activity_signup WHERE deleted=0) signups, (SELECT ROUND(COALESCE(SUM(total_duration),0),1) FROM student_info WHERE deleted=0) hours, (SELECT COUNT(*) FROM student_info WHERE deleted=1) si_dead, (SELECT COUNT(*) FROM activity_signup WHERE deleted=1) sg_dead, (SELECT COUNT(*) FROM service_duration WHERE deleted=1) sd_dead, (SELECT COUNT(*) FROM attendance_record WHERE deleted=1) att_dead"

# 2) sql/09（期望汇总行「检查项总数 20，违规合计 0」）
java "-Dstdout.encoding=UTF-8" -cp "$env:USERPROFILE\.m2\repository\org\postgresql\postgresql\42.7.13\postgresql-42.7.13.jar;.tmp-run-sql/classes" RunQueryFile . sql/09_consistency_check.sql
```

---

## 附：本轮用到的临时工具（都在 `.gitignore` 覆盖的 `.tmp-*` 下，未入库）

| 路径 | 用途 |
|---|---|
| `.tmp-b31/dash.ps1` | 打印看板核心指标 + 标签分布（只读），`powershell -NoProfile -ExecutionPolicy Bypass -File .tmp-b31/dash.ps1 8080` |
| `.tmp-b31/prod-checks.ps1` | prod profile 三项收紧对照脚本 |
| `.tmp-b31/repro-before-fix.txt` | 修复前（旧 jar）`verify.ps1` 原始输出 |
| `.tmp-b31/verify-after-fix-run1.txt` / `run2.txt` | 修复后两轮 `verify.ps1` 原始输出 |
| `.tmp-b31/dash-before-repro.txt` / `dash-after-repro.txt` / `dash-before-final-verify.txt` / `dash-after-fix-run1.txt` / `dash-after-fix-run2.txt` | 各阶段看板快照 |
| `.tmp-b31/mvn-package.log` | `mvn -B package -DskipTests` 完整输出 |
| `.tmp-b31/sql09-after-fix-run1.txt` / `sql09-after-rebuild.txt` | `sql/09` 两次实跑输出 |
| `.tmp-b31/boot-8080.log` / `boot-8080-final2.log` / `boot-8081-prod.log` | 各实例启动与运行日志 |
| `.tmp-b31/prod-checks-out.txt` | prod 三项实测完整输出 |

> ⚠️ **PowerShell 5.1 脚本必须带 UTF-8 BOM**：本轮新建的两个 `.ps1` 起初因无 BOM 被 5.1 按 GBK 解码，
> 中文注释里的全角括号被拆坏、**吃掉引号导致语法错误**（`You must provide a value expression following the '/' operator`）。
> 现有 `verify.ps1` 是带 BOM 的。以后往 `.tmp-*` 里加脚本请照此办理。
