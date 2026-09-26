# 性能测试工具（deploy/perf）

本目录只放**工具与说明**，不放测试结果。压测工具是单文件 Java 程序（`loadtest.java`），
零第三方依赖，JDK 21 直接以源码模式运行，不需要 maven、k6 或 JMeter。

---

## 一、前置条件

| 项 | 要求 |
| --- | --- |
| JDK | 21+（`java -version` 能看到 21） |
| 后端 | 已启动并监听 `8080`（Spring Boot，数据库连通） |
| 账号 | `admin` / `org_admin` / `student`，口令均为 `123456`（见 `sql/10_base_and_test_accounts.sql`） |
| 数据 | 演示数据在位（`sql/04`、`sql/07` 导入后：1001 学生 / 10 活动 / 2349 报名 …） |
| 工作目录 | **仓库根目录**（命令里的路径按根目录写） |

Windows PowerShell 下中文输出建议先切 UTF-8，否则控制台会花屏（不影响统计，只影响看）：

```powershell
chcp 65001 | Out-Null
$OutputEncoding = [System.Text.Encoding]::UTF8
```

---

## 二、怎么跑

```powershell
# 1) 冒烟：先确认后端与账号都正常（5 用户 10 秒只读）
java deploy/perf/loadtest.java --users 5 --seconds 10 --scenario read

# 2) 只读场景：50 并发 30 秒（默认 base 是 http://localhost:8080）
java deploy/perf/loadtest.java --users 50 --seconds 30 --scenario read

# 3) 写场景：学生报名 + 签到签退
java deploy/perf/loadtest.java --users 50 --seconds 30 --scenario write

# 4) 混合场景（读迭代:写迭代 = 7:3）
java deploy/perf/loadtest.java --users 50 --seconds 60 --scenario mixed --think 20

# 5) 换地址 / 换口令
java deploy/perf/loadtest.java --base http://192.168.1.10:8080 --users 100 --seconds 60 --scenario read

# 6) 只自检（不连后端）：解析器与参数解析是否正常
java deploy/perf/loadtest.java --selftest
```

参数一览（全部可选）：

| 参数 | 默认 | 说明 |
| --- | --- | --- |
| `--base` | `http://localhost:8080` | 被测服务地址 |
| `--users` | `50` | 并发虚拟用户数（= 线程数） |
| `--seconds` | `30` | 正式压测时长（秒），预热流量不计入统计 |
| `--scenario` | `mixed` | `read` / `write` / `mixed` |
| `--think` | `10` | 每次请求后随机等待 0~N 毫秒（模拟用户操作间隔） |
| `--warmup` | `2` | 预热秒数（JIT、连接池、MyBatis 语句缓存都在这一段热起来） |
| `--timeout` | `20000` | 单请求超时（毫秒） |
| `--sessions` | `0` | 登录会话总数，0=自动（≈users，三角色各 1/3，上限 24） |
| `--password` | `123456` | 测试账号口令 |

> `--users` 是**并发虚拟用户**，不是"总人次"。每个用户闭环：发请求 → 思考 → 再发请求。
> 50 用户、`--think 10` 大致对应"50 个人同时在线、每 10 毫秒点一下"，
> 比真实用户激进得多，作为压力上限使用。

---

## 三、读结果的正确姿势

**判定成功必须看响应体的 `code`，不能只看 HTTP 状态码。**
本系统所有业务异常（名额已满、重复报名、签到窗口未开、无权限…）都返回 `HTTP 200 + code != 0`，
只看 HTTP 会把"全被拒绝"报成"100% 成功"。工具把结果分成三档：

```
成功(code=0)          正常返回
业务拒绝(code!=0)     业务规则校验（预期内，见下方"业务拒绝原因 TOP5"）
HTTP失败              网络错误 / 4xx / 5xx —— 这一档 > 0 才是真故障
```

关键指标怎么解读：

