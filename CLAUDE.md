# CLAUDE.md

> 本文件由 Claude Code 自动加载。放在仓库根目录，任何新会话都会读到，
> 用来把项目上下文与踩过的坑带过去。**新增约定或踩坑后请补充到对应章节。**

## 项目

**高校志愿服务时长认证与公益画像数据分析系统**（毕业设计，3 人小组）

围绕志愿活动发布 → 学生报名 → 组织审核 → 签到签退 → 组织提交时长 →
学校审核 → 学生获得时长 → 公益画像与数据看板，形成完整闭环。
三个角色：学生 / 组织管理员 / 学校管理员。

## 目录结构

```
volunteer-cert-portrait/
├── volunteer-cert-portrait-server/   # 后端（Spring Boot 多模块 Maven）
├── volunteer-cert-portrait-web/      # 前端（Vue 3 + Vite）
├── sql/                              # 建表 + 初始化 + 演示数据 + 一致性自检脚本（附 README）
└── docs/                             # 待办清单、下一步待办、答辩前待办、会话记录、进展与待办、规则方案、开发计划、知识库
```

## 技术栈（版本均已核实可用）

| 组件 | 版本 |
|---|---|
| Spring Boot | 4.1.1（Spring Framework 7.0.9、内嵌 Tomcat 11） |
| JDK | 21 |
| Maven | 3.9.16 |
| ORM | MyBatis-Plus 3.5.17（**必须用 `mybatis-plus-spring-boot4-starter`**） |
| 认证 | Sa-Token 1.46.0（**必须用 `sa-token-spring-boot4-starter`**） |
| 接口文档 | Knife4j Next 5.7.5（groupId 是 `com.baizhukui`，**不是** `com.github.xiaoymin`） |
| 数据库 | PostgreSQL 18.6 |

## 常用命令

```bash
# 构建（11 个模块）
cd volunteer-cert-portrait-server && mvn -B package -DskipTests

# 启动
java -jar vcp-boot/target/vcp-boot-1.0.0.jar

# 接口文档
open http://localhost:8080/doc.html

# 重建数据库（会清空数据）
cd sql
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 02_schema.sql
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 03_init_data.sql
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 04_demo_data.sql

# 一致性自检（只读、可重复执行，前置条件 01~06；20 项违规数全 0 即通过）
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 09_consistency_check.sql
```

数据库连接信息见 `vcp-boot/src/main/resources/application.yml`；口令等本地覆盖放在
`volunteer-cert-portrait-server/vcp-boot/src/main/resources/application-local.yml`
（已 gitignore，模板见同目录 `application-local.yml.example`），仓库内文件不含任何密钥。
默认账号：`admin` / `org_admin` / `student`（密码均为 `123456`，**库内存 BCrypt 密文、明文不落库**；
老库需跑 `sql/08_password_bcrypt.sql` 刷密文，新库不用）。

## 后端模块结构

```
vcp-boot          启动类 VcpApplication + 配置 + 唯一可执行 jar
vcp-framework     安全认证、全局异常、ORM 配置、字段自动填充、操作日志
vcp-common        统一返回 R<T>、异常体系、常量、枚举、工具类（无业务依赖）
vcp-system        用户/角色/学生档案/字典/通知/日志/附件
vcp-org           志愿组织
vcp-volunteer     活动分类/活动/报名/签到
vcp-certification 服务时长/审核
vcp-portrait      公益画像
vcp-analytics     看板统计（只读聚合，无独立表）
vcp-dependencies  独立 BOM
```

**依赖规则**（防乱用，答辩加分）：禁止反向依赖与循环依赖；跨模块只调用对方
**Service 接口**，禁止直接访问对方 Mapper/表；通知类耦合用 Spring Event 解耦。
`vcp-dependencies` **不得继承聚合根**（原因见下方"踩过的坑"）。

## 关键约定

- 主键 `BIGSERIAL`（数据库自增）→ MyBatis-Plus 必须配 `id-type: auto`
- 命名 `snake_case`；时间字段 `create_time` / `update_time`
- 时长字段 `NUMERIC(10,1)`，单位**小时**
- **状态字段统一存英文大写码**，中文由 `sys_dict` 翻译。完整码表见
  `docs/后端进展与待办.md` 第七节
