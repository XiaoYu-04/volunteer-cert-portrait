# sql 目录说明

高校志愿服务时长认证与公益画像数据分析系统的数据库脚本。

数据库：**PostgreSQL 16+**，库名 **`volunteer_cert_portrait`**

## 一、文件清单与执行顺序

按文件名序号依次执行，**顺序不可颠倒**：

| 顺序 | 文件 | 内容 | 是否必需 |
|---|---|---|---|
| 1 | `01_create_database.sql` | 创建数据库 `volunteer_cert_portrait` | 必需 |
| 2 | `02_schema.sql` | 建表脚本：16 张表 + 表/字段注释 + 索引 | 必需 |
| 3 | `03_init_data.sql` | 基础初始化数据：角色、账号、学生档案、组织、活动分类、数据字典 | 必需 |
| 4 | `04_demo_data.sql` | 演示数据：8 组织 / 20 活动 / 31 学生 / 149 条报名及对应签到与时长 | 可选（开发与答辩演示用） |

`02_schema.sql` 开头会 `DROP TABLE IF EXISTS`，**可重复执行**（会清空数据）。若要重新生成演示数据：
先跑 `02`，再依次跑 `03`、`04`。

## 二、执行方式

```bash
# 方式一：逐条执行（推荐，出错时容易定位）
psql -U postgres -h <主机> -p <端口> -f 01_create_database.sql
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 02_schema.sql
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 03_init_data.sql
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 04_demo_data.sql
```

```bash
# 方式二：一次跑完
psql -U postgres -h <主机> -p <端口> -f 01_create_database.sql
cat 02_schema.sql 03_init_data.sql 04_demo_data.sql \
  | psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait
```

也可以用 DBeaver / pgAdmin 等图形工具依次打开执行。

**注意**：`01_create_database.sql` 里的 `CREATE DATABASE` 不能在事务中执行。用图形工具时请单独执行这一条，
不要和 `02` 一起放进同一个事务批次。

### 默认账号

密码均为明文 `123456`。**接入 BCrypt 等加密后必须替换 `sys_user.password`，否则登录会失败。**

| 账号 | 角色 | 说明 |
|---|---|---|
| `admin` | 学校管理员 | 时长终审、组织审核、用户管理、看板 |
| `org_admin` | 组织管理员 | 已绑定组织「计算机学院青年志愿者协会」 |
| `student` | 学生 | 学生档案：计算机学院 / 软件技术 / 软件2301 |
| `org_admin2` ~ `org_admin8` | 组织管理员 | 演示数据新增，分别绑定组织 2~8 |
| `stu0001` ~ `stu0030` | 学生 | 演示数据新增 |

## 三、全局设计约定

源自知识库《全局技术规范》，建表脚本严格遵循：

- 主键统一 `id BIGSERIAL PRIMARY KEY`（数据库自增）
  → 对应 `application.yml` 的 `mybatis-plus.global-config.db-config.id-type: auto`。
  **若配成 MyBatis-Plus 默认的 `assign_id`（雪花 ID），会绕过序列并与脚本里显式写入的初始 id 冲突。**
- 命名统一 `snake_case`
- 状态字段统一用 `VARCHAR` 存**英文大写枚举码**，中文由 `sys_dict` 翻译（便于 i18n 与前端统一渲染）
- 时间字段统一 `create_time` / `update_time`，类型 `TIMESTAMP`
- 时长统一 `NUMERIC(10,1)`，单位**小时**
- 逻辑删除统一 `deleted SMALLINT`（0 未删除 / 1 已删除）

### 关于 `deleted` 字段的覆盖范围

**并非所有表都有 `deleted`**，这是有意为之：

| 表 | 是否有 deleted | 原因 |
|---|---|---|
| `sys_user`、`sys_role`、`student_info`、`org_info`、`activity_category`、`volunteer_activity`、`activity_signup`、`attendance_record`、`service_duration` | ✅ 有 | 业务数据，删除需留痕，且外键引用需要保留行 |
| `sys_user_role` | ❌ 无 | 关联表，按物理删除设计 |
| `duration_audit`、`operation_log` | ❌ 无 | 流水/日志，只追加不修改 |
| `notification` | ❌ 无 | 用 `is_read` 表达已读状态，不做软删除 |
| `sys_dict` | ❌ 无 | 用 `status` 表达启用/禁用 |
| `attachment` | ❌ 无 | 文件按物理删除 |