| 指标 | 看什么 |
| --- | --- |
| **HTTP失败** | 必须是 0；非 0 先查后端日志，不要先看延迟 |
| **业务拒绝原因 TOP5** | 演示库下出现 `30012 当前状态无法签到` / `30013 请先签到再签退` / `30007 重复报名` 属**预期**（活动时间窗没开、学生已报满活动），不算故障 |
| **RPS** | 吞吐；只读场景先看它 |
| **p95 / p99** | 尾延迟；`avg` 会被少数慢请求带偏，压测主要看 p95/p99 |
| **按接口分组** | 找出最慢接口。只读场景里 `/analytics/dashboard` 通常最慢（一次请求内要跑 11 条聚合 SQL） |
| **最后一行"结论"** | 一句话总结，可直接贴进报告 |

典型"正常"的只读结果长这样（本机、50 并发，数字仅供对照）：

```
总请求数 3~6 万 / HTTP 失败 0 / 业务拒绝 0
RPS 数百~上千，p50 < 50ms，p95 < 300ms
最慢接口 GET /analytics/dashboard（p95 显著高于其它接口）
```

**已知会影响结果的现状（不是工具问题）**：

1. `GET /api/v1/categories` 曾在 2026-09-27 之前**恒返回 500**：`ActivityCategoryMapper.xml` 查了
   `c.remark`，而库里 `activity_category` 没有该列（补列语句原先只存在于已删除的 07 脚本）。
   现已把 `ALTER TABLE activity_category ADD COLUMN IF NOT EXISTS remark` 并入
   `sql/06_backend_gap_fix2.sql`，云库也已补列、接口实测恢复（0.16ms）。
   若你的库还没跑过 06 的最新版本，该接口会在压测里稳定计入"HTTP失败"，先补列再压。
2. 签到/签退在**演示库当前时间下必然被拒绝**：10 场活动要么已结束、要么 9/30 之后才开始，
   超出"开始前 30 分钟 ~ 结束后 30 分钟"的签到窗口。写场景的这两个接口用于测量
   "校验失败路径"的耗时，不代表签到功能有问题。
3. 写场景的报名会**真实写库**：学生已报名 9 个活动，只剩 1 个 PUBLISHED 可报；
   报满后工具自动退化为"取消一条待审核/已通过报名 → 立即重新报名"的循环
   （服务端支持复用 CANCELED 行），因此压测期间 `activity_signup` 与
   `volunteer_activity.signed_count` 会被反复改动。压完可用 `sql/09_consistency_check.sql`
   做一致性自检。

---

## 四、用 k6 复现

本机没装 k6 也能用上面的 Java 工具；如果要做更专业的压测（阶段压力、阈值断言、Prometheus 输出），
可以用下面的等价 k6 脚本。安装：<https://k6.io/docs/get-started/installation/>

`k6-read.js`（只读场景，50 VU × 30s，与 Java 工具口径一致）：

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://localhost:8080';
const bizFail = new Rate('biz_reject');           // 业务拒绝率（对照 loadtest 的"业务拒绝"档）
const endpoints = [
  '/api/v1/activities?page=1&pageSize=10',
  '/api/v1/durations?page=1&pageSize=10',
  '/api/v1/portraits/distribution',               // 仅学校管理员
  '/api/v1/analytics/dashboard',
  '/api/v1/system/users?page=1&pageSize=10',      // 仅学校管理员
  '/api/v1/signups?page=1&pageSize=10',
  '/api/v1/attendance/mine?page=1&pageSize=10',   // 仅学生
  '/api/v1/portraits/me',                         // 仅学生
  '/api/v1/system/notifications?page=1&pageSize=10',
  '/api/v1/categories',
];

export const options = {
  scenarios: {
    read: { executor: 'constant-vus', vus: 50, duration: '30s' },
  },
  // 本系统业务失败也是 HTTP 200，所以阈值只看 http_req_failed 与延迟
  thresholds: {
    http_req_failed: ['rate==0'],
    http_req_duration: ['p(95)<1000'],
  },
};