- 逻辑删除 `deleted SMALLINT`，但**仅用于业务数据表**；关联表、流水/日志表不加
- 接口前缀 `/api/v1/`；统一返回 `{ code, message, data }`，成功 `code=0`
- 权限标识格式 `域:资源:操作`（如 `system:user:list`）
- 枚举里 `APPROVED`/`REJECTED`（状态）与 `APPROVE`/`REJECT`（审核动作）**不同形**，别混

## ⚠️ 踩过的坑（改代码前先看）

1. **`vcp-dependencies` 不得声明 `parent` 为聚合根**
   否则与聚合根的 `dependencyManagement` BOM 导入形成循环，Maven 直接拒绝读取整个工程。
   pom 里已有注释说明，别"顺手补回去"。

2. **Boot 4 没有 `spring-boot-starter-aop`**
   已改名为 `spring-boot-starter-aspectj`。旧坐标在 4.x 下没有发布（最后版本停在 3.5.16），
   且不在 Boot 4 的 BOM 里，不写版本号会报 version missing。

3. **YAML 会把以 0 开头的数字串当八进制整数**
   `password: 012345` 不加引号会被解析成 `5349`。口令、手机号等一律**加引号**。

4. **`knife4j.enable` 默认为 `false` 且没有 `matchIfMissing`**
   不显式开启则增强能力静默失效，但 `/doc.html` 仍能打开，极易误判为已接好。
   **该开关还兼任「文档路径是否免登录」的总开关**，见下一条。

5. **Sa-Token 拦截器覆盖 `/**` 后必须放行文档路径**
   `/doc.html`、`/webjars/**`、`/v3/api-docs/**`、`/knife4j/**`，否则文档页 401。
   这四个路径自 2026-09-24 起**跟随 `knife4j.enable` 联动**（实现在 `SaTokenConfig.DOC_EXCLUDE_PATHS`）：
   开关为 `true`（开发默认）时照旧免登录；`application-prod.yml` 把它设为 `false` 后不再放行，
   文档页与 `/v3/api-docs` 一律要求登录态。登录 / 注册两个认证入口在任何 profile 下都无条件放行
   （`SaTokenConfig.EXCLUDE_PATHS`）。新增放行项：无条件放行加到 `EXCLUDE_PATHS`，随开关联动加到
   `DOC_EXCLUDE_PATHS`。

6. **Boot 4 的父 pom 不再配置 `annotationProcessorPaths`**
   现在 Lombok 靠 classpath 自动发现能工作。将来加 MapStruct 时**必须显式配置**
   `annotationProcessorPaths`（lombok + mapstruct-processor + lombok-mapstruct-binding
   三者缺一不可），否则 mapper 实现类会静默不生成。

7. **`01_create_database.sql` 必须单独执行**
   `CREATE DATABASE` 不能在事务块内运行。若与其他语句拼成一批提交（多语句会被放进
   隐式事务），会报 "cannot run inside a transaction block"。文件里的 `\l`、`\c`
   都在注释里，不会发给服务器。

8. **`04_demo_data.sql` 是多语句隐式事务**
   任何一条失败会**整批回滚**（不是"少几条数据"）。改完务必实跑验证。

9. **Flyway 当前是关闭的**（`spring.flyway.enabled: false`）
   原因：表结构在开发期频繁变动，而已执行脚本受 checksum 保护、不可再改。
   待表结构稳定后把 `02_schema.sql`/`03_init_data.sql` 复制到
   `db/migration/` 改名为 `V2__`/`V3__` 再开启。

10. **时间类型用的是 `TIMESTAMP`（无时区）而非 `timestamptz`**
    沿用原始设计，单时区部署无影响。若将来跨时区需改。

11. **Boot 4 已迁移到 Jackson 3，包名是 `tools.jackson` 而非 `com.fasterxml.jackson`**
    容器里自动配置的 `ObjectMapper` Bean 是 `tools.jackson.databind.ObjectMapper`；
    classpath 里那个 `com.fasterxml.jackson` 是 **Knife4j 传递引入的库**，容器不会为它注册 Bean ——
    注入它会直接启动失败（实测报 "required a bean of type
    'com.fasterxml.jackson.databind.ObjectMapper' that could not be found"）。
    异常也变了：`JsonProcessingException` → **非受检**的 `tools.jackson.core.JacksonException`。

12. **`PaginationInnerInterceptor` 的类文件在 `mybatis-plus-jsqlparser` jar 里**
    包路径却是 `com.baomidou.mybatisplus.extension.plugins.inner` —— 在
    `mybatis-plus-extension` 里 grep 是找不到它的，别以为没这个类。
    另外 `DbType` 的常量是 **`POSTGRE_SQL`**（带下划线），不是 `POSTGRESQL`。