知识库中「全表逻辑删除」的表述与最初的 AI 生成脚本并不一致（脚本只给了 9 张表 `deleted`），
此处**采用脚本的做法**，因为上面这些表加 `deleted` 没有实际意义、只会增加查询负担。

## 四、与最初 AI 生成脚本的差异（逐条说明）

最初那份 DDL 来自选题对话记录，是完整的可用基线。本次在其基础上做了以下调整，
**每一处都可以按需改回**：

### 1. 补齐 `sys_dict` 的字典类型

原脚本只初始化了 4 类（`activity_status` / `signup_status` / `attendance_status` / `duration_status`），
导致以下 4 类枚举**无法翻译成中文**，已补齐：

- `org_status`（PENDING / APPROVED / REJECTED）— 组织审核状态
- `notification_type`（SIGNUP / DURATION / SYSTEM）— 通知类型
- `audit_action`（SUBMIT / APPROVE / REJECT）— 时长审核动作
- `attachment_biz_type`（ACTIVITY / ORG / AVATAR）— 附件业务类型

### 2. `sys_dict` 增加唯一约束

原脚本没有 `UNIQUE(dict_type, dict_key)`，同一字典类型下 key 可重复插入，是个隐患。已加：

```sql
CONSTRAINT uk_dict_type_key UNIQUE (dict_type, dict_key)
```

### 3. 索引调整（删除冗余 + 补充复合索引）

原脚本 9 条索引里有 4 条是**冗余的**，已删除：

| 被删索引 | 删除原因 |
|---|---|
| `idx_user_username ON sys_user(username)` | `username` 已有 `UNIQUE` 约束，PostgreSQL 会自动建索引 |
| `idx_role_code ON sys_role(role_code)` | 同上，`role_code` 已有 `UNIQUE` |
| `idx_student_no ON student_info(student_no)` | 同上，`student_no` 已有 `UNIQUE` |
| `idx_signup_activity ON activity_signup(activity_id)` | 已被 `uk_activity_student(activity_id, student_id)` 的最左前缀覆盖 |

另外知识库建议的 `attendance_record(signup_id)` 也无需单建 —— `signup_id` 已有 `UNIQUE` 约束。

补充了知识库《全局技术规范》建议的复合索引，以及看板统计所需的索引：

| 新增索引 | 支撑的查询 |
|---|---|
| `activity_signup(student_id, status)` | 学生端「我的报名」按状态筛选（最左前缀同时覆盖仅按 student_id 的查询） |
| `service_duration(student_id, status)` | 学生端「我的时长」按状态筛选 |
| `volunteer_activity(org_id, status)` | 组织端「本组织的活动」列表 |
| `volunteer_activity(create_time)` | 看板「本月新增活动数」与活动数量趋势图 |
| `service_duration(activity_id)` | 按活动统计 |
| `student_info(college)` | 看板「各学院志愿时长排名」 |

> 索引不是越多越好：每个索引都会拖慢写入并占用空间。上表每个索引都对应一个明确的查询场景。

### 4. 演示数据与基础数据分离

原脚本把「角色/账号」和「1 个组织 + 3 个活动 + 1 条报名」混在一个文件里。
现拆成 `03_init_data.sql`（系统跑起来必需）与 `04_demo_data.sql`（演示用，可选），
后者按《开发计划与分工.md》第九阶段的规模要求生成，并全部使用**确定性算法**（取模而非 `random()`），
保证三个成员执行后得到完全一致的演示效果。

## 五、⚠️ 文档中未定义、需要团队拍板的事项

以下内容在《开发计划与分工.md》《知识库》《选题对话》**三份文档里都没有定义**，
我没有自行编造（`04_demo_data.sql` 里用到的标签规则已明确标注为**占位规则**）：

