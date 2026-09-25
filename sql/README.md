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
| 4 | `04_demo_data.sql` | 演示数据：8 组织 / 20 活动 / 31 学生 / 149 条报名及对应签到与时长 | **跑 `07` 时必需**（`07` 的硬前置，见下方说明）；只做接口联调、不跑 `07` 时可跳过 |
| 5 | `05_backend_gap_fix.sql` | 后端联调补列：字典色调、用户最近登录、通知、操作日志、组织档案字段与索引 | 必需（后端接口依赖） |
| 6 | `06_backend_gap_fix2.sql` | 后端联调补列（第二批）：活动报名截止/联系方式、报名理由、分类编码、学生性别年级、时长证明与所属组织，并补齐签到聚合与外键列索引 | 必需（后端接口依赖） |
| 7 | `07_demo_scale.sql` | 演示数据放大（增量，可选，仅供开发调试与答辩演示） | 可选（**跑之前必须先执行 `04`**，见下方说明） |
| 8 | `08_password_bcrypt.sql` | 口令明文转 BCrypt 密文（增量，仅「先建库、后升级到加密版代码」的老库需要） | 老库必需 |
| 9 | `09_consistency_check.sql` | **一致性自检（20 项）**：外键悬空 6 / 汇总字段与明细不符 4 / 状态与审核字段矛盾 4 / 学院专业错配 1 / 时间异常 2 / 演示数据完整性 3，输出 **20 行 + 1 行汇总**（`check_no = 99`），**违规数全 0 即通过**（2026-09-24 云库实跑，违规合计 0） | 可选（**只读，不改变数据**，可重复执行） |
| 10 | `10_base_and_test_accounts.sql` | **清库后重灌基础数据**：3 个角色 / 2 个测试管理员账号（`admin`、`org_admin`，口令 `123456`）/ 1 个已通过审核的组织 / 6 个活动分类 / 8 类字典（7 类状态字典 + 新增 `college` 学院字典 5 条），**不含演示数据、也不含 `student` 账号**（学生走自助注册）；学院字典 5 条只是**初始种子**，运行时增删启停走学校管理端的「学院管理」页 | **新环境必需**（`college` 学院字典的**唯一来源**，缺了注册页下拉为空、注册必被拒；可重复执行） |
| 11 | `11_activity_images.sql` | 活动图片增量字段：`attachment.content_type` / `caption` / `sort_order` 与业务排序索引 | 联调必需（增量、可重复执行） |
| 12 | `12_activity_images_demo.sql` | 少量图文演示：5 个账号（3 学生 + 1 组织管理员 + 1 学校管理员）、1 个组织、3 个已发布活动及 3 张 GPT-Image2 图片元数据 | 演示可选（增量、可重复执行） |

`02_schema.sql` 开头会 `DROP TABLE IF EXISTS`，**可重复执行**（会清空数据）。若要重新生成一套完整的演示数据，
**顺序不能换**（2026-09-25 云库整库重建实测，整链约 **16 秒**）：

```text
02_schema.sql → 03_init_data.sql → 04_demo_data.sql → 05_backend_gap_fix.sql
→ 06_backend_gap_fix2.sql → 07_demo_scale.sql → 10_base_and_test_accounts.sql
→ 11_activity_images.sql → 12_activity_images_demo.sql
（最后只读跑 09_consistency_check.sql 验收：检查项总数 20、违规合计 0）
```

三条容易踩的顺序理由（详见 `CLAUDE.md` 的「踩过的坑」第 20 条）：

- **`04` 是 `07` 的硬前置**：`07` 的新活动按 `org_id = 1 + (g % 6)` 取组织，要求 `org_info` 里存在 id 1~6，
  而 **id 2~8 全部由 `04` 创建** —— 跳过 `04` 直接跑 `07`，新活动的组织外键就会落错。
- **学院字典只在 `10` 里**（`03` 的 8 类字典没有 `college`）：新环境必须跑到 `10`，否则注册必被拒。
- **`12` 放最后**：它给三场图文演示活动定的名额是 20 / 35 / 18，先跑 `12` 再跑 `07` 的话，
  `07` 的「名额 < 46 一律抬到 46」回填会把这几个数改掉。