13. **跨域 `allowedOrigins("*")` 配 `allowCredentials(true)` 会直接抛异常**
    Spring 的 `validateAllowCredentials()` 禁止该组合，必须改用 `allowedOriginPatterns`。
    另外 `allowedHeaders` 要放开 `Content-Type`（前端固定发 JSON），
    只放 `Authorization` 会让业务请求跨域失败。

14. **Sa-Token 1.46 只有 `SaInterceptor` 一个拦截器类**（没有 `SaTokenInterceptor`）；
    `StpInterface` 的包路径是 `cn.dev33.satoken.stp.StpInterface`。
    坑：**在 `StpInterfaceImpl` 里不能调用 `StpUtil.getRoleList()`** —— 它的内部实现就是
    `SaManager.getStpInterface().getRoleList(...)`，会无限递归，要直接读会话。

15. **Windows 上正在运行的 jar 会锁住文件，`mvn package` 的 repackage 步骤会失败**
    报 `Unable to rename 'vcp-boot-1.0.0.jar' to '...jar.original'`。
    打包前先停掉 `java -jar` 起的实例（`jps -l` 找 PID → `taskkill //PID <pid> //F`）。

16. **`R<T>` 只有 `code`/`message`/`data` 三个字段，不要给它加 `isXxx()` 之类的方法**
    Jackson 会把它当 JavaBean 属性序列化，导致**每个**接口响应都多一个字段
    （曾出现 `"success":false` 混进所有响应）。判断成功用 `code == R.SUCCESS`。

17. **数据库凭证一律外置，不得写回 `application.yml`**
    仓库是**公开仓库**，明文口令进过 git 历史（提交 `161e16c`），删掉文件也删不掉历史 ——
    只有轮换口令才能让旧口令失效。凭证放 `vcp-boot/src/main/resources/application-local.yml`
    （已 gitignore，模板见同目录 `application-local.yml.example`），或环境变量
    `VCP_DB_USERNAME` / `VCP_DB_PASSWORD`。
    **注意缺凭证不会启动失败**（实测：`@ConfigurationProperties` 绑定对解析不了的占位符不报错，
    会把它当普通字符串用），要到第一次访问数据库才报认证失败 —— 排查连不上库时先看这里。
    另外本地构建**不再**把 `application-local.yml` 打进 jar（2026-09-24 起由 `vcp-boot/pom.xml`
    的 `<resources>` 精确排除，clean 重打后实测确认）；对外分发前仍建议自查一遍 jar 内容。

18. **MyBatis 的 `returnInstanceForEmptyRow` 默认为 false：一行所有映射列都是 NULL 时，
    这一行不返回对象，而是往 `List` 里塞一个 `null`**
    踩坑现场：`vcp-portrait` 的标签分布用 `lambdaQuery().select(StudentProfile::getTags)`
    只投影一列，而 `student_profile` 有 2 行 `tags` 为 NULL（31 行里），
    于是 `selectList` 返回 31 个元素、其中 2 个是 `null`，for 循环直接 NPE（`code=10000`）。
    **不报 SQL 错、只在数据恰好有空值时炸**，很容易漏。
    规矩：**任何投影查询（`.select(...)` / 自定义 `@Select`）至少要带一个恒非空列**
    ——主键、外键或 `COUNT(*)`。聚合能下推就下推（`GROUP BY` + `COUNT(*)`），
    既避开这个坑又少传行。别去开全局的 `return-instance-for-empty-row`：
    那是全局开关，会改变所有模块的行为。

### 前端（`volunteer-cert-portrait-web/`）

1. **Vue 3.5 的模板解析器只在属性值含分号时才按「多语句」解析**
   换行分隔、没有分号的多语句内联处理器会报
   `Error parsing JavaScript expression: Unexpected token, expected ","`，
   **会让整个 `vite build` 失败**。多语句一律抽成具名函数再 `@click="fn"`。
   （曾同时卡住三个并行开发会话。）

2. **ECharts 是按需注册的**
   新增图表类型必须在 `src/components/charts/echarts.js` 里补注册，
   否则运行时**静默不渲染且不报错**，很难排查。
   （2026-09-24 核对过一次，无缺口：`options.js` 用到的 6 类图表
   `line`/`bar`/`pie`/`gauge`/`heatmap`/`radar` 与 `grid`/`tooltip`/`legend`/`visualMap`/
   `title`/`radar`/`calendar` 组件全部已注册；`axisPointer` 全嵌在 `tooltip` 内，
   由 `TooltipComponent` 覆盖，不需要单独注册 `AxisPointerComponent`。）

