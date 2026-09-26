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

# 重建数据库（会清空数据）—— 顺序不能换：04 是 07 的硬前置、10 供学院字典、12 放最后
cd sql
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 02_schema.sql
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 03_init_data.sql
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 04_demo_data.sql
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 05_backend_gap_fix.sql
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 06_backend_gap_fix2.sql
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 07_demo_scale.sql
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 10_base_and_test_accounts.sql
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 11_activity_images.sql
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 12_activity_images_demo.sql
# 老库另需 08_password_bcrypt.sql（把明文口令刷成 BCrypt 密文；新库不用）
# 整链实测约 16 秒；动 DDL 前先清 idle in transaction 陈旧会话（见「踩过的坑」第 19 条）

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

19. **云库动 DDL 前先清 `idle in transaction` 陈旧会话**（2026-09-25 实测，一次白等 618 秒）
    `sql/02_schema.sql` 的 `DROP TABLE` 一开始**卡了 618 秒** —— 既不是脚本慢、也不是云库卡：
    上一次会话遗留的两个 `idle in transaction` 连接（pid 5798 / 5801，已挂 18 小时 52 分，
    来自上一轮的 JDBC 只读工具）持有 `attachment_id_seq` 的锁，把 `DROP TABLE` 挡在锁后面。
    **排查链**：`pg_stat_activity` 看 `state` 与 `wait_event`（`wait_event_type = 'Lock'` 即被锁）→
    `pg_locks` **自连接**找 `NOT granted` 的阻塞者（`blocked.pid` → `blocking.pid`）→
    `pg_terminate_backend(<blocking pid>)`。清掉 4 个陈旧会话（另两个是
    `idle in transaction (aborted)`）后 DROP **立刻完成**；整链重跑时 `02` 只花 **208 ms**、
    全链约 **16 秒**。**整库重建 / 动 DDL 之前先清陈旧会话**，否则极易误判成「脚本慢」。

20. **脚本依赖：`04` 是 `07` 的硬前置、学院字典只在 `10` 里**（2026-09-25 更正）
    - `sql/README.md:16` 曾把 `04_demo_data.sql` 标成「可选」，那是**错的**：`07` 的新活动按
      `org_id = 1 + (g % 6)` 取组织，要求 `org_info` 里存在 id 1~6，而 **id 2~8 全部由 `04` 创建**。
      跳过 `04` 直接跑 `07`，新活动的组织外键就会落错。
      （2026-09-25 已修正 `sql/README.md` 的标注：`:16` 改为「跑 `07` 时必需」、`:19` 加「必须先执行 `04`」、
      `:22` 的 `10` 改为「新环境必需」、执行方式两段补上 `10` 与只读的 `09`。）
    - 5 条学院字典（`dict_type = 'college'`）**不在** `03_init_data.sql` 里，只在
      `10_base_and_test_accounts.sql` 里；新环境**必须跑到 `10`**，否则注册页下拉为空、
      注册必被拒（「请选择学院」）。
    - `12_activity_images_demo.sql` 要**放最后**：它给三场图文演示活动定的名额是 **20 / 35 / 18**，
      先跑 `12` 再跑 `07` 的话，`07` 的「名额下限 < 46 一律抬到 46」回填会把这几个数改掉。
    - 完整顺序（整链实测约 16 秒）：`02 → 03 → 04 → 05 → 06 → 07 → 10 → 11 → 12`，
      最后**只读**跑 `09` 复验（检查项总数 20、违规合计 0）。见「常用命令」一节。

21. **`ILIKE CONCAT('%', #{kw}, '%')` 在 PostgreSQL 下参数类型推不出来**（2026-09-26 实测）
    `CONCAT` 是 `VARIADIC "any"`，而本项目连接串带了 `stringtype=unspecified`，参数以 unknown
    类型下发 → 服务端报 `42P18 could not determine data type of parameter $1`，被全局异常兜成
    **`code=10000 系统繁忙`**。前端表现是「关键字查询一点就报系统繁忙」（`/activities`、
    `/signups`、`/attendance`、`/attendance/mine`、`/portraits` 全中）。
    ⚠️ **直连跑同一条 SQL 却是成功的**（psql / `preferQueryMode=simple` 会把字面量替换进去），
    所以纯 SQL 复核查不出来，必须走 PreparedStatement（走接口，或 JDBC 探针）。
    修法：`CONCAT('%', #{kw}::text, '%')`（或 `'%' || #{kw} || '%'`）。2026-09-26 已修 **11 处**
    （`VolunteerActivityMapper.xml`、`ActivitySignupMapper.xml`、`AttendanceRecordMapper.xml` 4 处、
    `PortraitAggregateMapper.java` 3 处含 tag 筛选）。**新增模糊查询必须带 `::text`**。