export function setup() {
  const login = (username) => {
    const res = http.post(`${BASE}/api/v1/auth/login`,
      JSON.stringify({ username, password: '123456' }),
      { headers: { 'Content-Type': 'application/json' } });
    const json = JSON.parse(res.body);
    if (json.code !== 0) throw new Error(`login ${username} failed: ${res.body}`);
    return json.data.token;
  };
  return { admin: login('admin'), orgAdmin: login('org_admin'), student: login('student') };
}

// 每个 VU 按 1/3 概率扮演一个角色，只打该角色有权限的接口（与 loadtest.java 一致）
export default function (tokens) {
  const role = __VU % 3;
  const token = role === 0 ? tokens.admin : (role === 1 ? tokens.orgAdmin : tokens.student);
  const allowed = role === 0
    ? endpoints
    : role === 1
      ? endpoints.filter((p) => !p.includes('system/users') && !p.includes('distribution') && !p.includes('portraits/me') && !p.includes('attendance/mine'))
      : endpoints.filter((p) => !p.includes('system/users') && !p.includes('distribution'));
  const path = allowed[Math.floor(Math.random() * allowed.length)];
  const res = http.get(BASE + path, { headers: { Authorization: `Bearer ${token}` } });
  let code = -1;
  try { code = JSON.parse(res.body).code; } catch (e) { /* 非 JSON，按失败计 */ }
  bizFail.add(code !== 0);
  check(res, { 'http 200': (r) => r.status === 200, 'code==0': () => code === 0 });
  sleep(Math.random() * 0.01);
}
```

写场景（报名 + 签到签退）在 k6 里同样可行，但幂等逻辑（查重、换活动、取消后重报）
用 JS 写会更啰嗦，直接用 Java 工具的 `--scenario write` 更省事；若必须在 k6 里跑写，
把 `POST /api/v1/signups`（body `{"activityId":9,"reason":"perf-test"}`）与
`POST /api/v1/attendance/sign-in|sign-out`（body `{"attendanceId":1}`）按同样的
"先查再报、30007 就换一个"逻辑包一层即可。

运行：

```bash
k6 run -e BASE=http://localhost:8080 k6-read.js
```

## 五、用 JMeter 复现

1. 线程组：线程数 50、Ramp-up 5s、循环 30s（或"调度器 + 持续时间"）。
2. 登录：`POST /api/v1/auth/login`，body `{"username":"admin","password":"123456"}`，
   用 **JSON Extractor** 取 `$.data.token` 写入变量 `token`（放在 setUp 线程组里只登一次）。
3. HTTP 信息头管理器：`Authorization: Bearer ${token}`、`Accept-Encoding: gzip`。
4. 用 **Random Controller** 在 10 个 GET 取样器之间随机（各取样器路径见上表）。
5. 断言别只看"响应代码 200"：加 **JSON 断言** `$.code == 0`，
   否则业务拒绝会被算成成功（与本文第三节同一口径）。
6. 监听器用 **Aggregate Report**（看 Average/90%/95%/99%）与 **Summary Report**；
   压测时关闭"查看结果树"，否则 JMeter 自己会先 OOM。

---

## 六、这套工具测不到的东西（需要另配环境）

- **浏览器端渲染耗时**：本工具只测接口，前端的 28 个页面渲染要 Chrome DevTools / Lighthouse。
- **数据库内部指标**（慢 SQL 明细、连接等待）：云库没开 `pg_stat_statements`；
  数据库侧的口径与证据见 `sql/14_performance_indexes.sql` 与本次性能报告。
- **长时间稳定性 / 内存泄漏**：把 `--seconds` 调到 1800 以上跑一次即可观察；
  注意观察后端 GC 与 Hikari 连接数（`/actuator` 未开，用 jconsole/jcmd）。