- ⚠️ 动 `02` 这种 DDL 前，先清掉库里 `idle in transaction` 的陈旧会话，否则 `DROP TABLE` 会一直等锁
  （2026-09-25 实测白等 618 秒；排查与处理见 `CLAUDE.md` 同一条）。

`05`、`06` 是**纯增量、可重复执行**的补列脚本：已有库（含已导入演示数据的库）直接按序号接着执行即可，
不必重跑 `02`；重跑也不会改坏数据（只建缺失的列/索引，回填只填 NULL 行）。

`07_demo_scale.sql` 是**演示数据放大**脚本（增量，可选，仅供开发调试与答辩演示），性质与 `05`、`06` 相同：
**纯增量、可重复执行**，**前置条件是先跑完 `01` → `06`**；其中 **`04` 是硬前置**
（`07` 的新活动按 `org_id = 1 + (g % 6)` 取组织，id 2~8 全部由 `04` 创建）。

`08_password_bcrypt.sql` 是**口令加密迁移**脚本（增量，可重复执行），只服务于老库：
后端 B15 把口令校验从明文相等改成了 BCrypt 比对（`PasswordUtils.matches` 对明文记录一律返回 false），
所以**升级代码前就已建好的库必须跑它**，否则所有账号都登录不了。

`12_activity_images_demo.sql` 引用的 3 张图片位于
`volunteer-cert-portrait-server/uploads/demo/activities/`。部署到服务器时，请把整个
`uploads/demo/` 同步到后端的 `./uploads/demo/`（systemd 示例对应 `/opt/vcp/uploads/demo/`），
否则演示活动会显示图片 404。
`03` / `04` / `07` 的种子口令已同步换成密文，**新库不需要跑**。
脚本只更新「口令恰好是明文 123456」的行（演示数据里全部账号都是这个口令），
跑完会报出残留的明文账号数；若库里存在口令不是 123456 的明文账号，
必须由管理员用「重置密码」接口（`PUT /api/v1/system/users/{id}/password`）单独处理。

`09_consistency_check.sql` 是**一致性自检**脚本（**只读，不改变数据**，可重复执行）：输出 20 行检查结果 + 1 行汇总
（汇总行 `check_no = 99`，文案形如「检查项总数 20，违规合计 N」），**违规数全 0 即通过**。
它**可在任意阶段执行，不改变上面的执行顺序**；建议在 `07` 之后跑一次作为验收。
前置条件：`01`~`06` 必须已执行（脚本用到 `05` 补的 `org_info.college`）；`07` 可选 ——
没跑过 `07` 的库也能执行不报错，只是第 15、20 项会命中非 0（演示数据规模不足的正常现象）。
2026-09-24 在云库（PostgreSQL 18.6）实跑：20 项违规数全 0、汇总行「检查项总数 20，违规合计 0」。

- **不重建库，`04` 的规模参数保持不动**：`04` 仍是「干净起步」的基线（8 组织 / 20 活动 / 31 学生 / 149 条报名），
  `07` 只在其之上做增量放大，已在联调的后端不需要重建数据库。
- **可重复执行**：整体包在一个事务里，各节都带「还不够才补」的守卫；活动数已 ≥ 386 即视为放大过、整体跳过，
  中途失败则整批回滚，不会留下半套数据。
- **含一处补列**：`ALTER TABLE activity_category ADD COLUMN IF NOT EXISTS remark VARCHAR(255)` ——
  所以跑完 `07` **必须重启后端**，否则分类接口会查这个不存在的列而报错。
- **执行后应达到的规模**：组织 8 个（不变）、活动 386 场、学生约 1500 人、报名约 1 万条。
  实测（2026-09-24 实跑，脚本末尾 6 项 / 9 条断言全部通过）：学生 **1500** 人、活动 **386** 场、报名 **10719** 条、
  累计时长 **19319.3** 小时；六档等级齐全（普通 118 / 一星 168 / 二星 299 / 三星 687 / 四星 207 / 五星 21）；
  演示账号 `student`（学生档案 id=1）22.6 小时、四星志愿者、9 场活动、3 个画像标签。
- **顺带回填了一直为空的字段**：`org_info` 的 `code` / `college` / `founded_at` / `member_count`、
  `activity_signup.reason`、`activity_category.remark`；新增学生账号自 `stu100001` 起（6 位序号，与 `04` 的 `stu0001`~`stu0030` 不冲突）。

