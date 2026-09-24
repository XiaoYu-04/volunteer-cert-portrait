-- =============================================================
-- 高校志愿服务时长认证与公益画像数据分析系统
-- 脚本 10：基础数据 + 2 个测试管理员账号（可重复执行）
-- 数据库类型：PostgreSQL 16+
-- =============================================================
--
-- 【用途】
--   清库（16 张表全部 TRUNCATE）之后，用本脚本一次性灌回「系统跑起来必需」的基础数据：
--   3 个角色、2 个测试管理员账号及角色绑定、1 个已通过审核的志愿组织、
--   6 个活动分类、8 类数据字典（7 类状态字典 + 1 类学院字典）。
--
-- 【本脚本不含演示数据】
--   不写 student_info，也不写活动 / 报名 / 签到 / 时长 / 通知 / 画像等业务表；
--   需要演示数据请另跑 04_demo_data.sql 与 07_demo_scale.sql（两者都是可选的）。
--   学生账号也刻意不在这里创建：学生已改走自助注册流程
--   （POST /api/v1/auth/register），再留一个固定口令的学生账号，
--   等于在系统里挂一个没人维护的弱口令入口。
--
-- 【前置条件】
--   必须先执行 01_create_database.sql 与 02_schema.sql（本脚本只 INSERT，不建表、不补列）。
--   05 / 06 不是前置条件：本脚本只写 02 里已有的列，sys_dict.tone 一律留空，
--   由 05 补的列默认值 'mute' 兜底（前端色调取值见 stores/dict.js 的 DICT_DEFS）。
--
-- 【可重复执行】
--   每条 INSERT 都带 ON CONFLICT DO NOTHING，重跑既不报错也不改动已有行。
--   刻意不写 conflict target（如 ON CONFLICT (username)）：sys_user 的唯一约束在
--   username、sys_role 在 role_code、sys_dict 在 uk_dict_type_key，而主键 id 也是唯一约束，
--   只指定其中一个，另一个上的冲突仍会直接抛错。代价是：若库里已有同 username /
--   role_code / (dict_type, dict_key) 的行，重跑不会把它刷成本脚本的取值。
--
-- 【显式 id 的范围】
--   sys_role / sys_user / org_info / activity_category 显式写 id，取值与 03_init_data.sql 一致，
--   好让 admin=1、org_admin=2 在任何库上都相同 —— org_info.contact_user_id 要引用 org_admin 的 id。
--   sys_user_role 与 sys_dict 不写 id：03 里也没写；sys_dict 的行数还会随字典增补变化，
--   写死 id 容易和库里已有的字典行撞主键。
--
-- 【跑完本脚本之后】
--   清库会把 05 / 06「回填到行上」的值一起清掉，而这两个脚本已经执行过、不会自动重跑。
--   本脚本按 03 的口径灌数据，所以不含 05 补的 sys_dict.tone、也不含 05 额外补的
--   user_status / org_status.DISABLED / attachment_biz_type 字典，以及 06 补的
--   activity_category.code。清库重灌后请按 05 → 06 的顺序再跑一遍（两者都是纯增量、
--   可重复执行的），否则状态标签会全部退化成灰色、活动分类 code 为空
--   （画像的类型标签会因此取不到）。
--
-- 【执行方式】
--   psql -U <用户> -h <主机> -p <端口> -d volunteer_cert_portrait -f sql/10_base_and_test_accounts.sql
-- =============================================================

SET client_encoding = 'UTF8';

-- 整脚本包一个事务：中途失败就整批回滚，不会留下「字典只灌了一半」的库
-- （字典缺类不报错，只会让页面显示英文码，事后很难发现）。
BEGIN;

-- =============================================================
-- 一、角色（3 条）
--     role_code 是后端判权的键（@SaCheckRole / RoleCodeEnum），
--     换成中文会让所有鉴权静默失效，中文只能写在 role_name。
-- =============================================================
INSERT INTO sys_role (id, role_code, role_name, remark) VALUES
(1, 'STUDENT',      '学生',       '学生角色'),
(2, 'ORG_ADMIN',    '组织管理员', '志愿组织管理员'),
(3, 'SCHOOL_ADMIN', '学校管理员', '学校管理员')
ON CONFLICT DO NOTHING;