22. **`DateTimeFormatter.toString()` 不是模式串，别拿它取长度**（2026-09-26 实测）
    `DATE.toString()` 返回形如 `Value(YearOfEra,4,19,EXCEEDS_PAD)'-'Value(MonthOfYear,2)...` 的描述串
    （**长度 78**），不是 `yyyy-MM-dd`（长度 10）。`VolunteerTimeUtils.toDeadline` 原先写
    `if (value.length() <= DATE.toString().length())` 想表达「只给了日期 → 按当天最后一刻」，
    结果**条件恒真**：连传完整的 `yyyy-MM-dd HH:mm` 也会被抹成当天 `23:59:59`。
    后果：`deadline <= start_time` 对「今天开始的活动」永远不成立 → 建/改活动直接
    10001「报名截止时间不能晚于活动开始时间」，**「进行中且可报名」的活动根本造不出来**
    （答辩现场想造一场窗口内的活动演示签到时必踩）。修法：看**输入串本身**有没有时间部分
    （`value.indexOf(' ') < 0 && value.indexOf('T') < 0`）。

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

10. **`utils/request.js` 里对 mock 必须用动态 `import()`，不能写成顶部静态 import**
    静态 import 会让**整套 mock**（数据集 + 60 多个 handler，含演示账号明文口令）
    **无条件打进产物**，连 `VITE_USE_MOCK=false` 的正式部署包也一样 ——
    mock 模块顶层有副作用（`loadPersistedUsers()` 读写 `localStorage`），
    tree-shaking 证明不了它无副作用，于是即使 `USE_MOCK` 被常量折叠成 `false` 也删不掉。
    **这不会报错**：部署包里只是多带了 52 kB 用不到的假数据，只有 grep 产物才看得出来。
    2026-09-24 实测：静态 import 时 `request` chunk 165.0 kB；改动态后 **113.0 kB**，
    且 `VITE_USE_MOCK=false` 时**连 mock chunk 都不生成**（死分支连同动态 import 一起被消除）。
    改完必须两条路径都验：mock 关 → `grep -rl "vcp_mock_extra_users" dist/` 应无输出；
    mock 开 → `.tmp-shots/accept-mock-fixes.cjs` 与 `shoot.cjs` 都要重跑。

11. **flex 行里只给某个子项写 `align-self:baseline` 是无效的**（2026-09-26 实测，发布活动页右上角）
    父级 `.panel-extra`（ink.css:552）的 `align-items` 是默认的 `normal`(=stretch)，
    此时整行里**只有写了 align-self:baseline 的那一个子项**参与基线对齐；而它对面那段
    「裸文本」是**匿名 flex item**，拿不到它的基线作对齐基准 → 该子项退化成顶部对齐。
    现场：发布活动页 panel-head 右块 = 16px「发布组织：…」+ 12px「标 * 的为必填项」，
    图例比组织名的基线**高 5px**（用户 125% 缩放下约 6.5px），小字整块浮在名字左上角。
    横排时**修法是把基线对齐写到父级**（`align-items:baseline`），子项（含匿名项）才互相对齐。
    （该页当晚已按用户要求改成**上下两行**，见第 12 条；这条对其它横排场景仍然成立。）

12. **竖排的右对齐要用 `align-items:flex-end`，不能沿用 `baseline`**（发布活动页右块现为上下两行）
    列方向（`flex-direction:column`）的**交叉轴是横向**，`baseline` 在那条轴上没有意义 ——
    浏览器退化成 `flex-start`，两行会变**左**对齐、右边缘参差。发布活动页 `panel-extra` 现为
    `flex-direction:column; align-items:flex-end; gap:var(--sp-1)`：第一行组织名、第二行必填说明，
    左边缘参差、右边缘齐内容区；`.panel-head` 的 `align-items:baseline` 让左侧标题与**第一行**同基线。

13. **量「右边缘是否齐内容区」时别忘了 `.panel` 的 1px 边框**（同上，探针踩过）
    内容区右缘 = `rect.right − borderRightWidth − paddingRight`。只减 padding 会整体差 1px，
    探针会把「已经对齐」误报成 8 条 FAIL（`.panel` 有 `border:1px`）。