> ⚠️ 这个脚本**不是「一次跑通」的**：首次实跑暴露了 3 个缺陷并已修复 ——
> ① 第 108 行 `date + bigint` 缺 `::int`（PostgreSQL 没有这个运算符）；
> ② 每场报名上限 8..39 会让「五星志愿者（≥40 小时）」一档恒为空、末尾自检失败，已改为 16..45；
> ③ 新增「十之二」节定向给高活跃学生补记录，否则 40 小时以上无人能达到。
> 另外第十节与第十之二节原先缺 `should_scale` 幂等守卫，已补。（脚本由 `0a668d7` 新增，缺陷修复与实跑见 `1138eaa`。）

`10_base_and_test_accounts.sql` 是**清库后的基础数据脚本**（可重复执行：每条 `INSERT` 都带 `ON CONFLICT DO NOTHING`）：
16 张表被 `TRUNCATE` 之后，用它一次灌回「系统跑起来必需」的基础数据 —— 3 个角色 / 2 个测试管理员
（`admin`、`org_admin`，口令 `123456` 的 BCrypt 密文）/ 1 个已通过审核的组织 / 6 个活动分类 / 8 类字典
（7 类状态字典 + `college` 学院字典 5 条）。**不含演示数据，也不建 `student` 账号**（学生走自助注册）。
其中**学院字典 5 条只是初始种子**：2026-09-24 起学院可以在学校管理端的「学院管理」页增删启停
（`GET` / `POST /api/v1/system/colleges`、`PUT /api/v1/system/colleges/{id}/status`、
`DELETE /api/v1/system/colleges/{id}`，权限码 `system:college:manage`），运行时改学院**不必回头改本脚本**；
脚本重跑只补齐缺失的行，不覆盖库里已有的行。
⚠️ 清库会连带清掉 `05` / `06` 回填到行上的值（字典 `tone`、`activity_category.code` 等），
跑完本脚本请按 `05` → `06` 的顺序再跑一遍（脚本头部也写了这一条）。

## 二、执行方式

```bash
# 方式一：逐条执行（推荐，出错时容易定位）
psql -U postgres -h <主机> -p <端口> -f 01_create_database.sql
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 02_schema.sql
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 03_init_data.sql
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 04_demo_data.sql
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 05_backend_gap_fix.sql
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 06_backend_gap_fix2.sql
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 07_demo_scale.sql
# 仅老库需要（新库跳过）
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 08_password_bcrypt.sql
# 学院字典（新环境必需）+ 活动图片字段 + 少量图文演示数据
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 10_base_and_test_accounts.sql
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 11_activity_images.sql
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 12_activity_images_demo.sql
# 只读验收（可重复执行；期望「检查项总数 20，违规合计 0」）
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 09_consistency_check.sql
```

```bash
# 方式二：一次跑完
psql -U postgres -h <主机> -p <端口> -f 01_create_database.sql
cat 02_schema.sql 03_init_data.sql 04_demo_data.sql 05_backend_gap_fix.sql 06_backend_gap_fix2.sql 07_demo_scale.sql \
  10_base_and_test_accounts.sql 11_activity_images.sql 12_activity_images_demo.sql \
  | psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait
# 再单独跑一次只读自检（它不改变数据，也可以随时单独执行）
psql -U postgres -h <主机> -p <端口> -d volunteer_cert_portrait -f 09_consistency_check.sql
```

也可以用 DBeaver / pgAdmin 等图形工具依次打开执行。

**注意**：`01_create_database.sql` 里的 `CREATE DATABASE` 不能在事务中执行。用图形工具时请单独执行这一条，
不要和 `02` 一起放进同一个事务批次。

### 默认账号

登录口令均为 `123456`。库里 `sys_user.password` 存的是它的 **BCrypt 密文**（`$2a$10$` 开头的 60 字符），
**明文口令不落库**；老库若还是明文，跑 `08_password_bcrypt.sql` 刷一遍。

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

## 五、⚠️ 文档中未定义、需要团队拍板的事项（原 9 项，已全部拍板）

以下内容在《开发计划与分工.md》《知识库》《选题对话》**三份文档里都没有定义**。
按本文档「保留历史」的约定，**已拍板的条目一律不删**，仍逐条留在下表并标注状态。