-- =============================================================
-- 二、测试管理员账号（2 条）
--     password 是明文 123456 的 BCrypt 密文（$2a$10$ 开头的 60 字符），
--     与 03_init_data.sql、08_password_bcrypt.sql 里那串完全相同：
--     后端 B15 起按 BCrypt 比对，写明文一律登不进去。
--     同一明文每次哈希结果都不同（盐随机），固定成同一个值只是为了让各人的库便于比对排查。
--     status = 1 才允许登录；deleted 不写，走默认 0。
-- =============================================================
INSERT INTO sys_user (id, username, password, real_name, phone, email, status) VALUES
(1, 'admin',     '$2a$10$jPEdxZ8vkTShM79ugE6IZOPtQaMjGq9QqBFhGc9IpzdhIUVywxEwa', '学校管理员', '13800000001', 'admin@example.com', 1),
(2, 'org_admin', '$2a$10$jPEdxZ8vkTShM79ugE6IZOPtQaMjGq9QqBFhGc9IpzdhIUVywxEwa', '组织管理员', '13800000002', 'org@example.com',   1)
ON CONFLICT DO NOTHING;

-- =============================================================
-- 三、账号与角色绑定（2 条）
--     登录后按 sys_user_role 取角色码，缺了这两行，
--     账号能登录但所有 @SaCheckRole 都过不去，表现为「点哪儿都 403」。
-- =============================================================
INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 3),   -- admin     → 学校管理员
(2, 2)    -- org_admin → 组织管理员
ON CONFLICT DO NOTHING;

-- =============================================================
-- 四、志愿组织（1 条）
--     这条不是演示数据而是必需项：org_admin 登录时由 OrgLookupPortImpl
--     按 contact_user_id 反查 orgId，没有这一行就拿不到 orgId，
--     组织端页面全是空列表（不报错，最难定位）。
--     status 必须是 APPROVED —— 待审核的组织同样进不了业务页面。
-- =============================================================
INSERT INTO org_info (id, contact_user_id, org_name, org_type, contact_name, phone, email, description, status) VALUES
(1, 2, '计算机学院青年志愿者协会', '学院组织', '组织管理员', '13800000002', 'org@example.com',
 '计算机学院下属志愿服务组织，长期开展校园服务与社区帮扶活动。', 'APPROVED')
ON CONFLICT DO NOTHING;

-- =============================================================
-- 五、活动分类（6 条）
--     发布活动时必选，缺了分类下拉框就是空的。
-- =============================================================
INSERT INTO activity_category (id, category_name, sort, status) VALUES
(1, '校园服务', 1, 1),
(2, '社区服务', 2, 1),
(3, '环保公益', 3, 1),
(4, '大型赛事', 4, 1),
(5, '助老服务', 5, 1),
(6, '文化传播', 6, 1)
ON CONFLICT DO NOTHING;

-- =============================================================
-- 六、数据字典（32 条）
--     状态一律英文码入库、中文只由字典翻译，缺哪一类，
--     页面上就原样显示哪一类的英文码。
--     tone 不写：由 05 补的列默认值 'mute' 兜底，因此本脚本在没跑过 05 的库上也能执行。
-- =============================================================

-- 活动状态
INSERT INTO sys_dict (dict_type, dict_key, dict_value, sort) VALUES
('activity_status', 'DRAFT',     '草稿',   1),
('activity_status', 'PUBLISHED', '已发布', 2),
('activity_status', 'CLOSED',    '已结束', 3),
('activity_status', 'CANCELED',  '已取消', 4)
ON CONFLICT DO NOTHING;

-- 报名状态
INSERT INTO sys_dict (dict_type, dict_key, dict_value, sort) VALUES
('signup_status', 'PENDING',   '待审核', 1),
('signup_status', 'APPROVED',  '已通过', 2),
('signup_status', 'REJECTED',  '已驳回', 3),
('signup_status', 'CANCELED',  '已取消', 4),
('signup_status', 'COMPLETED', '已完成', 5)
ON CONFLICT DO NOTHING;

-- 签到状态
INSERT INTO sys_dict (dict_type, dict_key, dict_value, sort) VALUES
('attendance_status', 'NOT_SIGNED', '未签到', 1),
('attendance_status', 'SIGNED_IN',  '已签到', 2),
('attendance_status', 'SIGNED_OUT', '已签退', 3),
('attendance_status', 'ABNORMAL',   '异常',   4),
('attendance_status', 'ABSENT',     '缺勤',   5)
ON CONFLICT DO NOTHING;

-- 时长状态
INSERT INTO sys_dict (dict_type, dict_key, dict_value, sort) VALUES
('duration_status', 'PENDING_SUBMIT', '待提交', 1),
('duration_status', 'PENDING_AUDIT',  '待审核', 2),
('duration_status', 'APPROVED',       '已通过', 3),
('duration_status', 'REJECTED',       '已驳回', 4)
ON CONFLICT DO NOTHING;

-- 组织审核状态
INSERT INTO sys_dict (dict_type, dict_key, dict_value, sort) VALUES
('org_status', 'PENDING',  '待审核', 1),
('org_status', 'APPROVED', '已通过', 2),
('org_status', 'REJECTED', '已驳回', 3)
ON CONFLICT DO NOTHING;