14. **判断「齐不齐」不能只量 `getBoundingClientRect()`** —— 它给的是盒子，不是基线。
    要量基线就用 Range 取文字背景盒，再减去 canvas `measureText().fontBoundingBoxAscent`。
    另外**别往 flex 容器里插探针 span**：插进去它自己就变成一个 flex item，量到的不是原来那段文字
    （本项目踩过，量出的数全是错的）。回归脚本 `.tmp-shots/probe-publish-header.cjs`：
    1440/1200/1024/900 四个宽度断言「图例在组织名下方 / 两行右缘齐内容区 / 首行与标题同基线 /
    不溢出」，**29 项**。

15. **`overflow-x:auto` 会把纵向的 `visible` 也算成 `auto`：内容溢出 1px 就长出一条竖向滚动条**
    （2026-09-26 实测，tab 条「右边的滑块」）
    现场：`.ink-tabs` 右侧凭空多出一条竖向滚动条。根因是 `.ink-tab{margin-bottom:-1px}`
    （让 2px 朱砂下划线压住容器那条 1px 深色边框）使页签**溢出内容盒 1px**
    —— 实测 `clientHeight 55 / scrollHeight 56`；而规范规定一个轴非 `visible` 时另一个轴的
    `visible` 计算成 `auto`，于是纵向也可滚动 → 滚动条出现（Windows 经典滚动条占 **15px**）。
    **修法：给容器补 `padding-bottom:1px`**，把这 1px 收进 padding box（溢出归零、观感不变，
    整条只高 1px）。⚠️ `overflow-y:hidden` 也能让滚动条消失，但它会把压住边框的那 1px 一起裁掉、
    下划线由 2px 变 1.5px（逐像素实测），别图省事用它。
    ⚠️ **判定「有没有滚动条」的两个坑**：① 不能用 `scrollHeight > clientHeight`
    （`overflow-y:hidden` 下这个不等式照样成立）；② headless 浏览器用叠加式滚动条，
    `offsetWidth − clientWidth` **恒为 0**，必须 headed 跑，判据是 `offsetWidth − clientWidth − 左右边框`。

16. **滚动容器会把子元素的键盘焦点环裁掉三个面，只剩一条像「分隔线」的红线**
    （2026-09-26 实测，同一个 tab 条）
    现场：当前页签右缘一条朱砂竖线，看着像分隔线。根因不是边框，而是全局
    `:focus-visible{outline:2px solid var(--c-focus)}`（base.css:38），而 `--c-focus` 就是
    **#B03A2E（与 `--c-a2` 同色）**；焦点环画在边框盒**外面**，上/左/下三面被 `.ink-tabs` 的
    滚动裁剪吃掉，只剩右边一面 —— 「焦点提示」于是变成一条莫名其妙的竖线。
    修法：把环收进元素内部 —— `.ink-tab:focus-visible{outline-offset:-2px}`
    （`.console-nav a:focus-visible{outline-offset:-2px}` 早就是这个做法，新加可聚焦元素时照抄）。
    回归脚本 `.tmp-shots/probe-tabs-fixed.cjs` / `probe-tabs-fixed2.cjs`（4 个 tab 页 + 375px 窄屏）。

17. **环形 / 玫瑰图的外侧「强调标签」会被画布裁掉 —— 悬停信息统一走 tooltip**（2026-09-26 实测）
    当圆环占满大半高度时（审核三态：`center 46% / radius 84%`，顶部只剩 8px），
    ECharts 的 `emphasis.label`（`position:'outer'`）画在环外，**顶部 / 底部扇区的标签会超出画布、
    被 canvas 上 / 下边缘裁掉** —— 现场：学校端 hover「已驳回」，标签第一行只剩一半（用户所说「显示不全」）。
    该标签与 tooltip 内容重复，故把 `pieEmphasis()` 改为「只放大扇区、不画标签」
    （`{ scale:true, scaleSize:6 }`），悬停数值一律交给 tooltip（不受画布裁剪、字段更全）；
    三个环图（`typePie` / `profile` / `audit`）共用该 helper，会一起变。
    附带：`sign`（签到仪表盘）默认 tooltip 只显示裸数值（`90.1` 加一个无意义色点），
    已改用 formatter 输出「活动签到率 / 90.1%」。

## 当前进度