| # | 待定项 | 现状 | 影响 |
|---|---|---|---|
| 1 | **公益等级的档位与阈值** | 三份文档均无。唯一出现的取值是种子数据里的 `普通志愿者` | `student_info.public_welfare_level` 无法计算，画像页无法展示等级 |
| 2 | **公益标签的判定规则** | 只有 6 个标签名（热心志愿者 / 长期坚持型 / 社区服务型 / 环保行动型 / 大型活动型 / 校园服务型），无任何判定条件 | `student_profile.tags` 无法生成。注意：6 个标签名里**没有**「助老服务」「文化传播」对应的标签 |
| 3 | **服务时长如何计算** | 测试项提到「时长重复提交」但无公式。是否取 `签退时间 - 签到时间`？是否受活动预计时长约束？异常签到的时长怎么算？ | 影响 `service_duration.duration` 的生成逻辑 |
| 4 | **签到/签退的时间窗口** | 无定义。活动开始前多久可签到？结束后多久必须签退？逾期是否自动记缺勤？ | 影响签到接口校验 |
| 5 | **报名人数上限的并发控制** | `signed_count` 是冗余计数列，与 `activity_signup` 实际条数可能漂移。测试项提到「活动已满继续报名」，但没说怎么防并发超卖 | 影响报名接口。建议用行锁或 `UPDATE ... WHERE signed_count < max_count` 原子更新 |
| 6 | **审核驳回后如何重新提交** | 测试项提到该场景，但无状态机定义 | 影响 `service_duration` / `activity_signup` 的状态流转 |
| 7 | **`total_duration` 有两处** | `student_info.total_duration` 与 `student_profile.total_duration` 含义相同、注释相同 | 需明确以哪个为准。**建议以 `student_info` 为准**，`student_profile` 仅作画像快照 |
| 8 | **时间类型是否用 `timestamptz`** | 现按原脚本用 `TIMESTAMP`（无时区） | 单时区部署无影响；若将来跨时区或服务器时区不一致，`timestamptz` 更安全 |
| 9 | **逻辑删除与唯一约束的冲突** | `activity_signup` 有 `uk_activity_student(activity_id, student_id)`，同时又有 `deleted` 软删除 | 若"取消报名"用 `deleted=1` 实现，该学生再次报名会撞唯一约束。需二选一：改成部分唯一索引 `UNIQUE (activity_id, student_id) WHERE deleted = 0`，或明确"取消=改 status、复用同一行"。`service_duration.signup_id` 的 UNIQUE 同理 |

## 六、发现的文档问题

1. **《开发计划与分工.md》里的示例统计 SQL 是错的**。原文（第八阶段）：
   ```sql
   SELECT college, SUM(duration) AS total_duration
   FROM service_duration
   WHERE status = '已通过'
   GROUP BY college;
   ```
   两个问题：`service_duration` 表里**没有 `college` 字段**（学院在 `student_info` 里，需 JOIN）；
   且状态存的是英文码 `APPROVED`，不是中文 `已通过`。正确写法：
   ```sql
   SELECT si.college, ROUND(SUM(sd.duration), 1) AS total_hours
   FROM service_duration sd
   JOIN student_info si ON si.id = sd.student_id
   WHERE sd.status = 'APPROVED'
   GROUP BY si.college
   ORDER BY total_hours DESC;
   ```

2. **《知识库》部署章节引用的文件名与实际不符**。原文写「PG 导入 `sql/volunteer_cert_portrait.sql`」，
   但现在拆成了 `02_schema.sql` + `03_init_data.sql`（+ 可选的 `04_demo_data.sql`），
   请把文档里的引用同步更新。

3. **《知识库》的索引建议与最初脚本不一致**。知识库建议 `activity_signup(student_id,status)`、
   `service_duration(student_id,status)`、`volunteer_activity(org_id,status)` 等复合索引，
   而最初脚本只有 9 条单列索引。本目录已按知识库的建议补齐（见第四节）。

## 七、与后端工程的衔接

- 后端数据源配置在 `volunteer-cert-portrait-server/vcp-boot/src/main/resources/application.yml`
- **Flyway 目前是关闭的**（`spring.flyway.enabled: false`）。原因：表结构在开发期会频繁变动，
  而 Flyway 的已执行脚本受 checksum 保护、不可再改，此时开启会处处报错。
  等表结构稳定后，把 `02_schema.sql`、`03_init_data.sql` 复制到
  `vcp-boot/src/main/resources/db/migration/` 并改名为 `V2__init_schema.sql`、`V3__init_data.sql`，
  再把开关打开，即可由应用启动时自动建表。
- 实体类字段与表字段通过 MyBatis-Plus 的下划线转驼峰映射（`map-underscore-to-camel-case: true`）对应，
  **无需**在实体上写 `@TableField` 逐个映射