3. **图表内柱状元素的 `borderRadius` 是原型的刻意选择**
   页面 UI 零圆角、图表内元素轻微圆角（`[6,6,0,0]`）。不要"顺手统一"成零圆角。

4. **mock 路由里静态段必须排在 `:id` 之前**
   `/v1/activities/org-overview` 会被 `/v1/activities/:id` 抢先匹配，
   把 `org-overview` 当成 id。同类的还有 `/v1/portraits/me`、`/v1/portraits/distribution`。

5. **`src/mock/data/dataset.js` 的 import 要带 `.js` 后缀**
   该文件会被 `scripts/verify-mock-data.mjs` 用 Node 直接加载，
   而 Node 的 ESM 解析器不像 Vite 那样自动补扩展名。

6. **样式三层顺序不可颠倒**：`tokens.css` → `base.css` → `ink.css`。
   令牌层没先加载，后面的组件类会全部取不到 CSS 变量。

7. **改了模拟数据要跑 `npm run verify:mock`**
   聚合数字之间有隐性约束（如学院时长合计 = 累计志愿时长 = 86,420），
   改一个数很容易让不同页面显示的数字互相打架。断言已写成脚本。

8. **Vite 8 底层是 Rolldown：`manualChunks` 不能传对象**
   `build.rollupOptions.output.manualChunks` 传 `{ echarts: ['echarts'] }` 这种对象形式会
   **直接让整个构建失败**（`Invalid type: Expected Function but received Object`，
   随后 `TypeError: manualChunks is not a function`），只接受函数。
   本项目改用 Rolldown 原生的 `advancedChunks.groups`（见 `vite.config.js`）。
   **正则匹配路径分隔符必须用 `[\\/]` 而不是 `/`** —— Windows 上模块 id 是反斜杠，
   写成 `/node_modules\/echarts/` 会匹配不到（rolldown 文档明确警告，本项目正是 Windows 开发）。

9. **别被「Some chunks are larger than 500 kB」这条构建警告误诊**
   实测（2026-09-24）：**主 chunk 一直只有 ~57 kB**，`request` chunk ~164 kB；
   超 500 kB 的是**懒加载**的 ECharts chunk（~653 kB）。它**不在** `dist/index.html`
   的 modulepreload 列表里，只在打开图表页时才下载。
   历史文档写的「ECharts 使主 chunk 超过 500 kB」是错的（真正被熔在一起的
   是 `options.js` 的图表配置 + ECharts，rolldown 给那个 chunk 起了个 `options` 的名字，
   容易让人误以为是主包）。已用 `advancedChunks` 把两者拆开：`echarts` 653 kB + `options` 9.9 kB。

## 当前进度

**已完成**：后端工程可构建可启动（`mvn package` 11 个模块全过）；数据库 16 张表已建成并验证
（`sql/` 脚本在 PostgreSQL 18.6 实跑，**20 项一致性自检全部为 0**；已固化为
`sql/09_consistency_check.sql`，2026-09-24 云库实跑违规合计 0）；
接口文档可用。**6 个业务模块全部落地**（2026-09-23）：`vcp-system` / `vcp-org` /
`vcp-volunteer` / `vcp-certification` / `vcp-portrait` / `vcp-analytics`，三个角色实打
20 个接口全部符合预期、日志零异常。OpenAPI 共 54 个路径 / 66 个「方法 + 路径」，
前端 28 个页面与 `src/api/` 的 64 个调用已与后端契约对齐，0 缺失（含 2026-09-24 新增的两条改密接口）。

**当前阶段**：开发计划**第九阶段（系统测试与数据完善）**。已完成：6 个业务模块全部落地、
`mvn package` 11 个模块全过、`sql/07_demo_scale.sql` 已实跑、**前后端联调（B12）已于
2026-09-23 审计通过**（前端 28 个页面、接口层 64 个调用与后端全部对上，0 缺失）。
集成时发现的 4 个问题已**全部闭环**（前端 B20-3 / B20-4；后端 B20-2 已补上
`activity_category.remark` 列并实跑通过；B20-1 `signed_count` 口径于 2026-09-24 由脚本侧
对齐实现闭环 —— `sql/02`（列注释）/ `sql/04` / `sql/07` 统一口径为「未取消未驳回的报名」，
`sql/07` 新活动名额下限 40 → 46）。