**已完成**：后端工程可构建可启动（`mvn package` 11 个模块全过）；数据库 16 张表已建成并验证
（`sql/` 脚本在 PostgreSQL 18.6 实跑，**20 项一致性自检全部为 0**；已固化为
`sql/09_consistency_check.sql`，2026-09-24 云库实跑违规合计 0）；
**2026-09-25 云库已整库重建并回灌全量演示数据**（`02 → 03 → 04 → 05 → 06 → 07 → 10 → 11 → 12`，
随后 `09` 复跑：检查项总数 20、违规合计 0）—— `07` 口径 **1500 学生 / 386 活动 / 10719 报名 /
19319.3 小时**，叠加 `12` 后 **1503 学生 / 389 活动**（报名与时长不变；**两组数字别混用**）；
后端已重新打包（11 模块 BUILD SUCCESS）并重启（**pid 32936**，8080 监听，开发口径即未带 prod profile），
三角色冒烟全过（`/activities` 389、`/analytics/dashboard` 报名 10719 / 时长 19319.3 / 签到率 90.1%）；
接口文档可用。**6 个业务模块全部落地**（2026-09-23）：`vcp-system` / `vcp-org` /
`vcp-volunteer` / `vcp-certification` / `vcp-portrait` / `vcp-analytics`，三个角色实打
20 个接口全部符合预期、日志零异常。OpenAPI 共 **60 个路径 / 73 个「方法 + 路径」**
（2026-09-26 运行实例 `/v3/api-docs` 实查；B24 新增 `GET /api/v1/attendance/mine`），
前端 29 个页面与 `src/api/` 的 **71 个调用**已与后端契约对齐，0 缺失
（含 2026-09-24 新增的两条改密接口与学校管理端学院管理的 4 条接口，
以及 2026-09-26 B24 的学生自助签到接口）。
活动图片上传已落地：`POST /api/v1/attachments/images`，PostgreSQL 只存附件元数据，
图片本体写入 `uploads/`；发布页支持 1 张封面 + 最多 6 张带说明图片。

**2026-09-26（B24 学生自助签到闭环 + 两个横切缺陷）**：新增 `GET /api/v1/attendance/mine`
（学生本人签到记录，只挂 `@SaCheckLogin`、数据范围只看登录态），`AttendanceVO` 补
`activityStartAt`/`activityEndAt`/`canSignIn`/`canSignOut`（后两个由服务端按 A2 的 30 分钟窗口算好），
前端「我的报名」行上挂签到 / 签退按钮 + 「签到」列（未签到的显示），mock 三处对齐（含演示账号 4 条可签到记录）。
同一轮验收还挖出并修掉两个**既有横切缺陷**：**B32** `ILIKE CONCAT('%', ?, '%')` 参数类型推不出来
→ 5 个接口的关键字查询全线 10000（11 处加 `::text`）；**B33** `toDeadline` 用
`DATE.toString().length()` 判「只给日期」导致**时分秒恒被抹掉** → 「进行中且可报名」的活动造不出来
（改看输入串是否含时间部分）。详见踩坑 21、22 与 `docs/待办清单.md` 的 B24 / B32 / B33。

**当前阶段**：开发计划**第九阶段（系统测试与数据完善）**。已完成：6 个业务模块全部落地、
`mvn package` 11 个模块全过、`sql/07_demo_scale.sql` 已实跑、**前后端联调（B12）已于
2026-09-23 审计通过**（前端 29 个页面、接口层 70 个调用与后端全部对上，0 缺失）。
集成时发现的 4 个问题已**全部闭环**（前端 B20-3 / B20-4；后端 B20-2 已补上
`activity_category.remark` 列并实跑通过；B20-1 `signed_count` 口径于 2026-09-24 由脚本侧
对齐实现闭环 —— `sql/02`（列注释）/ `sql/04` / `sql/07` 统一口径为「未取消未驳回的报名」，
`sql/07` 新活动名额下限 40 → 46）。