> **状态（2026-09-24 复核）**：本清单最初 9 项，**现已全部拍板、剩余 0 项待定** ——
> 第 1、2 项（公益等级档位阈值、公益标签判定规则）于 2026-09-22 确认并落地，
> 依据见 `docs/公益等级与标签规则方案.md`；第 3~9 项对应 `docs/待办清单.md` 的 **A1–A7**，
> 已于 2026-09-23 / 2026-09-24 逐条拍板并写进代码（其中 A1–A5、A7 属「先行拍板、
> 请三人复核」的工程默认值，完整理由见该清单 A 组）。

| # | 待定项 | 现状 | 影响 |
|---|---|---|---|
| 1 | **公益等级的档位与阈值** | ✅ **已拍板（2026-09-22）**：采用**演示尺度阈值** —— 普通 <3 / 一星 3-6 / 二星 6-10 / 三星 10-20 / 四星 20-40 / 五星 ≥40 小时（实际部署需上调），口径见 `docs/公益等级与标签规则方案.md` | `student_info.public_welfare_level` 由累计有效时长分档；`sql/07` 有「六档齐全」断言 |
| 2 | **公益标签的判定规则** | ✅ **已拍板（2026-09-22）**：共 8 类标签（**新增**「助老服务型」「文化传播型」），类型标签**不设**参与次数下限；分类→标签按分类 `code` 映射（`CAMPUS` / `COMMUNITY` / `ENVIRONMENT` / `EVENT` / `ELDERLY` / `CULTURE`），中文名只作兜底 | `student_profile.tags` 由画像重算生成；原 6 个标签名里没有「助老服务」「文化传播」，已补齐 |
| 3 | **服务时长如何计算** | ✅ **已拍板（2026-09-23，A1）**：`(签退时间 − 签到时间) / 3600`，保留 2 位小数；缺签退或签到状态为 `ABNORMAL` 时取活动预计时长，并以预计时长的 1.5 倍封顶 | `service_duration.duration` 的生成逻辑，已落地于 `vcp-volunteer` 的 `AttendancePolicy` |
| 4 | **签到/签退的时间窗口** | ✅ **已拍板（2026-09-23，A2）**：活动开始前 30 分钟开放签到，结束后 30 分钟内必须签退；未签退记 `ABSENT`，查询时惰性判定（不引定时任务）；30 分钟为可调常量 | 签到接口校验，已落地于 `vcp-volunteer`（`VolunteerConstants`） |
| 5 | **报名人数上限的并发控制** | ✅ **已拍板（2026-09-23，A3）**：用 `UPDATE ... WHERE signed_count < max_count` 原子更新，影响行数为 0 即「已满」；不做「先查再写」、不用行锁 | 报名接口，已落地于 `vcp-volunteer` 的 `SignupServiceImpl` / `VolunteerActivityMapper` |
| 6 | **审核驳回后如何重新提交** | ✅ **已拍板（2026-09-23，A4）**：`REJECTED` 允许重新提交、回到 `PENDING_AUDIT`，并往 `duration_audit` 写一条新的 `SUBMIT` 流水（保留驳回历史，不覆盖） | `service_duration` 的状态流转，已落地于 `vcp-certification` 的 `DurationServiceImpl` |
| 7 | **`total_duration` 有两处** | ✅ **已拍板（2026-09-23，A5）**：**以 `student_info.total_duration` 为准**，时长审核通过时由后端累加；`student_profile` 仅作画像快照、可随时重算 | 两处口径必须相等，`sql/07` 有断言；已落地于 `vcp-certification` |
| 8 | **时间类型是否用 `timestamptz`** | ✅ **已拍板（2026-09-24，A6）**：**不改** —— 继续用 `TIMESTAMP` + `LocalDateTime`（单一部署、无跨时区需求） | 单时区部署无影响；将来跨时区或服务器时区不一致才需改 |
| 9 | **逻辑删除与唯一约束的冲突** | ✅ **已拍板（2026-09-23，A7）**：「取消 = 改 `status = 'CANCELED'`、复用同一行」，`deleted` 保持 0；不改唯一约束 | 报名取消，已落地于 `vcp-volunteer`；`uk_activity_student` 语义不变 |

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