**未完成 / 下一步**：**P1 收尾**：~~安全（密码加密 B15、本人改密、登录失败锁定）~~ ✅ 2026-09-24 已完成 ——
口令以 BCrypt 密文入库（`PasswordUtils`），新增 `PUT /api/v1/auth/password`（本人改密，踢其它会话）
与 `PUT /api/v1/system/users/{id}/password`（管理员重置，踢全部会话），登录连续 5 次失败锁 15 分钟；
**老库需补跑 `sql/08_password_bcrypt.sql`**，新库不用。**剩余正确性**：`operation_log.target` 写入侧从不填
（见 B20-6）；**P2 交付物**：测试用例表、测试报告、系统截图、部署、论文与答辩材料。
Flyway（B14）待表结构稳定后再开；文件上传（C9）**明确推迟**。

**待办**：总入口见 `docs/待办清单.md`；**下一次开工从哪开始**见 `docs/下一步待办.md` 的
「下次开工的起点」；**每次会话的过程与证据**见 `docs/会话记录.md`。
**A 组已无剩余待定** —— A6 本次拍板：**不改**，
继续用 `TIMESTAMP` + `LocalDateTime`。理由：单一部署、无跨时区需求；改成 `timestamptz`
要动 16 张表 + 全部实体 + 前端渲染 + 演示数据重跑，收益与代价不匹配。A8（演示数据尺度）、
A9（`sys_user.status` 类型）已拍板并落地（A9 三层天然对齐、实测通过）；A1–A5、A7 已拍板
并写进代码。

> ✅ 2026-09-23 **公开仓库凭证事件已闭环**：云数据库口令已轮换（旧口令实测被服务端拒绝），
> 含明文口令的会话存档已从仓库删除并推送（提交 `f556683`）。只剩「通知另外两位同学
> 更新本地 `application-local.yml`」；清理 git 历史建议推迟到答辩之后。
> 新口令不在仓库里：它只存在于执行轮换那台机器的 `application-local.yml`（已 gitignore），
> 需要的人找轮换执行者线下索取（群聊 / 私聊），拿到后写进自己的 `application-local.yml`
> 或设环境变量 `VCP_DB_PASSWORD`，**不要回写进任何被跟踪的文件**。
> 渠道说明见根 `README.md` 的「新同学四步上手」一节。

## 重要参考文档

| 文档 | 内容 |
|---|---|
| `docs/待办清单.md` | **唯一总入口**：需拍板（A 组）+ 后端工程（B 组）+ 前端（C 组）+ 交付物（D 组）+ 已完成（E 组） |
| `docs/后端进展与待办.md` | **后端现状、已完成、决策理由、待办、环境信息** |
| `docs/前端进展与待办.md` | **前端现状、设计系统、关键决策、待办**（前端可脱离后端独立运行） |
| `docs/下一步待办.md` | **下一次开工从哪开始**：P0/P1/P2 排序清单，每条带验收方式与「已销账」证据 |
| `docs/会话记录.md` | **会话过程与证据**：每段时间做了什么、为什么这么选、环境怎么搭、踩过的坑 |
| `docs/公益等级与标签规则方案.md` | 公益等级阈值与标签判定规则（**已确认**，含落地效果） |
| `sql/README.md` | 脚本执行方式、设计约定、待拍板事项（原 9 项已全部拍板，留档备查） |
| `docs/高校志愿服务时长认证与公益画像数据分析系统_开发计划与分工.md` | 原始开发计划 |
| `docs/知识库已确认项目选题与技术背景.md` | 命名体系、架构、模块职责与表归属 |

> ⚠️ 《开发计划与分工.md》第八阶段的示例统计 SQL **是错的**：
> 它从 `service_duration` 里 `SELECT college`，但该表没有这个字段（学院在
> `student_info` 里，需 JOIN），且状态存的是 `APPROVED` 而非中文 `已通过`。
> 正确写法见 `sql/README.md` 第六节。

## 工作方式约定

- 数据相关改动**必须实跑验证**，不能只看静态检查 —— 曾出现"静态检查全过、
  真跑就整批回滚"的情况
- 演示数据用**确定性算法**（取模）而非 `random()`，保证三人执行结果一致；
  排序打破并列时用 **id** 而非中文名（中文串排序依赖数据库 collation）
- 修 SQL 后要重新实跑，让"验证过的文件"与"提交的文件"一致