-- 通知类型
INSERT INTO sys_dict (dict_type, dict_key, dict_value, sort) VALUES
('notification_type', 'SIGNUP',   '报名结果', 1),
('notification_type', 'DURATION', '时长审核', 2),
('notification_type', 'SYSTEM',   '系统公告', 3)
ON CONFLICT DO NOTHING;

-- 时长审核动作（注意与 duration_status 的 APPROVED / REJECTED 不同形）
INSERT INTO sys_dict (dict_type, dict_key, dict_value, sort) VALUES
('audit_action', 'SUBMIT',  '提交', 1),
('audit_action', 'APPROVE', '通过', 2),
('audit_action', 'REJECT',  '驳回', 3)
ON CONFLICT DO NOTHING;

-- 学院（新类；以上 7 类都是「英文码 → 中文」的状态字典，这一类不是）
--   dict_key 与 dict_value 都填学院名：student_info.college 与 org_info.college 存的就是
--   学院名本身，这类数据没有英文码，硬造一套 COLLEGE_01 只会让字典与实际取值对不上。
--   调用方是学生注册流程：注册页的学院下拉取 GET /api/v1/auth/colleges（免登录接口），
--   注册时后端再拿学院名去比对字典里的启用项。字典为空则下拉为空、学生注册不了，
--   所以这一类与角色一样属于「系统跑起来必需」，不是可有可无的展示配置。
--   下面 5 个学院取自 04_demo_data.sql 与 07_demo_scale.sql 一致使用的取值；
--   07 里写明「college 取值必须落在 student_info 实际用到的学院里，否则按学院筛选组织
--   与按学院筛选学生两处会对不上」，因此本清单不得增删改，顺序也固定。
INSERT INTO sys_dict (dict_type, dict_key, dict_value, sort, status) VALUES
('college', '计算机学院',   '计算机学院',   1, 1),
('college', '电子信息学院', '电子信息学院', 2, 1),
('college', '经济管理学院', '经济管理学院', 3, 1),
('college', '外国语学院',   '外国语学院',   4, 1),
('college', '机械工程学院', '机械工程学院', 5, 1)
ON CONFLICT DO NOTHING;

-- =============================================================
-- 七、修复自增序列
--     上面显式写了 id，不会推进 BIGSERIAL 的序列；若库是用
--     TRUNCATE ... RESTART IDENTITY 清的，序列还停在 1，
--     应用的下一条插入就会撞上 id = 1 的 admin —— 首当其冲的是学生自助注册。
--     setval 第三个参数 true 表示下一个值取 max + 1，重复执行无副作用。
--     未显式指定 id 的表（sys_dict 等）由序列自动推进，无需修复。
-- =============================================================
SELECT setval(pg_get_serial_sequence('sys_role',          'id'), COALESCE((SELECT MAX(id) FROM sys_role),          1));
SELECT setval(pg_get_serial_sequence('sys_user',          'id'), COALESCE((SELECT MAX(id) FROM sys_user),          1));
SELECT setval(pg_get_serial_sequence('sys_user_role',     'id'), COALESCE((SELECT MAX(id) FROM sys_user_role),     1));
SELECT setval(pg_get_serial_sequence('org_info',          'id'), COALESCE((SELECT MAX(id) FROM org_info),          1));
SELECT setval(pg_get_serial_sequence('activity_category', 'id'), COALESCE((SELECT MAX(id) FROM activity_category), 1));

-- 先提交再核对，下面两段读到的就是已落库的结果
COMMIT;

-- =============================================================
-- 八、自检（只读，供人工核对）
-- =============================================================

-- 各表实际行数。本脚本单独跑完的期望值：3 / 2 / 2 / 1 / 6 / 32
-- （字典 32 = 7 类状态字典 27 条 + 学院 5 条；若这之前还跑过 05，
--   会多出附件业务类型等增量，行数偏大属正常，按差集核对即可）
SELECT 1 AS ord, 'sys_role'          AS table_name, COUNT(*) AS row_count FROM sys_role
UNION ALL SELECT 2, 'sys_user',          COUNT(*) FROM sys_user
UNION ALL SELECT 3, 'sys_user_role',     COUNT(*) FROM sys_user_role
UNION ALL SELECT 4, 'org_info',          COUNT(*) FROM org_info
UNION ALL SELECT 5, 'activity_category', COUNT(*) FROM activity_category
UNION ALL SELECT 6, 'sys_dict',          COUNT(*) FROM sys_dict
ORDER BY ord;

-- 学院字典逐条：应为 5 行、sort 1~5，顺序即前端下拉顺序；
-- 取值必须与 student_info.college / org_info.college 里实际出现的学院一致
SELECT sort, dict_key, dict_value, status
  FROM sys_dict
 WHERE dict_type = 'college'
 ORDER BY sort;