**未完成 / 下一步**：**P1 收尾**：~~安全（密码加密 B15、本人改密、登录失败锁定）~~ ✅ 2026-09-24 已完成 ——
口令以 BCrypt 密文入库（`PasswordUtils`），新增 `PUT /api/v1/auth/password`（本人改密，踢其它会话）
与 `PUT /api/v1/system/users/{id}/password`（管理员重置，踢全部会话），登录连续 5 次失败锁 15 分钟；
**老库需补跑 `sql/08_password_bcrypt.sql`**，新库不用。**剩余正确性**：`operation_log.target` 写入侧从不填
（见 B20-6）；~~**B31 删除用户不级联**~~ ✅ **2026-09-25 已修并实测闭环** —— `DELETE /api/v1/system/users/{id}`
现在**同事务级联**软删 `student_info` / 报名 / 签到 / `service_duration`，并按 `sql/09` 第 ⑦ 项同一谓词
**重算** `signed_count`（跨模块走「vcp-system 声明端口 + 上层实现」，照 `OrgLookupPort` 既有套路；
**必需注入**而非可缺省 —— 缺实现时安静跳过就是静默的半个级联）。
**口径（别再改回去）**：报名走**逻辑删除 `deleted = 1`、保留原 `status`**，**不是**置 `CANCELED`
（看板 enrolled 的口径是 `COUNT(*) FROM activity_signup WHERE deleted = 0`，CANCELED 行仍会被数进去）。
**对照证据**：同一个 `verify.ps1`，修复前跑一轮就漂（10719→10720、19319.3→19321.8、学生 1503→1504），
修复后跑一轮**不漂**；`sql/09` 仍「20 项违规合计 0」。顺带修掉同族缺口
`PortraitAggregateMapper.selectTagCounts` 缺 `si.deleted = 0`。**仍未做**：`sql/09` 未加
「业务记录属于已逻辑删除的用户」这一项（项数仍是 20）；修复**不追溯**存量脏数据（靠重跑整链清，实测 9.7 秒）。
**P2 交付物**：测试用例表 ✅、测试报告 ✅、**系统截图 ✅（`docs/screenshots/` 28 张 / 2.73 MB，真后端口径）**、
部署（文档 + 配置 + `deploy/verify-deploy.ps1` 就绪，**真机未部署**）、
**论文五章初稿 ✅（`docs/论文/`，含 164 页 DOCX）**、**答辩材料 ✅（`docs/答辩材料/`，含 18 页 PPT）**。
Flyway（B14）待表结构稳定后再开；**活动图片上传已完成**，
组织资质材料与用户头像上传仍未接入。

**待办**：总入口见 `docs/待办清单.md`；**下一次开工从哪开始**见 `docs/下一步待办.md` 的
「下次开工的起点」；**每次会话的过程与证据**见 `docs/会话记录.md`。
**A 组已无剩余待定** —— A6 本次拍板：**不改**，
继续用 `TIMESTAMP` + `LocalDateTime`。理由：单一部署、无跨时区需求；改成 `timestamptz`
要动 16 张表 + 全部实体 + 前端渲染 + 演示数据重跑，收益与代价不匹配。A8（演示数据尺度）、
A9（`sys_user.status` 类型）已拍板并落地（A9 三层天然对齐、实测通过）；A1–A5、A7 已拍板
并写进代码。

> ✅ 2026-09-23 **公开仓库凭证事件第一阶段闭环**：云数据库口令已轮换（旧口令实测被服务端拒绝），
> 含明文口令的会话存档已从仓库删除并推送（提交 `f556683`）。
>
> ⚠️ **2026-09-26 现状变更（知情决定）**：为让任何人 clone 后零配置连库，演示库口令**刻意改回明文**
> —— `ALTER ROLE postgres PASSWORD '123456'`，并写进 `application.yml` 作为默认值
> （`VCP_DB_PASSWORD` / `application-local.yml` 仍可覆盖，优先级更高）。
> 代价：仓库 public = 口令公开，任何能访问 `103.40.14.100:19476` 的人都能改/删这个库，
> 所以该库**只放演示数据**。**答辩结束后必须轮换**；生产部署必须带 `--spring.profiles.active=prod`
> 并用环境变量覆盖这个默认值。由此「通知两位同学更新本地凭证」一项**不再需要**，已关闭。
> 清理 git 历史仍建议推迟到答辩之后（历史里的旧口令已失效）。

## 重要参考文档

| 文档 | 内容 |
|---|---|
| `docs/待办清单.md` | **唯一总入口**：需拍板（A 组）+ 后端工程（B 组）+ 前端（C 组）+ 交付物（D 组）+ 已完成（E 组） |
| `docs/后端进展与待办.md` | **后端现状、已完成、决策理由、待办、环境信息** |
| `docs/前端进展与待办.md` | **前端现状、设计系统、关键决策、待办**（前端可脱离后端独立运行） |
| `docs/下一步待办.md` | **下一次开工从哪开始**：P0/P1/P2 排序清单，每条带验收方式与「已销账」证据 |
| `docs/会话记录.md` | **会话过程与证据**：每段时间做了什么、为什么这么选、环境怎么搭、踩过的坑 |
| `docs/部署文档.md` | **部署步骤、验收清单、故障排查**（配套 `deploy/nginx.conf` + `deploy/vcp.service`）；末节逐条标明「哪些结论有实跑证据、哪些是照配置推的」 |
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
